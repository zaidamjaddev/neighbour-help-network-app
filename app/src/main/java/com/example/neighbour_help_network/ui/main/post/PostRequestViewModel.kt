package com.example.neighbour_help_network.ui.main.post

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.neighbour_help_network.data.local.LocalAiEngine
import com.example.neighbour_help_network.data.model.HelpRequest
import com.example.neighbour_help_network.data.repository.AuthRepository
import com.example.neighbour_help_network.data.repository.HelpRequestRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

/**
 * PostRequestViewModel — Manages state for the Post Help Request screen.
 */
class PostRequestViewModel : ViewModel() {

    private val helpRepository = HelpRequestRepository()
    private val authRepository = AuthRepository()

    val aiResult = MutableLiveData<LocalAiEngine.AiAnalysisResult?>()
    val submitResult = MutableLiveData<Result<String>?>()
    val isLoading = MutableLiveData<Boolean>(false)

    fun analyzeDescription(description: String) {
        val result = LocalAiEngine.analyzeHelpDescription(description)
        aiResult.value = result
    }

    fun submitRequest(
        title: String,
        description: String,
        latitude: Double,
        longitude: Double
    ) {
        val firebaseUser = FirebaseAuth.getInstance().currentUser ?: return

        isLoading.value = true

        viewModelScope.launch {
            try {
                // Fetch the latest profile name directly from Firestore (Exactly as shown in Settings)
                val profile = authRepository.getUserProfile().getOrNull()
                
                // Prioritize Profile Display Name > Auth Display Name > Fallback
                val finalDisplayName = profile?.displayName?.trim()?.takeIf { it.isNotBlank() }
                    ?: firebaseUser.displayName?.trim()?.takeIf { it.isNotBlank() }
                    ?: "A Neighbour"

                val analysis = aiResult.value ?: LocalAiEngine.analyzeHelpDescription(description)

                val request = HelpRequest(
                    userId = firebaseUser.uid,
                    userDisplayName = finalDisplayName,
                    title = title.trim(),
                    description = description.trim(),
                    category = analysis.predictedCategory,
                    urgencyScore = analysis.urgencyScore,
                    urgencyLevel = analysis.urgencyLevel,
                    tags = analysis.automatedTags,
                    latitude = latitude,
                    longitude = longitude,
                    status = "open"
                )

                val result = helpRepository.postRequest(request)
                submitResult.postValue(result)
            } catch (e: Exception) {
                submitResult.postValue(Result.failure(e))
            } finally {
                isLoading.postValue(false)
            }
        }
    }

    fun consumeSubmitResult() {
        submitResult.value = null
    }
}
