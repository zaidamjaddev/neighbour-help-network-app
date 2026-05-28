package com.example.neighbour_help_network.data.repository

import com.example.neighbour_help_network.data.model.ChatMessage
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

/**
 * ChatRepository — Wraps the Firestore chats subcollection.
 *
 * Data path: "chats/{chatId}/messages/{messageId}"
 *
 * - listenToMessages()  → real-time listener, ordered ascending by timestamp
 * - sendMessage()       → adds a new message document (suspending)
 */
class ChatRepository {

    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    private fun messagesRef(chatId: String) =
        firestore.collection("chats").document(chatId).collection("messages")

    /**
     * Attaches a real-time listener to a chat's messages subcollection.
     * Callers MUST call [ListenerRegistration.remove] when the Fragment/ViewModel is destroyed.
     */
    fun listenToMessages(
        chatId: String,
        onUpdate: (List<ChatMessage>) -> Unit
    ): ListenerRegistration {
        return messagesRef(chatId)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val messages = snapshot.toObjects(ChatMessage::class.java)
                onUpdate(messages)
            }
    }

    /**
     * Sends a message by adding it to the chat's messages subcollection.
     */
    suspend fun sendMessage(chatId: String, message: ChatMessage): Result<Unit> = runCatching {
        messagesRef(chatId).add(message).await()
        Unit
    }
}
