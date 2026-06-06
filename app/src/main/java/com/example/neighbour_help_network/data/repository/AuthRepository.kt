package com.example.neighbour_help_network.data.repository

import android.net.Uri
import com.example.neighbour_help_network.data.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import android.util.Log
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await

/**
 * AuthRepository — Manages Firebase Auth, Firestore user profiles, and Storage photo uploads.
 */
class AuthRepository {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()
    private val usersCollection = firestore.collection("users")

    val currentUser: FirebaseUser? get() = auth.currentUser

    suspend fun login(email: String, password: String): Result<FirebaseUser> = runCatching {
        auth.signInWithEmailAndPassword(email, password).await().user!!
    }

    suspend fun signup(
        name: String,
        email: String,
        password: String,
        phone: String,
        photoLocalPath: String? = null // optional local file path for profile image
    ): Result<FirebaseUser> = runCatching {
        val authResult = auth.createUserWithEmailAndPassword(email, password).await()
        val firebaseUser = authResult.user!!

        val photoUrl = photoLocalPath ?: ""

        // Update Firebase Auth display name
        val profileUpdates = UserProfileChangeRequest.Builder()
            .setDisplayName(name.trim())
            .apply { if (photoUrl.isNotEmpty()) setPhotoUri(Uri.parse("file://" + photoUrl)) }
            .build()
        firebaseUser.updateProfile(profileUpdates).await()

        // Persist user profile in Firestore
        val userProfile = User(
            uid = firebaseUser.uid,
            displayName = name.trim(),
            email = email.trim().lowercase(),
            phone = phone.trim(),
            isVolunteer = true,
            createdAt = System.currentTimeMillis(),
            photoUrl = photoUrl,
            totalPoints = 0,
            resolvedRequests = 0
        )
        usersCollection.document(firebaseUser.uid).set(userProfile).await()

        firebaseUser
    }

    // IMAGE UPLOAD DISABLED
    // suspend fun uploadProfilePhoto(uid: String, imageBytes: ByteArray): Result<String> = runCatching {
    //     Log.d("AuthRepository", "Starting uploadProfilePhoto for uid: $uid")
    //     val ref = storage.reference.child("profilePhotos/$uid.jpg")
    //     ref.putBytes(imageBytes).await()
    //     val url = ref.downloadUrl.await().toString()
    //     Log.d("AuthRepository", "uploadProfilePhoto success: $url")
    //     url
    // }.onFailure {
    //     Log.e("AuthRepository", "uploadProfilePhoto failed", it)
    // }

    // IMAGE UPLOAD DISABLED
    // suspend fun updateProfilePhoto(imageBytes: ByteArray): Result<String> = runCatching {
    //     val uid = auth.currentUser?.uid ?: throw Exception("Not authenticated")
    //     val url = uploadProfilePhoto(uid, imageBytes).getOrThrow()
    //     val profileUpdates = UserProfileChangeRequest.Builder().setPhotoUri(Uri.parse(url)).build()
    //     auth.currentUser?.updateProfile(profileUpdates)?.await()
    //     usersCollection.document(uid).set(mapOf("photoUrl" to url), SetOptions.merge()).await()
    //     url
    // }

    suspend fun getUserProfile(): Result<User?> = runCatching {
        val uid = auth.currentUser?.uid ?: throw Exception("Not authenticated")
        usersCollection.document(uid).get().await().toObject(User::class.java)
    }

    suspend fun updateUserProfile(updates: Map<String, Any>): Result<Unit> = runCatching {
        val uid = auth.currentUser?.uid ?: throw Exception("Not authenticated")

        // If name is being updated, sync with FirebaseAuth too
        updates["displayName"]?.let { newName ->
            val profileUpdates = UserProfileChangeRequest.Builder()
                .setDisplayName(newName.toString())
                .build()
            auth.currentUser?.updateProfile(profileUpdates)?.await()
        }

        usersCollection.document(uid).update(updates).await()
    }

    suspend fun awardUserPoints(userId: String, points: Int): Result<Unit> = runCatching {
        if (points <= 0) return@runCatching
        usersCollection.document(userId)
            .set(
                mapOf(
                    "totalPoints" to com.google.firebase.firestore.FieldValue.increment(points.toLong()),
                    "resolvedRequests" to com.google.firebase.firestore.FieldValue.increment(1L)
                ),
                SetOptions.merge()
            )
            .await()
    }

    suspend fun incrementRequesterResolvedCount(userId: String, points: Int): Result<Unit> = runCatching {
        if (points <= 0) return@runCatching
        usersCollection.document(userId)
            .set(
                mapOf(
                    "totalPoints" to com.google.firebase.firestore.FieldValue.increment(points.toLong()),
                    "resolvedRequests" to com.google.firebase.firestore.FieldValue.increment(1L)
                ),
                SetOptions.merge()
            )
            .await()
    }

    /**
     * Update the user document's photoUrl with a local file path (no Firebase Storage used).
     * Returns the stored path on success.
     */
    suspend fun updateLocalProfilePhoto(localPath: String): Result<String> = runCatching {
        val uid = auth.currentUser?.uid ?: throw Exception("Not authenticated")
        // Store the path in Firestore so other clients read it.
        usersCollection.document(uid).set(mapOf("photoUrl" to localPath), SetOptions.merge()).await()

        // Optionally update FirebaseAuth photoUri (stores the same URI string). Not required for usage.
        try {
            val profileUpdates = UserProfileChangeRequest.Builder()
                .setPhotoUri(Uri.parse("file://" + localPath))
                .build()
            auth.currentUser?.updateProfile(profileUpdates)?.await()
        } catch (e: Exception) {
            // Non-fatal — we still return success since Firestore write succeeded.
            Log.w("AuthRepository", "Failed to update auth photoUri: ${e.message}")
        }

        localPath
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

    /**
     * Persists the FCM device token for the currently signed-in user in Firestore.
     * Uses set+merge so this never fails even on edge-case document states.
     */
    suspend fun saveFcmToken(token: String): Result<Unit> = runCatching {
        val uid = auth.currentUser?.uid ?: return@runCatching
        usersCollection.document(uid)
            .set(mapOf("fcmToken" to token), SetOptions.merge())
            .await()
    }

    fun signOut() {
        auth.signOut()
    }
}
