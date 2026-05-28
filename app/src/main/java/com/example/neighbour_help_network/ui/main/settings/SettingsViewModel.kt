package com.example.neighbour_help_network.ui.main.settings

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.neighbour_help_network.data.model.User
import com.example.neighbour_help_network.data.repository.AuthRepository
import kotlinx.coroutines.launch

/**
 * SettingsViewModel — Manages profile updates and logout logic.
 */
class SettingsViewModel : ViewModel() {

    private val repository = AuthRepository()

    private val _userProfile = MutableLiveData<User?>()
    val userProfile: LiveData<User?> = _userProfile

    private val _updateStatus = MutableLiveData<Result<Unit>?>()
    val updateStatus: LiveData<Result<Unit>?> = _updateStatus

    private val _isLoggedOut = MutableLiveData<Boolean>(false)
    val isLoggedOut: LiveData<Boolean> = _isLoggedOut

    init {
        fetchUserProfile()
    }

    private fun fetchUserProfile() {
        viewModelScope.launch {
            val result = repository.getUserProfile()
            if (result.isSuccess) {
                _userProfile.value = result.getOrNull()
            }
        }
    }

    fun updateProfile(name: String, phone: String) {
        viewModelScope.launch {
            val updates = mapOf(
                "displayName" to name,
                "phone" to phone
            )
            val result = repository.updateUserProfile(updates)
            _updateStatus.value = result
            if (result.isSuccess) {
                fetchUserProfile() // Refresh local data
            }
        }
    }

    fun logout() {
        repository.signOut()
        _isLoggedOut.value = true
    }

    fun resetUpdateStatus() {
        _updateStatus.value = null
    }
}
