package com.ubaidd.host

import com.ubaidd.host.data.model.ChannelHealth
import com.ubaidd.host.data.model.ChannelState
import com.ubaidd.host.data.model.DeviceTelemetry
import com.ubaidd.host.data.model.PairingConfig
import com.ubaidd.host.data.model.SessionDocument
import com.ubaidd.host.data.model.VideoMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HostUnitTest {

    @Test
    fun testPairingConfigIntegrity() {
        val config = PairingConfig(
            version = 1,
            pairingId = "UBD-99AAFF",
            projectId = "salim-x-ubaid",
            storageBucket = "salim-x-ubaid.firebasestorage.app",
            apiKey = "AIzaSyBdiTj7YRtZ5ncYv6few_Gfaw9h-mbqU3w",
            hostPackage = "com.ubaidd.host",
            deviceName = "Host Pixel 8"
        )

        assertEquals("UBD-99AAFF", config.pairingId)
        assertEquals("salim-x-ubaid", config.projectId)
        assertEquals("com.ubaidd.host", config.hostPackage)
    }

    @Test
    fun testChannelHealthStateTransitions() {
        val initial = ChannelHealth(
            channelId = "control",
            displayName = "Touch & Control",
            state = ChannelState.WAITING,
            isVerified = false,
            statusMessage = "Awaiting controller"
        )

        assertFalse(initial.isVerified)
        assertEquals(ChannelState.WAITING, initial.state)

        val connected = initial.copy(
            state = ChannelState.CONNECTED,
            isVerified = true,
            roundTripLatencyMs = 28L,
            statusMessage = "Verified active (28ms RTT)"
        )

        assertTrue(connected.isVerified)
        assertEquals(ChannelState.CONNECTED, connected.state)
        assertEquals(28L, connected.roundTripLatencyMs)
    }

    @Test
    fun testVideoMetricsFlowing() {
        val metrics = VideoMetrics(
            framesDelivered = 120L,
            fps = 30.0f,
            width = 1080,
            height = 2400,
            isFlowing = true
        )

        assertTrue(metrics.isFlowing)
        assertEquals(120L, metrics.framesDelivered)
        assertEquals(30.0f, metrics.fps, 0.01f)
    }

    @Test
    fun testDeviceTelemetrySampling() {
        val telemetry = DeviceTelemetry(
            batteryPercent = 95,
            isCharging = true,
            powerSource = "AC Charger",
            networkType = "Wi-Fi",
            isScreenOn = true
        )

        assertEquals(95, telemetry.batteryPercent)
        assertTrue(telemetry.isCharging)
        assertEquals("Wi-Fi", telemetry.networkType)
    }

    @Test
    fun testSessionDocumentDefaults() {
        val session = SessionDocument(
            sessionId = "UBD-123456",
            hostPackage = "com.ubaidd.host",
            status = "waiting"
        )

        assertEquals("com.ubaidd.host", session.hostPackage)
        assertEquals("waiting", session.status)
        assertTrue(session.hostCandidates.isEmpty())
        assertTrue(session.controllerCandidates.isEmpty())
    }
}
