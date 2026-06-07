package com.example.neighbour_help_network.data.repository

import android.util.Log
import com.example.neighbour_help_network.data.model.HelpRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.tasks.await

/**
 * HelpRequestRepository — Single source of truth for all help-request data.
 */
class HelpRequestRepository {

    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val collection = firestore.collection("help_requests")

    suspend fun postRequest(request: HelpRequest): Result<String> = runCatching {
        collection.add(request).await().id
    }

    /**
     * Returns a realtime count of requests the user has completed/resolved.
     * This is used as the user's karma score.
     */
    fun listenToUserKarma(
        userId: String,
        onUpdate: (Int) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        return collection
            .whereEqualTo("status", "resolved")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }
                val count = snapshot?.documents?.count {
                    val request = it.toObject(HelpRequest::class.java)
                    request?.acceptedBy == userId || request?.userId == userId
                } ?: 0
                onUpdate(count)
            }
    }

    /**
     * Listens to requests with specific statuses.
     */
    fun listenToRequestsByStatus(
        statusList: List<String>,
        onUpdate: (List<HelpRequest>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        return collection
            .whereIn("status", statusList)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("HelpRequestRepo", "Error: ${error.message}")
                    onError(error)
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener
                
                val requests = snapshot.toObjects(HelpRequest::class.java)
                    .sortedByDescending { it.timestamp?.time ?: System.currentTimeMillis() }
                
                onUpdate(requests)
            }
    }

    /**
     * Listens to resolved requests involving the user.
     */
    fun listenToUserHistory(
        userId: String,
        onUpdate: (List<HelpRequest>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        return collection
            .whereEqualTo("status", "resolved")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("HelpRequestRepo", "History Error: ${error.message}")
                    onError(error)
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener
                
                val requests = snapshot.toObjects(HelpRequest::class.java)
                    .filter { it.userId == userId || it.acceptedBy == userId }
                    .sortedByDescending { it.timestamp?.time ?: System.currentTimeMillis() }
                
                onUpdate(requests)
            }
    }

    /**
     * Listens to accepted requests involving the user (either requester or acceptor).
     */
    fun listenToAcceptedRequests(
        userId: String,
        onUpdate: (List<HelpRequest>) -> Unit
    ): ListenerRegistration {
        return collection
            .whereEqualTo("status", "accepted")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("HelpRequestRepo", "Accepted Requests Error: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener

                val requests = snapshot.toObjects(HelpRequest::class.java)
                    .filter { it.userId == userId || it.acceptedBy == userId }
                    .sortedByDescending { it.timestamp?.time ?: System.currentTimeMillis() }

                onUpdate(requests)
            }
    }

    suspend fun acceptRequest(requestId: String, userId: String, userName: String): Result<Unit> = runCatching {
        collection.document(requestId).update(
            mapOf(
                "status"         to "accepted",
                "acceptedBy"     to userId,
                "acceptedByName" to userName
            )
        ).await()
    }

    /**
     * Completes a request and awards points to both helper and requester.
     * Transaction ensures points are incremented atomically.
     */
    suspend fun completeRequest(requestId: String): Result<Unit> = runCatching {
        val docRef = collection.document(requestId)
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(docRef)
            val request = snapshot.toObject(HelpRequest::class.java)
                ?: throw IllegalStateException("Request not found")

            if (request.status == "resolved") return@runTransaction null

            // Mark request as resolved
            transaction.update(docRef, mapOf(
                "status" to "resolved",
                "resolvedAt" to FieldValue.serverTimestamp()
            ))

            // Award points to the Helper (+5 pts)
            if (request.acceptedBy.isNotBlank()) {
                transaction.set(
                    firestore.collection("users").document(request.acceptedBy),
                    mapOf(
                        "totalPoints" to FieldValue.increment(5L),
                        "resolvedRequests" to FieldValue.increment(1L)
                    ),
                    SetOptions.merge()
                )
            }

            // Award points to the Requester (+2 pts)
            if (request.userId.isNotBlank()) {
                transaction.set(
                    firestore.collection("users").document(request.userId),
                    mapOf(
                        "totalPoints" to FieldValue.increment(2L),
                        "resolvedRequests" to FieldValue.increment(1L)
                    ),
                    SetOptions.merge()
                )
            }
            null
        }.await()
    }

    suspend fun deleteRequest(requestId: String): Result<Unit> = runCatching {
        collection.document(requestId).delete().await()
    }

    suspend fun updateRequest(requestId: String, updates: Map<String, Any>): Result<Unit> = runCatching {
        collection.document(requestId).update(updates).await()
    }
}
