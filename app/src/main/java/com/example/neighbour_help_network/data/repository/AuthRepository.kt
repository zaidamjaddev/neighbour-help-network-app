package com.example.neighbour_help_network.data.repository

import com.example.neighbour_help_network.data.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await

/**
 * AuthRepository — Manages Firebase Auth and Firestore user profiles.
 */
class AuthRepository {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val usersCollection = firestore.collection("users")

    val currentUser: FirebaseUser? get() = auth.currentUser

    suspend fun login(email: String, password: String): Result<FirebaseUser> = runCatching {
        auth.signInWithEmailAndPassword(email, password).await().user!!
    }

    suspend fun signup(
        name: String,
        email: String,
        password: String,
        phone: String
    ): Result<FirebaseUser> = runCatching {
        val authResult = auth.createUserWithEmailAndPassword(email, password).await()
        val firebaseUser = authResult.user!!

        // 1. Update Firebase Auth display name
        val profileUpdates = userProfileChangeRequest {
            displayName = name.trim()
        }
        firebaseUser.updateProfile(profileUpdates).await()

        // 2. Persist user profile in Firestore
        val userProfile = User(
            uid = firebaseUser.uid,
            displayName = name.trim(),
            email = email.trim().lowercase(),
            phone = phone.trim(),
            isVolunteer = true,
            createdAt = System.currentTimeMillis()
        )
        usersCollection.document(firebaseUser.uid).set(userProfile).await()

        firebaseUser
    }

    suspend fun getUserProfile(): Result<User?> = runCatching {
        val uid = auth.currentUser?.uid ?: throw Exception("Not authenticated")
        usersCollection.document(uid).get().await().toObject(User::class.java)
    }

    suspend fun updateUserProfile(updates: Map<String, Any>): Result<Unit> = runCatching {
        val uid = auth.currentUser?.uid ?: throw Exception("Not authenticated")
        
        // If name is being updated, sync with FirebaseAuth too
        updates["displayName"]?.let { newName ->
            auth.currentUser?.updateProfile(userProfileChangeRequest {
                displayName = newName.toString()
            })?.await()
        }

        usersCollection.document(uid).update(updates).await()
    }

    fun listenToVolunteers(
        onUpdate: (List<User>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        return usersCollection
            .whereEqualTo("isVolunteer", true)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener
                val volunteers = snapshot.toObjects(User::class.java)
                onUpdate(volunteers)
            }
    }

    fun signOut() {
        auth.signOut()
    }
}
