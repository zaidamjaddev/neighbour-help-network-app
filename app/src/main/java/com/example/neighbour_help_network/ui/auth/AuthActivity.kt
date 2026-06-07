package com.example.neighbour_help_network.ui.auth

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.neighbour_help_network.R
import com.example.neighbour_help_network.data.repository.AuthRepository
import com.example.neighbour_help_network.databinding.ActivityAuthBinding
import com.example.neighbour_help_network.ui.main.MainActivity
// IMAGE UPLOAD DISABLED: import com.example.neighbour_help_network.utils.ImageUtils
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * AuthActivity — Single-screen Login + Signup onboarding experience.
 *
 * Uses a tab toggle (MaterialButtonToggleGroup) to switch between the
 * Login and Signup layouts within a single card, with smooth fade animation.
 *
 * Observes AuthViewModel.authState for Loading / Success / Error transitions.
 * On Success: saves FCM token to Firestore, then launches MainActivity.
 */
class AuthActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAuthBinding
    private val viewModel: AuthViewModel by viewModels()

    // IMAGE UPLOAD DISABLED
    // private var selectedPhotoBytes: ByteArray? = null
    private var selectedPhotoLocalPath: String? = null
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val bytes = com.example.neighbour_help_network.utils.ImageUtils.compressImageFromUri(this, uri)
            if (bytes != null) {
                // Save locally and display
                val filename = "signup_${System.currentTimeMillis()}.jpg"
                val savedPath = com.example.neighbour_help_network.utils.ImageUtils.saveProfileImage(this, filename, bytes)
                if (savedPath != null) {
                    selectedPhotoLocalPath = savedPath
                    binding.ivSignupAvatar.setImageBitmap(BitmapFactory.decodeFile(savedPath))
                } else {
                    Toast.makeText(this, "Failed to save image. Please try another.", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Failed to read image. Please try another.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // If already signed in, go straight to MainActivity
        if (viewModel.currentUser != null) {
            saveFcmTokenThenNavigate()
            return
        }

        setupTabToggle()
        setupLoginButton()
        setupSignupButton()
        setupAvatarPicker()
        observeAuthState()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tab toggle
    // ─────────────────────────────────────────────────────────────────────────

    private fun setupTabToggle() {
        val fadeIn = AnimationUtils.loadAnimation(this, android.R.anim.fade_in)

        binding.btnTabLogin.isChecked = true

        binding.toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            when (checkedId) {
                R.id.btnTabLogin -> {
                    binding.layoutLogin.startAnimation(fadeIn)
                    binding.layoutLogin.visibility = View.VISIBLE
                    binding.layoutSignup.visibility = View.GONE
                }
                R.id.btnTabSignup -> {
                    binding.layoutSignup.startAnimation(fadeIn)
                    binding.layoutSignup.visibility = View.VISIBLE
                    binding.layoutLogin.visibility = View.GONE
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Avatar Picker — IMAGE UPLOAD DISABLED
    // ─────────────────────────────────────────────────────────────────────────

    private fun setupAvatarPicker() {
        binding.flAvatarPicker.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Login
    // ─────────────────────────────────────────────────────────────────────────

    private fun setupLoginButton() {
        binding.btnLogin.setOnClickListener {
            clearLoginErrors()
            val email = binding.etLoginEmail.text.toString().trim()
            val password = binding.etLoginPassword.text.toString()

            if (!validateLoginFields(email, password)) return@setOnClickListener

            viewModel.login(email, password)
        }
    }

    private fun validateLoginFields(email: String, password: String): Boolean {
        var isValid = true
        if (email.isBlank()) {
            binding.tilLoginEmail.error = getString(R.string.error_field_required)
            isValid = false
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilLoginEmail.error = getString(R.string.error_invalid_email)
            isValid = false
        }
        if (password.isBlank()) {
            binding.tilLoginPassword.error = getString(R.string.error_field_required)
            isValid = false
        } else if (password.length < 6) {
            binding.tilLoginPassword.error = getString(R.string.error_password_short)
            isValid = false
        }
        return isValid
    }

    private fun clearLoginErrors() {
        binding.tilLoginEmail.error = null
        binding.tilLoginPassword.error = null
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Signup
    // ─────────────────────────────────────────────────────────────────────────

    private fun setupSignupButton() {
        binding.btnSignup.setOnClickListener {
            clearSignupErrors()
            val name = binding.etSignupName.text.toString().trim()
            val email = binding.etSignupEmail.text.toString().trim()
            val phone = binding.etSignupPhone.text.toString().trim()
            val password = binding.etSignupPassword.text.toString()
            val confirmPassword = binding.etSignupConfirmPassword.text.toString()

            if (!validateSignupFields(name, email, phone, password, confirmPassword)) return@setOnClickListener

            viewModel.signup(name, email, password, phone, selectedPhotoLocalPath)
        }
    }

    private fun validateSignupFields(
        name: String, email: String, phone: String,
        password: String, confirmPassword: String
    ): Boolean {
        var isValid = true
        if (name.isBlank()) {
            binding.tilSignupName.error = getString(R.string.error_field_required)
            isValid = false
        }
        if (email.isBlank()) {
            binding.tilSignupEmail.error = getString(R.string.error_field_required)
            isValid = false
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilSignupEmail.error = getString(R.string.error_invalid_email)
            isValid = false
        }
        if (phone.isBlank()) {
            binding.tilSignupPhone.error = getString(R.string.error_field_required)
            isValid = false
        }
        if (password.isBlank()) {
            binding.tilSignupPassword.error = getString(R.string.error_field_required)
            isValid = false
        } else if (password.length < 6) {
            binding.tilSignupPassword.error = getString(R.string.error_password_short)
            isValid = false
        }
        if (confirmPassword != password) {
            binding.tilSignupConfirmPassword.error = getString(R.string.error_passwords_mismatch)
            isValid = false
        }
        return isValid
    }

    private fun clearSignupErrors() {
        listOf(
            binding.tilSignupName,
            binding.tilSignupEmail,
            binding.tilSignupPhone,
            binding.tilSignupPassword,
            binding.tilSignupConfirmPassword
        ).forEach { it.error = null }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Observe ViewModel
    // ─────────────────────────────────────────────────────────────────────────

    private fun observeAuthState() {
        viewModel.authState.observe(this) { state ->
            when (state) {
                is AuthViewModel.AuthState.Loading -> {
                    setLoadingState(true)
                }
                is AuthViewModel.AuthState.Success -> {
                    setLoadingState(false)
                    saveFcmTokenThenNavigate()
                }
                is AuthViewModel.AuthState.Error -> {
                    setLoadingState(false)
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun setLoadingState(loading: Boolean) {
        binding.progressAuth.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnLogin.isEnabled = !loading
        binding.btnSignup.isEnabled = !loading
        binding.toggleGroup.isEnabled = !loading
        binding.flAvatarPicker.isEnabled = !loading
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FCM Token + Navigation
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Fetches the current FCM registration token and saves it to Firestore
     * so Cloud Functions can send targeted push notifications to this device.
     * Navigates to MainActivity regardless of the token result.
     */
    private fun saveFcmTokenThenNavigate() {
        FirebaseMessaging.getInstance().token
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val token = task.result
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            AuthRepository().saveFcmToken(token)
                            Log.d("AuthActivity", "FCM token saved on login")
                        } catch (e: Exception) {
                            Log.e("AuthActivity", "FCM token save failed: ${e.message}")
                        }
                    }
                }
                navigateToNextScreen()
            }
    }

    private fun navigateToNextScreen() {
        val user = viewModel.currentUser
        if (user != null && !user.isEmailVerified) {
            startActivity(Intent(this, EmailVerificationActivity::class.java))
        } else {
            startActivity(Intent(this, MainActivity::class.java))
        }
        finish()
    }
}
