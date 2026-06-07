package com.example.neighbour_help_network.ui.auth

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Html
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat
import androidx.lifecycle.lifecycleScope
import com.example.neighbour_help_network.R
import com.example.neighbour_help_network.data.repository.AuthRepository
import com.example.neighbour_help_network.databinding.ActivityVerificationBinding
import com.example.neighbour_help_network.ui.main.MainActivity
import kotlinx.coroutines.launch

/**
 * EmailVerificationActivity — Hard-enforcement verification screen.
 * Shows instructions, verification status checks, resend actions with cooldowns,
 * and standard logout navigation back to login if required.
 */
class EmailVerificationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVerificationBinding
    private val repository = AuthRepository()
    private var resendTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVerificationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val currentUser = repository.currentUser
        if (currentUser == null) {
            navigateToLogin()
            return
        }

        // Display user email
        val email = currentUser.email ?: ""
        val formattedDesc = getString(R.string.desc_email_verification, email)
        binding.tvVerificationDesc.text = HtmlCompat.fromHtml(formattedDesc, HtmlCompat.FROM_HTML_MODE_LEGACY)

        setupButtons()
    }

    private fun setupButtons() {
        binding.btnCheckStatus.setOnClickListener {
            checkVerificationStatus()
        }

        binding.btnResendEmail.setOnClickListener {
            resendVerificationEmail()
        }

        binding.btnBackToLogin.setOnClickListener {
            logOutAndNavigateBack()
        }
    }

    private fun checkVerificationStatus() {
        setLoadingState(true)
        lifecycleScope.launch {
            val result = repository.reloadUser()
            setLoadingState(false)
            
            val user = result.getOrNull()
            if (user != null && user.isEmailVerified) {
                Toast.makeText(this@EmailVerificationActivity, "Email verified successfully!", Toast.LENGTH_SHORT).show()
                navigateToMain()
            } else {
                Toast.makeText(this@EmailVerificationActivity, getString(R.string.msg_please_verify_email), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun resendVerificationEmail() {
        setLoadingState(true)
        lifecycleScope.launch {
            val result = repository.sendEmailVerification()
            setLoadingState(false)
            if (result.isSuccess) {
                Toast.makeText(this@EmailVerificationActivity, getString(R.string.msg_verification_email_sent), Toast.LENGTH_SHORT).show()
                startResendCooldown()
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Failed to send verification email."
                Toast.makeText(this@EmailVerificationActivity, errorMsg, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startResendCooldown() {
        binding.btnResendEmail.isEnabled = false
        resendTimer?.cancel()
        resendTimer = object : CountDownTimer(30000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsRemaining = millisUntilFinished / 1000
                binding.btnResendEmail.text = "Resend in ${secondsRemaining}s"
            }

            override fun onFinish() {
                binding.btnResendEmail.isEnabled = true
                binding.btnResendEmail.setText(R.string.btn_resend_verification_email)
            }
        }.start()
    }

    private fun logOutAndNavigateBack() {
        repository.signOut()
        navigateToLogin()
    }

    private fun setLoadingState(isLoading: Boolean) {
        binding.progressVerification.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnCheckStatus.isEnabled = !isLoading
        binding.btnBackToLogin.isEnabled = !isLoading
        if (!isLoading && resendTimer == null) {
            binding.btnResendEmail.isEnabled = true
        } else if (isLoading) {
            binding.btnResendEmail.isEnabled = false
        }
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun navigateToLogin() {
        startActivity(Intent(this, AuthActivity::class.java))
        finish()
    }

    override fun onDestroy() {
        resendTimer?.cancel()
        super.onDestroy()
    }
}
