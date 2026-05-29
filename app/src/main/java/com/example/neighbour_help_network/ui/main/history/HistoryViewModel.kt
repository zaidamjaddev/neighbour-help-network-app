package com.example.neighbour_help_network.ui.main.history

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.neighbour_help_network.data.model.HelpRequest
import com.example.neighbour_help_network.data.repository.HelpRequestRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration

/**
 * HistoryViewModel — Manages the resolved help requests for the current user.
 */
class HistoryViewModel : ViewModel() {

    private val repository = HelpRequestRepository()
    private var listenerReg: ListenerRegistration? = null

    val historyRequests = MutableLiveData<List<HelpRequest>>(emptyList())
    val isLoading = MutableLiveData<Boolean>(true)

    init {
        startListening()
    }

    private fun startListening() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        isLoading.value = true
        // Using positional arguments to ensure correct parameter mapping
        listenerReg = repository.listenToUserHistory(
            uid,
            { list ->
                isLoading.postValue(false)
                historyRequests.postValue(list)
            },
            { error ->
                isLoading.postValue(false)
            }
        )
    }

    override fun onCleared() {
        super.onCleared()
        listenerReg?.remove()
    }
}
