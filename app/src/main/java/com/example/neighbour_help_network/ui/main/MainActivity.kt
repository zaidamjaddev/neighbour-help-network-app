package com.example.neighbour_help_network.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import com.bumptech.glide.Glide
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
    
    lateinit var appBarConfiguration: AppBarConfiguration
        private set

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val authRepository = AuthRepository()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
            Toast.makeText(this, "Notifications are disabled.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupNavigation()
        setupBackPressHandler()
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

        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.dashboardFragment,
                R.id.nearbyFeedFragment,
                R.id.acceptedRequestsFragment,
                R.id.postRequestFragment,
                R.id.liveChatFragment,
                R.id.settingsFragment,
                R.id.leaderboardFragment
            ),
            binding.drawerLayout
        )

        // Manual Item Selection to avoid ConcurrentModificationException
        binding.navigationView.setNavigationItemSelectedListener { menuItem ->
            val id = menuItem.itemId
            if (navController.currentDestination?.id != id) {
                // Navigate in a post to ensure no conflict with transition state
                binding.root.post {
                    try {
                        navController.navigate(id)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        // Sync drawer state with current destination
        navController.addOnDestinationChangedListener { _, destination, _ ->
            binding.navigationView.setCheckedItem(destination.id)
        }
    }

    /**
     * Public method for fragments to open the drawer.
     */
    fun openDrawer() {
        binding.drawerLayout.openDrawer(GravityCompat.START)
    }

    private fun setupBackPressHandler() {
        val drawerCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                binding.drawerLayout.closeDrawer(GravityCompat.START)
            }
        }
        onBackPressedDispatcher.addCallback(this, drawerCallback)

        binding.drawerLayout.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerOpened(drawerView: View) {
                drawerCallback.isEnabled = true
            }
            override fun onDrawerClosed(drawerView: View) {
                drawerCallback.isEnabled = false
            }
        })
    }

    private fun updateNavHeader() {
        val headerView = binding.navigationView.getHeaderView(0)
        val tvUserEmail = headerView.findViewById<TextView>(R.id.tvUserEmail)
        val tvUserName = headerView.findViewById<TextView>(R.id.tvNavUserName)
        val ivUserPhoto = headerView.findViewById<ImageView>(R.id.ivNavUserPhoto)

        val firebaseUser = auth.currentUser
        tvUserEmail.text = firebaseUser?.email ?: "neighbor@hoodhelp.com"
        tvUserName.text = firebaseUser?.displayName?.takeIf { it.isNotBlank() } ?: "HoodHelp"

        val authPhotoUrl = firebaseUser?.photoUrl?.toString()
        if (!authPhotoUrl.isNullOrBlank()) {
            loadAvatar(ivUserPhoto, authPhotoUrl)
        } else {
            Glide.with(this).clear(ivUserPhoto)
            ivUserPhoto.setImageResource(R.drawable.ic_person_placeholder)
        }

        lifecycleScope.launch {
            try {
                val result = authRepository.getUserProfile()
                result.getOrNull()?.let { user ->
                    if (user.displayName.isNotBlank()) tvUserName.text = user.displayName
                    if (user.photoUrl.isNotBlank()) loadAvatar(ivUserPhoto, user.photoUrl)
                }
            } catch (_: Exception) { }
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
        } catch (_: Exception) { }

        Glide.with(this)
            .load(url)
            .apply(requestOptions)
            .into(imageView)
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
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
