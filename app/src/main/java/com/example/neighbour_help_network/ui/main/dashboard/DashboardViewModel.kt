package com.example.neighbour_help_network.ui.main.dashboard

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.neighbour_help_network.data.model.HelpRequest
import com.example.neighbour_help_network.data.model.User
import com.example.neighbour_help_network.data.repository.AuthRepository
import com.example.neighbour_help_network.data.repository.HelpRequestRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch

/**
 * DashboardViewModel — Manages state for the Map/Dashboard screen.
 */
class DashboardViewModel : ViewModel() {

    private val repository = HelpRequestRepository()
    private val authRepository = AuthRepository()

    val radiusKm = MutableLiveData<Int>(5)
    val sosPosted = MutableLiveData<Boolean>()
    val sosError = MutableLiveData<String>()
    val helpers = MutableLiveData<List<User>>(emptyList())
    private var helpersRegistration: ListenerRegistration? = null

    fun onRadiusChanged(km: Int) {
        radiusKm.value = km
    }

    /**
     * Creates and posts an emergency SOS help request to Firestore.
     */
    fun postSosAlert(latitude: Double, longitude: Double) {
        val firebaseUser = FirebaseAuth.getInstance().currentUser ?: return

        viewModelScope.launch {
            try {
                // Fetch the latest profile name directly from Firestore (same as Settings)
                val profile = authRepository.getUserProfile().getOrNull()
                val finalDisplayName = profile?.displayName?.trim()?.takeIf { it.isNotEmpty() }
                    ?: firebaseUser.displayName?.trim()?.takeIf { it.isNotEmpty() }
                    ?: "A Neighbour"

                val sosRequest = HelpRequest(
                    userId = firebaseUser.uid,
                    userDisplayName = finalDisplayName,
                    title = "SOS ALERT",
                    description = "Emergency SOS alert! I need immediate assistance at my current location. Please respond ASAP.",
                    category = "Emergency SOS",
                    urgencyScore = 100,
                    urgencyLevel = "CRITICAL / SOS",
                    tags = listOf("sos", "emergency", "time-sensitive", "critical"),
                    latitude = latitude,
                    longitude = longitude,
                    status = "open"
                )

                val result = repository.postRequest(sosRequest)
                if (result.isSuccess) {
                    sosPosted.value = true
                } else {
                    sosError.value = result.exceptionOrNull()?.message ?: "Failed to send SOS."
                }
            } catch (e: Exception) {
                sosError.value = e.message ?: "An error occurred."
            }
        }
    }

    /**
     * Updates the logged-in user's coordinates in Firestore.
     */
    fun updateUserLocation(latitude: Double, longitude: Double) {
        viewModelScope.launch {
            authRepository.updateUserProfile(
                mapOf(
                    "latitude" to latitude,
                    "longitude" to longitude
                )
            )
        }
    }

    /**
     * Streams active volunteers from Firestore, filtering out current user and invalid coordinates.
     */
    fun startListeningToHelpers() {
        helpersRegistration?.remove()
        helpersRegistration = authRepository.listenToVolunteers(
            onUpdate = { list ->
                val currentUid = authRepository.currentUser?.uid
                val activeHelpers = list.filter {
                    it.uid != currentUid && it.latitude != null && it.longitude != null && it.latitude != 0.0 && it.longitude != 0.0
                }
                helpers.postValue(activeHelpers)
            },
            onError = {
                // Keep silent or log
            }
        )
    }

    override fun onCleared() {
        super.onCleared()
        helpersRegistration?.remove()
    }
}
