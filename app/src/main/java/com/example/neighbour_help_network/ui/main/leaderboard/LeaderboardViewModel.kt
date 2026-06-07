package com.example.neighbour_help_network.ui.main.leaderboard

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.neighbour_help_network.data.model.User
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

class LeaderboardViewModel : ViewModel() {

    private val firestore = FirebaseFirestore.getInstance()
    private var listenerRegistration: ListenerRegistration? = null

    private val _topUsers = MutableLiveData<List<User>>()
    val topUsers: LiveData<List<User>> = _topUsers

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    init {
        fetchTopUsers()
    }

    private fun fetchTopUsers() {
        _isLoading.value = true
        listenerRegistration = firestore.collection("users")
            .orderBy("totalPoints", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, error ->
                _isLoading.value = false
                if (error != null) return@addSnapshotListener
                
                val users = snapshot?.toObjects(User::class.java) ?: emptyList()
                _topUsers.value = users
            }
    }

    override fun onCleared() {
        super.onCleared()
        listenerRegistration?.remove()
    }
}
