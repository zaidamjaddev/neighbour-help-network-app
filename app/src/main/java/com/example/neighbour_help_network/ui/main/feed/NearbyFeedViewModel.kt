package com.example.neighbour_help_network.ui.main.feed

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.neighbour_help_network.data.model.HelpRequest
import com.example.neighbour_help_network.data.repository.HelpRequestRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch

class NearbyFeedViewModel : ViewModel() {

    private val repository = HelpRequestRepository()
    private val firestore  = FirebaseFirestore.getInstance()
    private var listenerReg: ListenerRegistration? = null

    val requests     = MutableLiveData<List<HelpRequest>>(emptyList())
    val isLoading    = MutableLiveData<Boolean>(false)
    val actionResult = MutableLiveData<Result<Unit>?>()

    init {
        showActiveRequests()
    }

    fun showActiveRequests() {
        startListening(listOf("open", "accepted"))
    }

    fun showHistoryRequests() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        isLoading.value = true
        listenerReg?.remove()
        listenerReg = repository.listenToUserHistory(
            userId = uid,
            onUpdate = { list ->
                isLoading.postValue(false)
                requests.postValue(list)
            },
            onError = { isLoading.postValue(false) }
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
            onError = { isLoading.postValue(false) }
        )
    }

    fun acceptRequest(requestId: String) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val uid = user.uid
        val name = user.displayName ?: "Neighbor"

        viewModelScope.launch {
            // Updated to pass user name for the chat list
            val result = repository.acceptRequest(requestId, uid, name)
            actionResult.postValue(result)

            if (result.isSuccess) {
                val trigger = mapOf(
                    "type"          to "request_accepted",
                    "requestId"     to requestId,
                    "acceptedByUid" to uid,
                    "timestamp"     to com.google.firebase.Timestamp.now()
                )
                firestore.collection("notification_triggers").add(trigger)
            }
        }
    }

    fun completeRequest(requestId: String) {
        viewModelScope.launch {
            actionResult.postValue(repository.completeRequest(requestId))
        }
    }

    fun deleteRequest(requestId: String) {
        viewModelScope.launch {
            actionResult.postValue(repository.deleteRequest(requestId))
        }
    }

    fun updateRequest(requestId: String, title: String, description: String) {
        viewModelScope.launch {
            val updates = mapOf("title" to title, "description" to description)
            actionResult.postValue(repository.updateRequest(requestId, updates))
        }
    }

    fun resetActionResult() { actionResult.value = null }

    override fun onCleared() {
        super.onCleared()
        listenerReg?.remove()
    }
}
