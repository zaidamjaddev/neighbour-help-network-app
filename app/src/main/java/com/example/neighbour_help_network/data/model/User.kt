package com.example.neighbour_help_network.data.model

/**
 * User profile stored in Firestore → "users/{uid}"
 */
data class User(
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val phone: String = "",
    val neighborhood: String = "",
    val isVolunteer: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val latitude: Double? = null,
    val longitude: Double? = null
)
