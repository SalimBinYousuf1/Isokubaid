package com.ubaidd.host.data.signaling

import android.util.Log
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription

class FirestoreSignaling(
    private val pairingId: String,
    private val onAnswerReceived: (SessionDescription) -> Unit,
    private val onRemoteCandidate: (IceCandidate) -> Unit,
    private val onSignalingError: (String) -> Unit
) {

    companion object {
        private const val TAG = "UbaidSignaling"
        private const val COLLECTION_SESSIONS = "sessions"
    }

    private var firestore: FirebaseFirestore? = null
    private var docRef: DocumentReference? = null
    private var snapshotListener: ListenerRegistration? = null
    private val processedRemoteCandidates = mutableSetOf<String>()

    fun start() {
        try {
            val db = FirebaseFirestore.getInstance()
            firestore = db
            val doc = db.collection(COLLECTION_SESSIONS).document(pairingId)
            docRef = doc

            // Initialize or claim host session in Firestore
            val initialData = hashMapOf(
                "sessionId" to pairingId,
                "hostPackage" to "com.ubaidd.host",
                "status" to "waiting",
                "lastUpdated" to System.currentTimeMillis()
            )
            doc.set(initialData, SetOptions.merge())
                .addOnSuccessListener {
                    Log.i(TAG, "Signaling mailbox registered for pairing ID: $pairingId")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed registering signaling mailbox", e)
                    onSignalingError("Signaling mailbox initialization failed: ${e.localizedMessage}")
                }

            // Listen for controller answers and candidates
            snapshotListener = doc.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Signaling snapshot error: ${error.message}")
                    onSignalingError("Signaling sync error: ${error.localizedMessage}")
                    return@addSnapshotListener
                }

                if (snapshot == null || !snapshot.exists()) return@addSnapshotListener

                // Check for remote Answer
                val answerMap = snapshot.get("answer") as? Map<*, *>
                if (answerMap != null) {
                    val typeStr = answerMap["type"] as? String ?: "answer"
                    val sdpStr = answerMap["sdp"] as? String
                    if (!sdpStr.isNullOrBlank()) {
                        val type = SessionDescription.Type.fromCanonicalForm(typeStr.lowercase())
                        onAnswerReceived(SessionDescription(type, sdpStr))
                    }
                }

                // Check for Controller Candidates
                val controllerCandidates = snapshot.get("controllerCandidates") as? List<*>
                if (controllerCandidates != null) {
                    for (item in controllerCandidates) {
                        val candMap = item as? Map<*, *> ?: continue
                        val sdp = candMap["sdp"] as? String ?: continue
                        val sdpMid = candMap["sdpMid"] as? String
                        val sdpMLineIndex = (candMap["sdpMLineIndex"] as? Number)?.toInt() ?: 0

                        val candidateKey = "$sdp|$sdpMid|$sdpMLineIndex"
                        if (processedRemoteCandidates.add(candidateKey)) {
                            Log.d(TAG, "Received new remote ICE candidate from controller")
                            onRemoteCandidate(IceCandidate(sdpMid, sdpMLineIndex, sdp))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fatal error initializing Firestore signaling", e)
            onSignalingError("Firebase initialization error: ${e.localizedMessage}")
        }
    }

    fun publishOffer(offer: SessionDescription) {
        val doc = docRef ?: return
        val offerData = hashMapOf(
            "status" to "offered",
            "offer" to hashMapOf(
                "type" to offer.type.canonicalForm(),
                "sdp" to offer.description
            ),
            "hostCandidates" to emptyList<Map<String, Any>>(), // Reset candidates for new session
            "lastUpdated" to System.currentTimeMillis()
        )
        doc.set(offerData, SetOptions.merge())
            .addOnSuccessListener {
                Log.i(TAG, "Published WebRTC Offer to Firestore")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to publish offer", e)
                onSignalingError("Failed to publish WebRTC offer: ${e.localizedMessage}")
            }
    }

    fun sendLocalCandidate(candidate: IceCandidate) {
        val doc = docRef ?: return
        val candidateMap = hashMapOf(
            "sdp" to candidate.sdp,
            "sdpMid" to (candidate.sdpMid ?: ""),
            "sdpMLineIndex" to candidate.sdpMLineIndex
        )
        doc.update("hostCandidates", FieldValue.arrayUnion(candidateMap))
            .addOnFailureListener { e ->
                Log.w(TAG, "Failed to send local ICE candidate", e)
            }
    }

    fun updateSessionStatus(status: String) {
        val doc = docRef ?: return
        doc.update("status", status, "lastUpdated", System.currentTimeMillis())
            .addOnFailureListener { e ->
                Log.w(TAG, "Failed updating session status to $status", e)
            }
    }

    fun stop() {
        snapshotListener?.remove()
        snapshotListener = null
        processedRemoteCandidates.clear()
        Log.i(TAG, "Signaling stopped")
    }
}
