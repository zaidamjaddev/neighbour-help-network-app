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
import kotlin.math.*

/**
 * DashboardViewModel — Manages state for the Map/Dashboard screen.
 *
 * New in this version:
 *  - nearbyHelpers: filters all helpers to only those within the selected radius.
 *  - nearbyUsersCount: the count exposed to the badge UI.
 *  - haversineDistanceKm(): Haversine formula for accurate surface distance.
 */
class DashboardViewModel : ViewModel() {

    private val repository = HelpRequestRepository()
    private val authRepository = AuthRepository()

    val radiusKm = MutableLiveData<Int>(5)
    val sosPosted = MutableLiveData<Boolean>()
    val sosError = MutableLiveData<String>()

    /** All volunteers with valid coordinates (any distance). */
    val helpers = MutableLiveData<List<User>>(emptyList())

    /** Volunteers filtered to be within the currently selected radius. */
    val nearbyHelpers = MutableLiveData<List<User>>(emptyList())

    /** Count of volunteers within radius — drives the badge. */
    val nearbyUsersCount = MutableLiveData<Int>(0)

    /** Current device location — updated once GPS fix arrives. */
    var currentLat: Double? = null
    var currentLng: Double? = null

    private var helpersRegistration: ListenerRegistration? = null

    // ─────────────────────────────────────────────────────────────────────────

    fun onRadiusChanged(km: Int) {
        radiusKm.value = km
        refilterNearbyHelpers()
    }

    /**
     * Called from DashboardFragment once we have a GPS fix.
     * Saves location to Firestore and triggers the first nearby-filter pass.
     */
    fun updateUserLocation(latitude: Double, longitude: Double) {
        currentLat = latitude
        currentLng = longitude
        refilterNearbyHelpers()
        viewModelScope.launch {
            authRepository.updateUserProfile(
                mapOf(
                    "latitude"  to latitude,
                    "longitude" to longitude
                )
            )
        }
    }

    /**
     * Re-filters the helpers list for map display and count badge.
     *
     * - When GPS is NOT yet available: shows ALL helpers immediately (no wait).
     * - When GPS IS available: filters to those within the selected radius.
     *
     * @param helperList pass the fresh list from Firestore callback directly
     *   (postValue is async, so helpers.value lags behind on first call).
     */
    private fun refilterNearbyHelpers(helperList: List<User> = helpers.value ?: emptyList()) {
        val lat = currentLat
        val lng = currentLng
        val km  = radiusKm.value ?: 5

        val filtered = if (lat != null && lng != null) {
            // GPS fix available — show only users within the selected radius
            helperList.filter { user ->
                val uLat = user.latitude ?: return@filter false
                val uLng = user.longitude ?: return@filter false
                haversineDistanceKm(lat, lng, uLat, uLng) <= km
            }
        } else {
            // No GPS yet — show ALL users with valid coordinates immediately
            helperList
        }

        nearbyHelpers.postValue(filtered)
        nearbyUsersCount.postValue(filtered.size)
    }

    /**
     * Creates and posts an emergency SOS help request to Firestore.
     */
    fun postSosAlert(latitude: Double, longitude: Double) {
        val firebaseUser = FirebaseAuth.getInstance().currentUser ?: return

        viewModelScope.launch {
            try {
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
     * Streams active volunteers from Firestore, filtering out current user and invalid coordinates.
     * On each update, also re-applies the radius filter.
     */
    fun startListeningToHelpers() {
        helpersRegistration?.remove()
        helpersRegistration = authRepository.listenToVolunteers(
            onUpdate = { list ->
                val currentUid = authRepository.currentUser?.uid
                val activeHelpers = list.filter {
                    it.uid != currentUid &&
                    it.latitude != null && it.longitude != null &&
                    it.latitude != 0.0 && it.longitude != 0.0
                }
                helpers.postValue(activeHelpers)
                // Pass activeHelpers directly — postValue() is async so
                // helpers.value hasn't updated yet when we call refilter.
                refilterNearbyHelpers(activeHelpers)
            },
            onError = {
                // Keep silent or log
            }
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Haversine distance formula
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns the great-circle distance in kilometres between two lat/lng points.
     */
    private fun haversineDistanceKm(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val r = 6371.0 // Earth radius in km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    override fun onCleared() {
        super.onCleared()
        helpersRegistration?.remove()
    }
}
