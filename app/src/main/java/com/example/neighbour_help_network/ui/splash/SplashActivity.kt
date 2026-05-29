package com.example.neighbour_help_network.ui.splash

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.example.neighbour_help_network.databinding.ActivitySplashBinding
import com.example.neighbour_help_network.ui.auth.AuthActivity
import com.example.neighbour_help_network.ui.main.MainActivity
import com.google.firebase.auth.FirebaseAuth

@SuppressLint("CustomSplash")
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 3000ms Delay
        Handler(Looper.getMainLooper()).postDelayed({
            checkAuthState()
        }, 3000)
    }

    private fun checkAuthState() {
        val user = FirebaseAuth.getInstance().currentUser
        if (user != null) {
            // User is signed in, go to MainActivity
            startActivity(Intent(this, MainActivity::class.java))
        } else {
            // No user is signed in, go to AuthActivity
            startActivity(Intent(this, AuthActivity::class.java))
        }
        finish()
    }
}
