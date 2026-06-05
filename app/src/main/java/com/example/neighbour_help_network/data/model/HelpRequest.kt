package com.example.neighbour_help_network.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * A help request posted by a resident.
 * Stored in Firestore -> "help_requests/{id}"
 */
data class HelpRequest(
    @DocumentId
    val id: String = "",
    val userId: String = "",
    val userDisplayName: String = "",
    val title: String = "",
    val description: String = "",
    val category: String = "General Assistance",
    val urgencyScore: Int = 20,
    val urgencyLevel: String = "LOW URGENCY",
    val tags: List<String> = emptyList(),
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val status: String = "open",        // "open" | "accepted" | "resolved"
    val acceptedBy: String = "",
    val acceptedByName: String = "",    // Name of the person who accepted help
    @ServerTimestamp
    val timestamp: Date? = null
)
