package com.example.neighbour_help_network.ui.main.feed

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.neighbour_help_network.data.model.HelpRequest
import com.example.neighbour_help_network.data.repository.HelpRequestRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch

/**
 * NearbyFeedViewModel — Manages the Firestore real-time stream for NearbyFeedFragment.
 */
class NearbyFeedViewModel : ViewModel() {

    private val repository = HelpRequestRepository()
    private var listenerReg: ListenerRegistration? = null

    val requests = MutableLiveData<List<HelpRequest>>(emptyList())
    val isLoading = MutableLiveData<Boolean>(true)
    val actionResult = MutableLiveData<Result<Unit>?>()

    init {
        startListening()
    }

    private fun startListening() {
        listenerReg = repository.listenToRequests { list ->
            isLoading.postValue(false)
            requests.postValue(list)
        }
    }

    fun acceptRequest(requestId: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        viewModelScope.launch {
            val result = repository.acceptRequest(requestId, uid)
            actionResult.postValue(result)
        }
    }

    fun deleteRequest(requestId: String) {
        viewModelScope.launch {
            val result = repository.deleteRequest(requestId)
            actionResult.postValue(result)
        }
    }

    fun updateRequest(requestId: String, title: String, description: String) {
        viewModelScope.launch {
            val updates = mapOf(
                "title" to title,
                "description" to description
            )
            val result = repository.updateRequest(requestId, updates)
            actionResult.postValue(result)
        }
    }

    fun resetActionResult() {
        actionResult.value = null
    }

    override fun onCleared() {
        super.onCleared()
        listenerReg?.remove()
    }
}
