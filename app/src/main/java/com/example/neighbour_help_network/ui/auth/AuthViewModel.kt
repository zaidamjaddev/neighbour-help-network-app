package com.example.neighbour_help_network.ui.auth

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.neighbour_help_network.data.repository.AuthRepository
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.launch

/**
 * AuthViewModel — Manages authentication state for AuthActivity.
 *
 * Exposes a single [authState] LiveData stream using the sealed AuthState class,
 * making it trivial for the View to react to Loading / Success / Error transitions.
 */
class AuthViewModel : ViewModel() {

    private val repository = AuthRepository()

    private val _authState = MutableLiveData<AuthState>()
    val authState: LiveData<AuthState> = _authState

    /** Returns the current Firebase user, or null if not signed in. */
    val currentUser: FirebaseUser? get() = repository.currentUser

    /**
     * Attempts to log in with email + password.
     * Posts Loading → Success | Error to [authState].
     */
    fun login(email: String, password: String) {
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            val result = repository.login(email, password)
            _authState.value = if (result.isSuccess) {
                AuthState.Success(result.getOrThrow())
            } else {
                AuthState.Error(result.exceptionOrNull()?.message ?: "Login failed. Please try again.")
            }
        }
    }

    /**
     * Creates a new account and writes the profile to Firestore.
     * Optionally uploads a profile photo to Firebase Storage.
     * Posts Loading → Success | Error to [authState].
     */
    fun signup(name: String, email: String, password: String, phone: String, photoLocalPath: String? = null) {
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            val result = repository.signup(name, email, password, phone, photoLocalPath)
            _authState.value = if (result.isSuccess) {
                AuthState.Success(result.getOrThrow())
            } else {
                AuthState.Error(result.exceptionOrNull()?.message ?: "Sign-up failed. Please try again.")
            }
        }
    }

    /** Sealed UI-state contract for the Auth screen. */
    sealed class AuthState {
        object Loading : AuthState()
        data class Success(val user: FirebaseUser) : AuthState()
        data class Error(val message: String) : AuthState()
    }
}
