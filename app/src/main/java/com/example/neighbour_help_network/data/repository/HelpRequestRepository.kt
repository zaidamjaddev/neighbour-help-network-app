package com.example.neighbour_help_network.data.repository

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

    fun listenToRequests(onUpdate: (List<HelpRequest>) -> Unit): ListenerRegistration {
        return collection
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val requests = snapshot.toObjects(HelpRequest::class.java)
                onUpdate(requests)
            }
    }

    suspend fun acceptRequest(requestId: String, userId: String): Result<Unit> = runCatching {
        collection.document(requestId).update(
            mapOf(
                "status"     to "accepted",
                "acceptedBy" to userId
            )
        ).await()
    }

    /**
     * Deletes a help request by ID.
     */
    suspend fun deleteRequest(requestId: String): Result<Unit> = runCatching {
        collection.document(requestId).delete().await()
    }

    /**
     * Updates an existing help request with new title/description.
     */
    suspend fun updateRequest(requestId: String, updates: Map<String, Any>): Result<Unit> = runCatching {
        collection.document(requestId).update(updates).await()
    }
}
