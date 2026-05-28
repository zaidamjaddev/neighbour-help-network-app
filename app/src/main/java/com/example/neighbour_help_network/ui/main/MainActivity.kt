package com.example.neighbour_help_network.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.neighbour_help_network.databinding.ActivityMainBinding
import com.example.neighbour_help_network.ui.auth.AuthActivity
import com.google.firebase.auth.FirebaseAuth

/**
 * MainActivity — Single-Activity host for the main navigation graph.
 *
 * Validates Firebase auth state on every resume. If not signed in, redirects to AuthActivity.
 * Hosts the NavHostFragment and wires it to the BottomNavigationView via NavigationUI.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    // Permission launcher for POST_NOTIFICATIONS (Android 13+)
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
            Toast.makeText(this, "Notifications are disabled. You might miss urgent help alerts.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupNavigation()
        askNotificationPermission()
    }

    override fun onStart() {
        super.onStart()
        // Guard: redirect to Auth if session expired
        if (auth.currentUser == null) {
            navigateToAuth()
        }
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(binding.navHostFragment.id) as NavHostFragment
        val navController = navHostFragment.navController

        binding.bottomNavigationView.setupWithNavController(navController)
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun navigateToAuth() {
        startActivity(Intent(this, AuthActivity::class.java))
        finish()
    }
}
