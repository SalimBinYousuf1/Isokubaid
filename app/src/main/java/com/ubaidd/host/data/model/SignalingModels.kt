package com.ubaidd.host.data.model

data class SdpPayload(
    val type: String = "",
    val sdp: String = ""
)

data class IceCandidatePayload(
    val sdp: String = "",
    val sdpMid: String? = null,
    val sdpMLineIndex: Int = 0
)

data class SessionDocument(
    val sessionId: String = "",
    val hostPackage: String = "com.ubaidd.host",
    val status: String = "waiting", // waiting, offered, answered, connected, closed
    val offer: Map<String, Any>? = null,
    val answer: Map<String, Any>? = null,
    val hostCandidates: List<Map<String, Any>> = emptyList(),
    val controllerCandidates: List<Map<String, Any>> = emptyList(),
    val lastUpdated: Long = System.currentTimeMillis()
)

data class PairingConfig(
    val version: Int = 1,
    val pairingId: String,
    val projectId: String,
    val storageBucket: String,
    val apiKey: String,
    val hostPackage: String = "com.ubaidd.host",
    val deviceName: String = ""
)
