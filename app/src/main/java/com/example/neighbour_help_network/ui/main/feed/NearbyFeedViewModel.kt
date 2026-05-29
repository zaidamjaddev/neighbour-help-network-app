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
    val isLoading = MutableLiveData<Boolean>(false)
    val actionResult = MutableLiveData<Result<Unit>?>()
    
    // Tracks current tab to ensure we don't show the wrong empty message
    var currentTabPosition = 0

    init {
        showActiveRequests()
    }

    fun showActiveRequests() {
        currentTabPosition = 0
        startListening(listOf("open", "accepted"))
    }

    fun showHistoryRequests() {
        currentTabPosition = 1
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        
        isLoading.value = true
        listenerReg?.remove()
        listenerReg = repository.listenToUserHistory(
            userId = uid,
            onUpdate = { list ->
                isLoading.postValue(false)
                requests.postValue(list)
            },
            onError = {
                isLoading.postValue(false)
            }
        )
    }

    private fun startListening(statusList: List<String>) {
        isLoading.value = true
        listenerReg?.remove()
        listenerReg = repository.listenToRequestsByStatus(
            statusList = statusList,
            onUpdate = { list ->
                isLoading.postValue(false)
                requests.postValue(list)
            },
            onError = {
                isLoading.postValue(false)
            }
        )
    }

    fun acceptRequest(requestId: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        viewModelScope.launch {
            val result = repository.acceptRequest(requestId, uid)
            actionResult.postValue(result)
        }
    }

    fun completeRequest(requestId: String) {
        viewModelScope.launch {
            val result = repository.completeRequest(requestId)
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
