package com.ubaidd.host.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.ubaidd.host.webrtc.WebRtcHostManager
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ubaidd.host.data.model.ChannelHealth
import com.ubaidd.host.data.model.ChannelState
import com.ubaidd.host.service.WebRtcHostService
import com.ubaidd.host.ui.theme.AccentDark
import com.ubaidd.host.ui.theme.AccentPrimary
import com.ubaidd.host.ui.theme.BorderMedium
import com.ubaidd.host.ui.theme.BorderSubtle
import com.ubaidd.host.ui.theme.CardBackground
import com.ubaidd.host.ui.theme.StatusBroken
import com.ubaidd.host.ui.theme.StatusBrokenBg
import com.ubaidd.host.ui.theme.StatusConnected
import com.ubaidd.host.ui.theme.StatusConnectedBg
import com.ubaidd.host.ui.theme.StatusReconnecting
import com.ubaidd.host.ui.theme.StatusReconnectingBg
import com.ubaidd.host.ui.theme.StatusWaiting
import com.ubaidd.host.ui.theme.StatusWaitingBg
import com.ubaidd.host.ui.theme.SurfaceWhite
import com.ubaidd.host.ui.theme.TextMuted
import com.ubaidd.host.ui.theme.TextPrimary
import com.ubaidd.host.ui.theme.TextSecondary
import com.ubaidd.host.ui.viewmodel.HostViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MainStatusScreen(
    viewModel: HostViewModel,
    onOpenSetup: () -> Unit
) {
    val isRunning by viewModel.isServiceRunning.collectAsState()
    val qrBitmap by viewModel.pairingQrBitmap.collectAsState()

    val hostManager = WebRtcHostService.instance?.hostManager
    val overallState by (hostManager?.overallConnectionState ?: remember { mutableStateOf(ChannelState.WAITING) }).let {
        if (hostManager != null) hostManager.overallConnectionState.collectAsState() else remember { mutableStateOf(ChannelState.WAITING) }
    }
    val channelHealthMap by (hostManager?.channelHealthMap ?: remember { mutableStateOf(emptyMap<String, ChannelHealth>()) }).let {
        if (hostManager != null) hostManager.channelHealthMap.collectAsState() else remember { mutableStateOf(emptyMap()) }
    }
    val videoMetrics by (hostManager?.videoMetrics ?: remember { mutableStateOf(com.ubaidd.host.data.model.VideoMetrics()) }).let {
        if (hostManager != null) hostManager.videoMetrics.collectAsState() else remember { mutableStateOf(com.ubaidd.host.data.model.VideoMetrics()) }
    }
    val debugLogs by WebRtcHostService.debugLogs.collectAsState()

    var showQrDialog by remember { mutableStateOf(false) }
    var showLogsDialog by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = SurfaceWhite
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Row
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Ubaid Host",
                            style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Engine ID: ${viewModel.pairingId}",
                            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.Monospace,
                            color = TextMuted
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { showQrDialog = true },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(CardBackground)
                                .testTag("main_qr_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCode,
                                contentDescription = "View Pairing QR Code",
                                tint = AccentPrimary
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(
                            onClick = onOpenSetup,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(CardBackground)
                                .testTag("main_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Revisit Setup",
                                tint = TextSecondary
                            )
                        }
                    }
                }
            }

            // Overall Peer Connection Status Banner
            item {
                OverallConnectionCard(
                    isServiceRunning = isRunning,
                    connectionState = overallState,
                    pairingId = viewModel.pairingId,
                    onToggleService = {
                        if (isRunning) {
                            viewModel.stopHostEngine()
                        } else {
                            viewModel.startHostEngine()
                        }
                    }
                )
            }

            // Diagnostics and Verification Action Bar
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Verified Stream Channels",
                        style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )

                    OutlinedButton(
                        onClick = { viewModel.runManualDiagnostics() },
                        modifier = Modifier.testTag("run_diagnostics_button"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = AccentPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Self-Test",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AccentPrimary
                        )
                    }
                }
            }

            // Channel 1: Video Track
            item {
                val videoHealth = channelHealthMap["video"] ?: ChannelHealth("video", "Screen Mirroring (Video)")
                val videoDetails = if (videoMetrics.isFlowing) {
                    "Delivering ${videoMetrics.width}x${videoMetrics.height} @ %.1f FPS (${videoMetrics.framesDelivered} frames)".format(videoMetrics.fps)
                } else {
                    videoHealth.statusMessage
                }

                VerifiedChannelCard(
                    title = "Screen Mirroring (Video)",
                    icon = Icons.Default.Videocam,
                    state = if (videoMetrics.isFlowing) ChannelState.CONNECTED else videoHealth.state,
                    isVerified = videoMetrics.isFlowing || videoHealth.isVerified,
                    latencyMs = 0L,
                    details = videoDetails,
                    metricBadge = if (videoMetrics.isFlowing) "%.1f FPS".format(videoMetrics.fps) else null
                )
            }

            // Channel 2: Touch & Control Channel
            item {
                val controlHealth = channelHealthMap[WebRtcHostManager.CHANNEL_CONTROL]
                    ?: ChannelHealth(WebRtcHostManager.CHANNEL_CONTROL, "Touch & Control")

                VerifiedChannelCard(
                    title = "Touch & Gesture Control",
                    icon = Icons.Default.TouchApp,
                    state = controlHealth.state,
                    isVerified = controlHealth.isVerified,
                    latencyMs = controlHealth.roundTripLatencyMs,
                    details = if (controlHealth.isVerified) {
                        "Accessibility engine active. Gesture dispatch verified."
                    } else {
                        controlHealth.statusMessage
                    },
                    metricBadge = if (controlHealth.roundTripLatencyMs > 0) "${controlHealth.roundTripLatencyMs}ms" else null
                )
            }

            // Channel 3: Files Server Channel
            item {
                val filesHealth = channelHealthMap[WebRtcHostManager.CHANNEL_FILES]
                    ?: ChannelHealth(WebRtcHostManager.CHANNEL_FILES, "File Server")

                VerifiedChannelCard(
                    title = "Filesystem & Chunk Transfer",
                    icon = Icons.Default.Folder,
                    state = filesHealth.state,
                    isVerified = filesHealth.isVerified,
                    latencyMs = filesHealth.roundTripLatencyMs,
                    details = if (filesHealth.isVerified) {
                        "Root storage accessible. Chunked transfer ready."
                    } else {
                        filesHealth.statusMessage
                    },
                    metricBadge = if (filesHealth.roundTripLatencyMs > 0) "${filesHealth.roundTripLatencyMs}ms" else null
                )
            }

            // Channel 4: Notifications Forwarder Channel
            item {
                val notifHealth = channelHealthMap[WebRtcHostManager.CHANNEL_NOTIFICATIONS]
                    ?: ChannelHealth(WebRtcHostManager.CHANNEL_NOTIFICATIONS, "Notifications Forwarder")

                val count = com.ubaidd.host.service.UbaidNotificationListenerService.totalNotificationsForwarded

                VerifiedChannelCard(
                    title = "Notification Live Forwarder",
                    icon = Icons.Default.Notifications,
                    state = notifHealth.state,
                    isVerified = notifHealth.isVerified,
                    latencyMs = notifHealth.roundTripLatencyMs,
                    details = if (notifHealth.isVerified) {
                        "Listener bound. $count notification(s) forwarded."
                    } else {
                        notifHealth.statusMessage
                    },
                    metricBadge = if (notifHealth.roundTripLatencyMs > 0) "${notifHealth.roundTripLatencyMs}ms" else null
                )
            }

            // Channel 5: Status Telemetry Channel
            item {
                val statusHealth = channelHealthMap[WebRtcHostManager.CHANNEL_STATUS]
                    ?: ChannelHealth(WebRtcHostManager.CHANNEL_STATUS, "Device Status Telemetry")

                VerifiedChannelCard(
                    title = "Device Status & Telemetry",
                    icon = Icons.Default.Sync,
                    state = statusHealth.state,
                    isVerified = statusHealth.isVerified,
                    latencyMs = statusHealth.roundTripLatencyMs,
                    details = if (statusHealth.isVerified) {
                        "Streaming battery %, charging state, and network."
                    } else {
                        statusHealth.statusMessage
                    },
                    metricBadge = if (statusHealth.roundTripLatencyMs > 0) "${statusHealth.roundTripLatencyMs}ms" else null
                )
            }

            // Diagnostic Log Section
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Engine Activity Log",
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )

                    TextButton(onClick = { showLogsDialog = true }) {
                        Text("View Full Log (${debugLogs.size})", color = AccentPrimary, fontSize = 13.sp)
                    }
                }
            }

            item {
                ActivityLogPreviewCard(
                    recentLogs = debugLogs.takeLast(4),
                    onExpand = { showLogsDialog = true }
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // QR Code Pairing Dialog
    if (showQrDialog) {
        PairingQrDialog(
            pairingId = viewModel.pairingId,
            qrBitmap = qrBitmap,
            onDismiss = { showQrDialog = false }
        )
    }

    // Full Log Viewer Dialog
    if (showLogsDialog) {
        FullLogDialog(
            logs = debugLogs,
            onDismiss = { showLogsDialog = false }
        )
    }
}

@Composable
fun OverallConnectionCard(
    isServiceRunning: Boolean,
    connectionState: ChannelState,
    pairingId: String,
    onToggleService: () -> Unit
) {
    val (statusLabel, statusColor, statusBg, statusIcon) = when {
        !isServiceRunning -> Quad("STANDBY (STOPPED)", TextMuted, CardBackground, Icons.Default.Stop)
        connectionState == ChannelState.CONNECTED -> Quad("CONNECTED LIVE", StatusConnected, StatusConnectedBg, Icons.Default.CheckCircle)
        connectionState == ChannelState.RECONNECTING -> Quad("RECONNECTING...", StatusReconnecting, StatusReconnectingBg, Icons.Default.Refresh)
        connectionState == ChannelState.BROKEN -> Quad("ERROR / OFFLINE", StatusBroken, StatusBrokenBg, Icons.Default.Error)
        else -> Quad("WAITING FOR SALIM", StatusWaiting, StatusWaitingBg, Icons.Default.HourglassTop)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CardBackground)
            .border(1.dp, BorderSubtle, RoundedCornerShape(18.dp))
            .padding(20.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(statusBg)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = statusIcon,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = statusLabel,
                            color = statusColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                Button(
                    onClick = onToggleService,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isServiceRunning) Color(0xFFEF4444) else AccentPrimary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(38.dp).testTag("service_toggle_button")
                ) {
                    Icon(
                        imageVector = if (isServiceRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isServiceRunning) "Stop Host" else "Start Host",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = if (connectionState == ChannelState.CONNECTED) {
                    "Peer-to-peer WebRTC connection established. Live media and all 4 data channels flowing."
                } else if (isServiceRunning) {
                    "Host engine is running in foreground. Listening on Firestore mailbox for Salim scan."
                } else {
                    "Host engine is stopped. Tap 'Start Host' to resume listening."
                },
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
fun VerifiedChannelCard(
    title: String,
    icon: ImageVector,
    state: ChannelState,
    isVerified: Boolean,
    latencyMs: Long,
    details: String,
    metricBadge: String? = null
) {
    val (stateBadge, badgeColor, badgeBg) = when {
        isVerified -> Triple("LIVE & VERIFIED", StatusConnected, StatusConnectedBg)
        state == ChannelState.RECONNECTING -> Triple("RECONNECTING", StatusReconnecting, StatusReconnectingBg)
        state == ChannelState.BROKEN -> Triple("UNAVAILABLE", StatusBroken, StatusBrokenBg)
        else -> Triple("LISTENING", StatusWaiting, StatusWaitingBg)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceWhite)
            .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isVerified) StatusConnectedBg else CardBackground),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isVerified) StatusConnected else TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                }

                if (metricBadge != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(CardBackground)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = metricBadge,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace,
                            color = TextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(badgeBg)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = stateBadge,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = details,
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
fun ActivityLogPreviewCard(
    recentLogs: List<Pair<Long, String>>,
    onExpand: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardBackground)
            .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
            .clickable(onClick = onExpand)
            .padding(14.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (recentLogs.isEmpty()) {
                Text(
                    text = "No log events yet. Start the engine to observe WebRTC confirmations.",
                    color = TextMuted,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            } else {
                val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
                recentLogs.forEach { (time, log) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        Text(
                            text = dateFormat.format(Date(time)),
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = log,
                            color = TextPrimary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PairingQrDialog(
    pairingId: String,
    qrBitmap: ImageBitmap?,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Pair with Salim",
                fontWeight = FontWeight.Bold,
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Scan this QR code from the Salim Controller app to establish a secure peer-to-peer WebRTC connection.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .size(260.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White)
                        .border(2.dp, BorderMedium, RoundedCornerShape(16.dp))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (qrBitmap != null) {
                        Image(
                            bitmap = qrBitmap,
                            contentDescription = "Pairing QR Code",
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text("Generating QR Code...", color = TextMuted)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Manual Pairing Code: $pairingId",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = AccentPrimary
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary)
            ) {
                Text("Close")
            }
        }
    )
}

@Composable
fun FullLogDialog(
    logs: List<Pair<Long, String>>,
    onDismiss: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Diagnostic Logs (${logs.size})",
                    fontWeight = FontWeight.Bold,
                    style = androidx.compose.material3.MaterialTheme.typography.titleLarge
                )
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                }
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(350.dp)
                    .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                items(logs.reversed()) { (time, log) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        Text(
                            text = dateFormat.format(Date(time)),
                            color = Color(0xFF64748B),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = log,
                            color = Color(0xFFF8FAFC),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary)
            ) {
                Text("Done")
            }
        }
    )
}

data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
