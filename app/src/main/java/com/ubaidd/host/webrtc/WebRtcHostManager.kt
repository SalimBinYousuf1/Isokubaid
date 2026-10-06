package com.ubaidd.host.webrtc

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import com.ubaidd.host.data.model.ChannelHealth
import com.ubaidd.host.data.model.ChannelState
import com.ubaidd.host.data.model.VideoMetrics
import com.ubaidd.host.data.signaling.FirestoreSignaling
import com.ubaidd.host.service.UbaidAccessibilityService
import com.ubaidd.host.service.UbaidNotificationListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.webrtc.CapturerObserver
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoFrame
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicLong

class WebRtcHostManager(
    private val context: Context,
    private val pairingId: String,
    private val onStreamConfirmedToast: (channelName: String) -> Unit,
    private val onLogEvent: (tag: String, message: String) -> Unit
) {

    companion object {
        private const val TAG = "UbaidHostManager"
        private const val VIDEO_TRACK_ID = "screen_video"
        private const val STREAM_ID = "ubaid_media_stream"

        // Data Channel identifiers
        const val CHANNEL_CONTROL = "control"
        const val CHANNEL_FILES = "files"
        const val CHANNEL_NOTIFICATIONS = "notifications"
        const val CHANNEL_STATUS = "status"
    }

    private val scope = CoroutineScope(Dispatchers.Default + Job())

    // WebRTC Core
    private var eglBase: EglBase? = null
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var screenCapturer: ScreenCapturerAndroid? = null
    private var videoSource: VideoSource? = null
    private var videoTrack: VideoTrack? = null

    // DataChannels
    private var controlChannel: DataChannel? = null
    private var filesChannel: DataChannel? = null
    private var notificationsChannel: DataChannel? = null
    private var statusChannel: DataChannel? = null

    // Signaling
    private var signaling: FirestoreSignaling? = null

    // Domain helpers
    private val fileServer = FileServer()
    private val statusReporter = StatusReporter(context) { telemetry ->
        pushStatusTelemetry(telemetry)
    }

    // Live Metrics & States
    private val _overallConnectionState = MutableStateFlow(ChannelState.WAITING)
    val overallConnectionState = _overallConnectionState.asStateFlow()

    private val _videoMetrics = MutableStateFlow(VideoMetrics())
    val videoMetrics = _videoMetrics.asStateFlow()

    private val _channelHealthMap = MutableStateFlow(
        mapOf(
            "video" to ChannelHealth("video", "Screen Mirroring (Video)", ChannelState.WAITING, statusMessage = "Capturer standby"),
            CHANNEL_CONTROL to ChannelHealth(CHANNEL_CONTROL, "Touch & Control", ChannelState.WAITING, statusMessage = "Awaiting controller"),
            CHANNEL_FILES to ChannelHealth(CHANNEL_FILES, "File Access Server", ChannelState.WAITING, statusMessage = "Awaiting controller"),
            CHANNEL_NOTIFICATIONS to ChannelHealth(CHANNEL_NOTIFICATIONS, "Notification Forwarder", ChannelState.WAITING, statusMessage = "Awaiting controller"),
            CHANNEL_STATUS to ChannelHealth(CHANNEL_STATUS, "Device Status Telemetry", ChannelState.WAITING, statusMessage = "Awaiting controller")
        )
    )
    val channelHealthMap = _channelHealthMap.asStateFlow()

    // Frame flow tracking
    private val deliveredFrameCount = AtomicLong(0L)
    private var lastFpsSampleTime = System.currentTimeMillis()
    private var framesAtLastSample = 0L

    // MediaProjection permission data
    private var mediaProjectionData: Intent? = null

    // Reconnection & Health probe jobs
    private var healthProbeJob: Job? = null
    private var periodicStatusJob: Job? = null
    private var reconnectBackoffMs = 1000L

    // Notification listener registration
    private val notificationCallback = object : UbaidNotificationListenerService.NotificationEventCallback {
        override fun onNotificationPostedEvent(
            id: String,
            packageName: String,
            appName: String,
            title: String,
            text: String,
            postTime: Long
        ) {
            sendNotificationPayload(
                JSONObject().apply {
                    put("type", "posted")
                    put("id", id)
                    put("packageName", packageName)
                    put("appName", appName)
                    put("title", title)
                    put("text", text)
                    put("postTime", postTime)
                }.toString()
            )
        }

        override fun onNotificationRemovedEvent(id: String, packageName: String) {
            sendNotificationPayload(
                JSONObject().apply {
                    put("type", "removed")
                    put("id", id)
                    put("packageName", packageName)
                }.toString()
            )
        }
    }

    fun startHost(projectionIntent: Intent?) {
        log("Lifecycle", "Starting Ubaid Host Engine with pairing ID: $pairingId")
        this.mediaProjectionData = projectionIntent

        initWebRtcFactory()
        createPeerConnection()
        setupMediaProjectionCapture(projectionIntent)
        openDataChannels()

        startSignaling()
        startHealthProbeLoop()
        startPeriodicStatusPush()

        statusReporter.start()
        UbaidNotificationListenerService.registerCallback(notificationCallback)
    }

    private fun initWebRtcFactory() {
        if (peerConnectionFactory != null) return

        try {
            eglBase = EglBase.create()
            val eglContext = eglBase?.eglBaseContext

            val encoderFactory = DefaultVideoEncoderFactory(eglContext, true, true)
            val decoderFactory = DefaultVideoDecoderFactory(eglContext)

            peerConnectionFactory = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory)
                .createPeerConnectionFactory()

            log("WebRTC", "PeerConnectionFactory initialized successfully with hardware acceleration")
        } catch (e: Exception) {
            log("WebRTC_ERROR", "Failed to initialize PeerConnectionFactory: ${e.message}")
        }
    }

    private fun createPeerConnection() {
        val factory = peerConnectionFactory ?: return

        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer()
        )

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        peerConnection = factory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {
                log("Signaling", "WebRTC signaling state: $state")
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                log("ICE", "ICE Connection state changed to: $state")
                handleIceConnectionStateChange(state)
            }

            override fun onIceConnectionReceivingChange(receiving: Boolean) {
                log("ICE", "ICE connection receiving: $receiving")
            }

            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
                log("ICE", "ICE gathering state: $state")
            }

            override fun onIceCandidate(candidate: IceCandidate?) {
                if (candidate != null) {
                    log("ICE", "Generated local ICE candidate: ${candidate.sdpMid}")
                    signaling?.sendLocalCandidate(candidate)
                }
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}

            override fun onAddStream(stream: org.webrtc.MediaStream?) {}
            override fun onRemoveStream(stream: org.webrtc.MediaStream?) {}

            override fun onDataChannel(dataChannel: DataChannel?) {
                // If remote controller initiated a channel, bind observer
                if (dataChannel != null) {
                    log("DataChannel", "Remote peer opened data channel: ${dataChannel.label()}")
                    bindDataChannelObserver(dataChannel)
                }
            }

            override fun onRenegotiationNeeded() {
                log("WebRTC", "Renegotiation needed, generating offer")
                createAndSendOffer(isIceRestart = false)
            }

            override fun onAddTrack(receiver: org.webrtc.RtpReceiver?, streams: Array<out org.webrtc.MediaStream>?) {}
            override fun onTrack(transceiver: org.webrtc.RtpTransceiver?) {}
        })
    }

    private fun handleIceConnectionStateChange(state: PeerConnection.IceConnectionState?) {
        when (state) {
            PeerConnection.IceConnectionState.CONNECTED,
            PeerConnection.IceConnectionState.COMPLETED -> {
                _overallConnectionState.value = ChannelState.CONNECTED
                signaling?.updateSessionStatus("connected")
                reconnectBackoffMs = 1000L
                log("Connection", "PEER CONNECTION VERIFIED CONNECTED")
            }

            PeerConnection.IceConnectionState.DISCONNECTED -> {
                _overallConnectionState.value = ChannelState.RECONNECTING
                log("Connection", "Peer connection disconnected, scheduling ICE restart")
                triggerIceRestart()
            }

            PeerConnection.IceConnectionState.FAILED -> {
                _overallConnectionState.value = ChannelState.BROKEN
                log("Connection", "Peer connection failed, triggering renegotiation")
                triggerFullRenegotiation()
            }

            PeerConnection.IceConnectionState.CLOSED -> {
                _overallConnectionState.value = ChannelState.WAITING
            }

            else -> {}
        }
    }

    private fun setupMediaProjectionCapture(projectionIntent: Intent?) {
        if (projectionIntent == null) {
            log("Video", "MediaProjection permission data is null - screen capture standby")
            updateChannelState("video", ChannelState.WAITING, isVerified = false, "Consent required")
            return
        }

        val factory = peerConnectionFactory ?: return
        val egl = eglBase ?: return

        try {
            surfaceTextureHelper = SurfaceTextureHelper.create("ScreenCapturerThread", egl.eglBaseContext)
            screenCapturer = ScreenCapturerAndroid(projectionIntent, object : MediaProjection.Callback() {
                override fun onStop() {
                    super.onStop()
                    log("Video_WARN", "MediaProjection stopped by system/user")
                    updateChannelState("video", ChannelState.BROKEN, isVerified = false, "Capture stopped by system")
                }
            })

            videoSource = factory.createVideoSource(true)

            // Wrap CapturerObserver with real frame delivery verification
            val realObserver = videoSource?.capturerObserver
            val instrumentedObserver = object : CapturerObserver {
                override fun onCapturerStarted(success: Boolean) {
                    realObserver?.onCapturerStarted(success)
                    log("Video", "Screen capturer started: $success")
                    if (success) {
                        updateChannelState("video", ChannelState.WAITING, isVerified = false, "Capture initialized, awaiting frames")
                    } else {
                        updateChannelState("video", ChannelState.BROKEN, isVerified = false, "Screen capturer failed to start")
                    }
                }

                override fun onCapturerStopped() {
                    realObserver?.onCapturerStopped()
                    log("Video", "Screen capturer stopped")
                    updateChannelState("video", ChannelState.WAITING, isVerified = false, "Capture stopped")
                }

                override fun onFrameCaptured(frame: VideoFrame?) {
                    realObserver?.onFrameCaptured(frame)
                    if (frame != null) {
                        val count = deliveredFrameCount.incrementAndGet()
                        val now = System.currentTimeMillis()

                        if (count == 1L) {
                            log("Video", "FIRST REAL VIDEO FRAME DELIVERED AND ENCODED (${frame.rotatedWidth}x${frame.rotatedHeight})")
                            updateChannelState("video", ChannelState.CONNECTED, isVerified = true, "Active live stream")
                            onStreamConfirmedToast("Screen Video Stream")
                        }

                        // Calculate live FPS every second
                        if (now - lastFpsSampleTime >= 1000L) {
                            val framesDelta = count - framesAtLastSample
                            val elapsedSec = (now - lastFpsSampleTime) / 1000f
                            val currentFps = framesDelta / elapsedSec
                            lastFpsSampleTime = now
                            framesAtLastSample = count

                            _videoMetrics.update {
                                it.copy(
                                    framesDelivered = count,
                                    fps = currentFps,
                                    width = frame.rotatedWidth,
                                    height = frame.rotatedHeight,
                                    isFlowing = true,
                                    lastFrameTimestamp = now
                                )
                            }
                        }
                    }
                }
            }

            screenCapturer?.initialize(surfaceTextureHelper, context, instrumentedObserver)

            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)

            // Use crisp standard streaming resolution capped for ultra-smooth latency
            val targetWidth = (metrics.widthPixels / 2).coerceAtLeast(540)
            val targetHeight = (metrics.heightPixels / 2).coerceAtLeast(960)

            screenCapturer?.startCapture(targetWidth, targetHeight, 30)

            videoTrack = factory.createVideoTrack(VIDEO_TRACK_ID, videoSource)
            videoTrack?.setEnabled(true)

            // Attach to peer connection
            peerConnection?.addTrack(videoTrack, listOf(STREAM_ID))
            log("Video", "Screen video track created and attached to PeerConnection: ${targetWidth}x${targetHeight}@30fps")

        } catch (e: Exception) {
            log("Video_ERROR", "Error configuring screen capture: ${e.message}")
            updateChannelState("video", ChannelState.BROKEN, isVerified = false, "Capture initialization failed: ${e.message}")
        }
    }

    private fun openDataChannels() {
        val pc = peerConnection ?: return

        val init = DataChannel.Init().apply {
            ordered = true
            maxRetransmits = -1
        }

        controlChannel = pc.createDataChannel(CHANNEL_CONTROL, init)
        filesChannel = pc.createDataChannel(CHANNEL_FILES, init)
        notificationsChannel = pc.createDataChannel(CHANNEL_NOTIFICATIONS, init)
        statusChannel = pc.createDataChannel(CHANNEL_STATUS, init)

        controlChannel?.let { bindDataChannelObserver(it) }
        filesChannel?.let { bindDataChannelObserver(it) }
        notificationsChannel?.let { bindDataChannelObserver(it) }
        statusChannel?.let { bindDataChannelObserver(it) }

        log("DataChannel", "Created and initialized data channels: control, files, notifications, status")
    }

    private fun bindDataChannelObserver(channel: DataChannel) {
        val label = channel.label()
        channel.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) {}

            override fun onStateChange() {
                val state = channel.state()
                log("DataChannel", "DataChannel '$label' state changed to: $state")
                when (state) {
                    DataChannel.State.OPEN -> {
                        updateChannelState(label, ChannelState.CONNECTED, isVerified = false, "Channel open, verifying handshake...")
                        // Trigger immediate ping-pong self test on this channel
                        exerciseChannelPing(label)
                    }
                    DataChannel.State.CLOSING,
                    DataChannel.State.CLOSED -> {
                        updateChannelState(label, ChannelState.WAITING, isVerified = false, "Channel closed")
                    }
                    else -> {}
                }
            }

            override fun onMessage(buffer: DataChannel.Buffer?) {
                if (buffer == null) return
                val data = buffer.data
                val bytes = ByteArray(data.remaining())
                data.get(bytes)
                val msg = String(bytes, Charsets.UTF_8)
                handleIncomingChannelMessage(label, msg)
            }
        })
    }

    private fun handleIncomingChannelMessage(channelName: String, messageStr: String) {
        try {
            val json = JSONObject(messageStr)
            val type = json.optString("type")

            // Universal Pong handler for self-test & round-trip verification
            if (type == "pong") {
                val pingTimestamp = json.optLong("timestamp", 0L)
                val rtt = if (pingTimestamp > 0L) System.currentTimeMillis() - pingTimestamp else 0L

                val previousHealth = _channelHealthMap.value[channelName]
                val wasAlreadyVerified = previousHealth?.isVerified == true

                updateChannelState(
                    channelName,
                    ChannelState.CONNECTED,
                    isVerified = true,
                    statusMessage = "Verified active (${rtt}ms RTT)",
                    rttMs = rtt
                )

                if (!wasAlreadyVerified) {
                    log("Verification", "STREAM CONFIRMED VERIFIED: '$channelName' (RTT: ${rtt}ms)")
                    onStreamConfirmedToast("Channel: $channelName verified live!")
                }
                return
            }

            // Universal Ping handler (Controller is pinging us)
            if (type == "ping") {
                val pongJson = JSONObject().apply {
                    put("type", "pong")
                    put("timestamp", json.optLong("timestamp", System.currentTimeMillis()))
                    put("status", "active")
                    put("hostVersion", 1)
                }
                sendOverChannel(channelName, pongJson.toString())
                return
            }

            // Specific channel command handlers
            when (channelName) {
                CHANNEL_CONTROL -> handleControlCommand(json)
                CHANNEL_FILES -> handleFilesCommand(messageStr)
                CHANNEL_STATUS -> handleStatusCommand(messageStr)
            }
        } catch (e: Exception) {
            log("DataChannel_ERROR", "Error parsing message on $channelName: ${e.message}")
        }
    }

    private fun handleControlCommand(json: JSONObject) {
        val type = json.optString("type")
        val service = UbaidAccessibilityService.instance

        if (service == null) {
            log("Control_WARN", "Incoming control command '$type' dropped: AccessibilityService not bound")
            updateChannelState(CHANNEL_CONTROL, ChannelState.BROKEN, isVerified = false, "AccessibilityService unbound")
            return
        }

        when (type) {
            "tap" -> {
                val x = json.optDouble("x", 0.5).toFloat()
                val y = json.optDouble("y", 0.5).toFloat()
                service.performTap(x, y) { success ->
                    log("Control", "Executed remote tap at ($x, $y) - Success: $success")
                }
            }
            "swipe" -> {
                val startX = json.optDouble("startX", 0.5).toFloat()
                val startY = json.optDouble("startY", 0.5).toFloat()
                val endX = json.optDouble("endX", 0.5).toFloat()
                val endY = json.optDouble("endY", 0.5).toFloat()
                val duration = json.optLong("durationMs", 300L)
                service.performSwipe(startX, startY, endX, endY, duration) { success ->
                    log("Control", "Executed remote swipe - Success: $success")
                }
            }
            "key" -> {
                val action = json.optString("action")
                val success = service.performKeyAction(action)
                log("Control", "Executed global key '$action' - Success: $success")
            }
            "text" -> {
                val text = json.optString("text")
                val success = service.injectText(text)
                log("Control", "Injected text: '$text' - Success: $success")
            }
        }
    }

    private fun handleFilesCommand(rawMessage: String) {
        scope.launch(Dispatchers.IO) {
            val response = fileServer.handleRequest(rawMessage)
            sendOverChannel(CHANNEL_FILES, response)
        }
    }

    private fun handleStatusCommand(rawMessage: String) {
        val response = statusReporter.handlePing(rawMessage)
        sendOverChannel(CHANNEL_STATUS, response)
    }

    fun exerciseChannelPing(channelName: String) {
        val pingJson = JSONObject().apply {
            put("type", "ping")
            put("timestamp", System.currentTimeMillis())
        }.toString()
        sendOverChannel(channelName, pingJson)
    }

    fun sendOverChannel(channelName: String, message: String): Boolean {
        val channel = when (channelName) {
            CHANNEL_CONTROL -> controlChannel
            CHANNEL_FILES -> filesChannel
            CHANNEL_NOTIFICATIONS -> notificationsChannel
            CHANNEL_STATUS -> statusChannel
            else -> null
        }

        if (channel == null || channel.state() != DataChannel.State.OPEN) {
            return false
        }

        return try {
            val buffer = ByteBuffer.wrap(message.toByteArray(Charsets.UTF_8))
            channel.send(DataChannel.Buffer(buffer, false))
            true
        } catch (e: Exception) {
            log("DataChannel_ERROR", "Failed to send data on '$channelName': ${e.message}")
            false
        }
    }

    private fun sendNotificationPayload(jsonString: String) {
        sendOverChannel(CHANNEL_NOTIFICATIONS, jsonString)
    }

    private fun pushStatusTelemetry(telemetry: com.ubaidd.host.data.model.DeviceTelemetry) {
        val json = statusReporter.buildTelemetryJson(telemetry)
        sendOverChannel(CHANNEL_STATUS, json)
    }

    private fun startSignaling() {
        signaling = FirestoreSignaling(
            pairingId = pairingId,
            onAnswerReceived = { answerSdp ->
                log("Signaling", "Received remote Answer SDP from controller")
                peerConnection?.setRemoteDescription(object : SdpObserver {
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onSetSuccess() {
                        log("Signaling", "Successfully applied remote Answer SDP")
                    }
                    override fun onCreateFailure(error: String?) {}
                    override fun onSetFailure(error: String?) {
                        log("Signaling_ERROR", "Failed setting remote Answer: $error")
                    }
                }, answerSdp)
            },
            onRemoteCandidate = { candidate ->
                log("Signaling", "Adding remote ICE candidate: ${candidate.sdpMid}")
                peerConnection?.addIceCandidate(candidate)
            },
            onSignalingError = { errorMsg ->
                log("Signaling_ERROR", errorMsg)
            }
        )

        signaling?.start()
        createAndSendOffer(isIceRestart = false)
    }

    private fun createAndSendOffer(isIceRestart: Boolean) {
        val pc = peerConnection ?: return
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"))
            if (isIceRestart) {
                mandatory.add(MediaConstraints.KeyValuePair("IceRestart", "true"))
            }
        }

        pc.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc == null) return
                log("Signaling", "WebRTC Offer created successfully (IceRestart: $isIceRestart)")
                pc.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onSetSuccess() {
                        log("Signaling", "Local SDP set, publishing offer to Firestore")
                        signaling?.publishOffer(desc)
                    }
                    override fun onCreateFailure(error: String?) {}
                    override fun onSetFailure(error: String?) {
                        log("Signaling_ERROR", "Failed setting local description: $error")
                    }
                }, desc)
            }

            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {
                log("Signaling_ERROR", "Failed creating offer: $error")
            }
            override fun onSetFailure(error: String?) {}
        }, constraints)
    }

    private fun triggerIceRestart() {
        scope.launch {
            delay(reconnectBackoffMs)
            reconnectBackoffMs = (reconnectBackoffMs * 2).coerceAtMost(16000L)
            log("Reconnection", "Executing ICE restart...")
            createAndSendOffer(isIceRestart = true)
        }
    }

    private fun triggerFullRenegotiation() {
        scope.launch {
            delay(reconnectBackoffMs)
            reconnectBackoffMs = (reconnectBackoffMs * 2).coerceAtMost(16000L)
            log("Reconnection", "Executing full renegotiation...")
            createAndSendOffer(isIceRestart = false)
        }
    }

    /**
     * Exercises each channel with real heartbeat round-trips
     */
    private fun startHealthProbeLoop() {
        healthProbeJob?.cancel()
        healthProbeJob = scope.launch {
            while (isActive) {
                delay(4000L)
                if (_overallConnectionState.value == ChannelState.CONNECTED) {
                    exerciseChannelPing(CHANNEL_CONTROL)
                    exerciseChannelPing(CHANNEL_FILES)
                    exerciseChannelPing(CHANNEL_NOTIFICATIONS)
                    exerciseChannelPing(CHANNEL_STATUS)
                }

                // Check AccessibilityService status locally
                val isAccessBound = UbaidAccessibilityService.isBound
                if (!isAccessBound) {
                    updateChannelState(CHANNEL_CONTROL, ChannelState.BROKEN, isVerified = false, "AccessibilityService unbound in system settings")
                }

                // Check Notification listener status locally
                val isNotifBound = UbaidNotificationListenerService.isBound
                if (!isNotifBound) {
                    updateChannelState(CHANNEL_NOTIFICATIONS, ChannelState.BROKEN, isVerified = false, "Notification listener unbound in system settings")
                }
            }
        }
    }

    private fun startPeriodicStatusPush() {
        periodicStatusJob?.cancel()
        periodicStatusJob = scope.launch {
            while (isActive) {
                delay(5000L)
                if (_overallConnectionState.value == ChannelState.CONNECTED) {
                    pushStatusTelemetry(statusReporter.sampleCurrentTelemetry())
                }
            }
        }
    }

    fun runManualDiagnostics() {
        log("Diagnostics", "Manual channel diagnostics initiated by user")
        exerciseChannelPing(CHANNEL_CONTROL)
        exerciseChannelPing(CHANNEL_FILES)
        exerciseChannelPing(CHANNEL_NOTIFICATIONS)
        exerciseChannelPing(CHANNEL_STATUS)

        UbaidAccessibilityService.instance?.runSelfTest { passed, msg ->
            log("Diagnostics", "Accessibility self-test result: $passed ($msg)")
            if (passed) {
                onStreamConfirmedToast("Accessibility engine verified")
            }
        }
    }

    private fun updateChannelState(
        channelName: String,
        state: ChannelState,
        isVerified: Boolean,
        statusMessage: String,
        rttMs: Long = 0L
    ) {
        _channelHealthMap.update { currentMap ->
            val existing = currentMap[channelName] ?: ChannelHealth(channelName, channelName)
            val updated = existing.copy(
                state = state,
                isVerified = isVerified,
                statusMessage = statusMessage,
                roundTripLatencyMs = if (rttMs > 0) rttMs else existing.roundTripLatencyMs,
                lastActiveTimeMs = System.currentTimeMillis()
            )
            currentMap + (channelName to updated)
        }
    }

    private fun log(tag: String, msg: String) {
        Log.i(TAG, "[$tag] $msg")
        onLogEvent(tag, msg)
    }

    fun stopHost() {
        log("Lifecycle", "Stopping Ubaid Host Engine...")
        healthProbeJob?.cancel()
        periodicStatusJob?.cancel()
        statusReporter.stop()
        UbaidNotificationListenerService.unregisterCallback(notificationCallback)

        signaling?.stop()
        signaling = null

        controlChannel?.close()
        filesChannel?.close()
        notificationsChannel?.close()
        statusChannel?.close()

        try {
            screenCapturer?.stopCapture()
            screenCapturer?.dispose()
        } catch (e: Exception) {
            Log.w(TAG, "Error disposing capturer", e)
        }

        surfaceTextureHelper?.dispose()
        videoTrack?.dispose()
        videoSource?.dispose()
        peerConnection?.close()
        peerConnectionFactory?.dispose()
        eglBase?.release()

        screenCapturer = null
        surfaceTextureHelper = null
        videoTrack = null
        videoSource = null
        peerConnection = null
        peerConnectionFactory = null
        eglBase = null

        _overallConnectionState.value = ChannelState.WAITING
        _channelHealthMap.update { map ->
            map.mapValues { (_, health) ->
                health.copy(state = ChannelState.WAITING, isVerified = false, statusMessage = "Stopped")
            }
        }
    }
}
