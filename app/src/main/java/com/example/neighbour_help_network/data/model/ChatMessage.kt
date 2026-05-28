package com.example.neighbour_help_network.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * A single chat message.
 * Stored in Firestore → "chats/{chatId}/messages/{messageId}"
 */
data class ChatMessage(
    @DocumentId
    val messageId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val text: String = "",
    @ServerTimestamp
    val timestamp: Date? = null
)
