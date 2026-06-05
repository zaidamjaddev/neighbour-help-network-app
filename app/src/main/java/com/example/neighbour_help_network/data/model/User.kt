package com.example.neighbour_help_network.data.model

import com.google.firebase.firestore.PropertyName

/**
 * User profile stored in Firestore → "users/{uid}"
 */
data class User(
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val phone: String = "",
    val neighborhood: String = "",
    @get:PropertyName("isVolunteer")
    @set:PropertyName("isVolunteer")
    var isVolunteer: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val latitude: Double? = null,
    val longitude: Double? = null,
    val fcmToken: String = ""
)
