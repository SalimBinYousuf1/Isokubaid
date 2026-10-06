package com.ubaidd.host.data.model

enum class ChannelState {
    CONNECTED,      // Verified active and responding
    WAITING,        // Ready and listening, awaiting peer handshake
    RECONNECTING,   // Actively restoring connection
    BROKEN          // Hardware/service error or unresponsive
}

data class ChannelHealth(
    val channelId: String,
    val displayName: String,
    val state: ChannelState = ChannelState.WAITING,
    val isVerified: Boolean = false,
    val roundTripLatencyMs: Long = 0L,
    val lastActiveTimeMs: Long = 0L,
    val statusMessage: String = "Initializing",
    val failureReason: String? = null
)

data class VideoMetrics(
    val framesDelivered: Long = 0L,
    val fps: Float = 0f,
    val width: Int = 1080,
    val height: Int = 2400,
    val isFlowing: Boolean = false,
    val lastFrameTimestamp: Long = 0L
)

data class DeviceTelemetry(
    val batteryPercent: Int = 100,
    val isCharging: Boolean = false,
    val powerSource: String = "Battery",
    val networkType: String = "Wi-Fi",
    val isScreenOn: Boolean = true,
    val timestamp: Long = System.currentTimeMillis()
)

data class FileInfoItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModified: Long
)
