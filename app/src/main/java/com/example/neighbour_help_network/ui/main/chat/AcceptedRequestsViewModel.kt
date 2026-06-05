package com.example.neighbour_help_network.ui.main.chat

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.neighbour_help_network.data.model.HelpRequest
import com.example.neighbour_help_network.data.repository.HelpRequestRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration

class AcceptedRequestsViewModel : ViewModel() {
    private val repository = HelpRequestRepository()
    private val auth = FirebaseAuth.getInstance()
    private var listenerReg: ListenerRegistration? = null

    val acceptedRequests = MutableLiveData<List<HelpRequest>>(emptyList())
    val isLoading = MutableLiveData<Boolean>(false)

    init {
        loadAcceptedRequests()
    }

    private fun loadAcceptedRequests() {
        val uid = auth.currentUser?.uid ?: return
        isLoading.value = true
        
        listenerReg = repository.listenToAcceptedRequests(uid) { list: List<HelpRequest> ->
            isLoading.postValue(false)
            acceptedRequests.postValue(list)
        }
    }

    override fun onCleared() {
        super.onCleared()
        listenerReg?.remove()
    }
}
