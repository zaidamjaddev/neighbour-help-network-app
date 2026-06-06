package com.example.neighbour_help_network.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupWithNavController
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.bumptech.glide.request.RequestOptions
import com.example.neighbour_help_network.R
import com.example.neighbour_help_network.data.repository.AuthRepository
import com.example.neighbour_help_network.databinding.ActivityMainBinding
import com.example.neighbour_help_network.ui.auth.AuthActivity
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

/**
 * MainActivity — Host for the side navigation drawer.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var appBarConfiguration: AppBarConfiguration
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val authRepository = AuthRepository()

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
        updateNavHeader()
        askNotificationPermission()
    }

    override fun onStart() {
        super.onStart()
        if (auth.currentUser == null) {
            navigateToAuth()
        }
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.navHostFragment) as NavHostFragment
        navController = navHostFragment.navController

        // Top-level destinations (those without a back button)
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.dashboardFragment,
                R.id.nearbyFeedFragment,
                R.id.acceptedRequestsFragment,
                R.id.postRequestFragment,
                R.id.liveChatFragment,
                R.id.settingsFragment
            ),
            binding.drawerLayout
        )

        binding.navigationView.setupWithNavController(navController)
    }

    /**
     * Populates the nav drawer header with the user's display name, email, and profile photo.
     * Falls back to a placeholder icon if no photo URL is stored.
     */
    private fun updateNavHeader() {
        val headerView = binding.navigationView.getHeaderView(0)
        val tvUserEmail = headerView.findViewById<TextView>(R.id.tvUserEmail)
        val tvUserName = headerView.findViewById<TextView>(R.id.tvNavUserName)
        val ivUserPhoto = headerView.findViewById<ImageView>(R.id.ivNavUserPhoto)

        // Populate from FirebaseAuth immediately (fast)
        val firebaseUser = auth.currentUser
        tvUserEmail.text = firebaseUser?.email ?: "neighbor@hoodhelp.com"
        tvUserName.text = firebaseUser?.displayName?.takeIf { it.isNotBlank() } ?: "HoodHelp"

        // Load photo from Auth profile if available (may have been set at sign-up)
        val authPhotoUrl = firebaseUser?.photoUrl?.toString()
        if (!authPhotoUrl.isNullOrBlank()) {
            loadAvatar(ivUserPhoto, authPhotoUrl)
        } else {
            Glide.with(this).clear(ivUserPhoto)
            ivUserPhoto.setImageResource(R.drawable.ic_person_placeholder)
        }

        // Also fetch from Firestore for the most up-to-date photoUrl
        lifecycleScope.launch {
            try {
                val result = authRepository.getUserProfile()
                result.getOrNull()?.let { user ->
                    if (user.displayName.isNotBlank()) tvUserName.text = user.displayName
                    if (user.photoUrl.isNotBlank()) loadAvatar(ivUserPhoto, user.photoUrl)
                }
            } catch (_: Exception) { /* silently ignore */ }
        }
    }

    fun refreshUserHeader() {
        updateNavHeader()
    }

    private fun loadAvatar(imageView: ImageView, url: String) {
        val requestOptions = RequestOptions()
            .placeholder(R.drawable.ic_person_placeholder)
            .error(R.drawable.ic_person_placeholder)
            .circleCrop()

        try {
            val file = java.io.File(url)
            if (file.exists()) {
                Glide.with(this)
                    .load(file)
                    .apply(requestOptions)
                    .skipMemoryCache(true)
                    .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE)
                    .into(imageView)
                return
            }
        } catch (_: Exception) {
            // fall back to URL loading
        }

        Glide.with(this)
            .load(url)
            .apply(requestOptions)
            .into(imageView)
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
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
