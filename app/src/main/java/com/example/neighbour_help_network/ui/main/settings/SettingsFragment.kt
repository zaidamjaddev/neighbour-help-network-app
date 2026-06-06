package com.example.neighbour_help_network.ui.main.settings

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.neighbour_help_network.R
import com.example.neighbour_help_network.databinding.FragmentSettingsBinding
import com.example.neighbour_help_network.ui.auth.AuthActivity
import com.example.neighbour_help_network.ui.main.MainActivity

/**
 * SettingsFragment — Allows users to update their profile, manage notifications, and logout.
 */
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SettingsViewModel by viewModels()

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val photoBytes = com.example.neighbour_help_network.utils.ImageUtils.compressImageFromUri(requireContext(), uri)
            if (photoBytes != null) {
                // Save to internal storage and update Firestore with the local path
                val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                    ?: System.currentTimeMillis().toString()
                val filename = "profile_${uid}_${System.currentTimeMillis()}.jpg"
                val savedPath = com.example.neighbour_help_network.utils.ImageUtils.saveProfileImage(requireContext(), filename, photoBytes)
                if (savedPath != null) {
                    // Show immediately
                    Glide.with(this)
                        .load(java.io.File(savedPath))
                        .circleCrop()
                        .placeholder(R.drawable.ic_person_placeholder)
                        .error(R.drawable.ic_person_placeholder)
                        .skipMemoryCache(true)
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .into(binding.ivSettingsAvatar)

                    viewModel.updateProfilePhotoPath(savedPath)
                } else {
                    Toast.makeText(requireContext(), "Failed to save image", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(requireContext(), "Failed to read image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupListeners()
        observeViewModel()
    }

    private fun setupToolbar() {
        val navController = findNavController()
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.dashboardFragment,
                R.id.nearbyFeedFragment,
                R.id.acceptedRequestsFragment,
                R.id.postRequestFragment,
                R.id.liveChatFragment,
                R.id.settingsFragment
            ),
            (activity as? AppCompatActivity)?.findViewById(R.id.drawerLayout)
        )
        binding.toolbarSettings.setupWithNavController(navController, appBarConfiguration)
    }

    private fun setupListeners() {
        binding.flSettingsAvatarPicker.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        binding.btnUpdateProfile.setOnClickListener {
            val name = binding.etFullName.text.toString().trim()
            val phone = binding.etPhone.text.toString().trim()

            if (name.isEmpty() || phone.isEmpty()) {
                Toast.makeText(requireContext(), getString(R.string.error_field_required), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewModel.updateProfile(name, phone)
        }

        binding.btnNotifications.setOnClickListener {
            val intent = Intent().apply {
                action = "android.settings.APP_NOTIFICATION_SETTINGS"
                putExtra("android.provider.extra.APP_PACKAGE", requireContext().packageName)
            }
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Unable to open settings", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnPrivacyPolicy.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.label_privacy_policy)
                .setMessage("Your data is used solely for connecting you with nearby help requests. Location data is only shared with others when you actively post or accept a request.")
                .setPositiveButton("Close", null)
                .show()
        }

        binding.btnLogout.setOnClickListener {
            showLogoutConfirmation()
        }
    }

    private fun observeViewModel() {
        viewModel.userProfile.observe(viewLifecycleOwner) { user ->
            user?.let {
                binding.etFullName.setText(it.displayName)
                binding.etEmail.setText(it.email)
                binding.etPhone.setText(it.phone)

                val localPath = it.photoUrl.takeIf { url -> url.isNotBlank() }
                if (localPath != null) {
                    val file = java.io.File(localPath)
                    if (file.exists()) {
                        Glide.with(this)
                            .load(file)
                            .circleCrop()
                            .placeholder(R.drawable.ic_person_placeholder)
                            .error(R.drawable.ic_person_placeholder)
                            .skipMemoryCache(true)
                            .diskCacheStrategy(DiskCacheStrategy.NONE)
                            .into(binding.ivSettingsAvatar)
                    } else {
                        binding.ivSettingsAvatar.setImageResource(R.drawable.ic_person_placeholder)
                    }
                } else {
                    binding.ivSettingsAvatar.setImageResource(R.drawable.ic_person_placeholder)
                }
            }
        }

        viewModel.updateStatus.observe(viewLifecycleOwner) { result ->
            result?.let {
                if (it.isSuccess) {
                    Toast.makeText(requireContext(), getString(R.string.msg_profile_updated), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Update failed: ${it.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                }
                viewModel.resetUpdateStatus()
            }
        }

        // IMAGE UPLOAD DISABLED
        viewModel.photoUpdateStatus.observe(viewLifecycleOwner) { result ->
            result?.let {
                if (it.isSuccess) {
                    Toast.makeText(requireContext(), "Profile photo updated successfully!", Toast.LENGTH_SHORT).show()
                    (activity as? MainActivity)?.refreshUserHeader()
                } else {
                    Toast.makeText(requireContext(), "Photo update failed: ${it.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                }
                viewModel.resetPhotoUpdateStatus()
            }
        }

        viewModel.isLoggedOut.observe(viewLifecycleOwner) { loggedOut ->
            if (loggedOut) {
                navigateToAuth()
            }
        }
    }

    private fun showLogoutConfirmation() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.btn_logout)
            .setMessage(R.string.msg_logout_confirm)
            .setPositiveButton(R.string.btn_logout) { _, _ ->
                viewModel.logout()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun navigateToAuth() {
        val intent = Intent(requireContext(), AuthActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        requireActivity().finish()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onResume() {
        super.onResume()
        viewModel.fetchUserProfile()
        (activity as? MainActivity)?.refreshUserHeader()
    }
}
