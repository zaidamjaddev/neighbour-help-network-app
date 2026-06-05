package com.example.neighbour_help_network.data.repository

import android.util.Log
import com.example.neighbour_help_network.data.model.HelpRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
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
     * Listens to requests with specific statuses.
     * We sort client-side to ensure immediate local updates (even with null timestamps)
     * and to avoid requiring mandatory composite indexes for simple filtering.
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

    suspend fun completeRequest(requestId: String): Result<Unit> = runCatching {
        collection.document(requestId).update("status", "resolved").await()
    }

    suspend fun deleteRequest(requestId: String): Result<Unit> = runCatching {
        collection.document(requestId).delete().await()
    }

    suspend fun updateRequest(requestId: String, updates: Map<String, Any>): Result<Unit> = runCatching {
        collection.document(requestId).update(updates).await()
    }
}
