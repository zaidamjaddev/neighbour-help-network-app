package com.example.neighbour_help_network.ui.main.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
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
        if (uri != null && isAdded) {
            val photoBytes = com.example.neighbour_help_network.utils.ImageUtils.compressImageFromUri(requireContext(), uri)
            if (photoBytes != null) {
                val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                    ?: System.currentTimeMillis().toString()
                val filename = "profile_${uid}_${System.currentTimeMillis()}.jpg"
                val savedPath = com.example.neighbour_help_network.utils.ImageUtils.saveProfileImage(requireContext(), filename, photoBytes)
                if (savedPath != null) {
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
                    Toast.makeText(context, "Failed to save image", Toast.LENGTH_SHORT).show()
                }
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
        val mainActivity = activity as? MainActivity ?: return
        binding.toolbarSettings.title = getString(R.string.label_settings_title)
        // Manual navigation setup to avoid ConcurrentModificationException
        binding.toolbarSettings.setNavigationIcon(R.drawable.ic_home) 
        binding.toolbarSettings.setNavigationOnClickListener {
            mainActivity.openDrawer()
        }
    }

    private fun setupListeners() {
        binding.flSettingsAvatarPicker.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        binding.btnUpdateProfile.setOnClickListener {
            val name = binding.etFullName.text.toString().trim()
            val phone = binding.etPhone.text.toString().trim()

            if (name.isEmpty() || phone.isEmpty()) {
                context?.let { Toast.makeText(it, getString(R.string.error_field_required), Toast.LENGTH_SHORT).show() }
                return@setOnClickListener
            }
            viewModel.updateProfile(name, phone)
        }

        binding.btnNotifications.setOnClickListener {
            val intent = Intent().apply {
                action = "android.settings.APP_NOTIFICATION_SETTINGS"
                putExtra("android.provider.extra.APP_PACKAGE", context?.packageName)
            }
            try {
                startActivity(intent)
            } catch (e: Exception) {
                context?.let { Toast.makeText(it, "Unable to open settings", Toast.LENGTH_SHORT).show() }
            }
        }

        binding.btnPrivacyPolicy.setOnClickListener {
            context?.let { ctx ->
                AlertDialog.Builder(ctx)
                    .setTitle(R.string.label_privacy_policy)
                    .setMessage("Your data is used solely for connecting you with nearby help requests.")
                    .setPositiveButton("Close", null)
                    .show()
            }
        }

        binding.btnLogout.setOnClickListener {
            showLogoutConfirmation()
        }
    }

    private fun observeViewModel() {
        viewModel.userProfile.observe(viewLifecycleOwner) { user ->
            _binding?.let { b ->
                user?.let {
                    b.etFullName.setText(it.displayName)
                    b.etEmail.setText(it.email)
                    b.etPhone.setText(it.phone)

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
                                .into(b.ivSettingsAvatar)
                        } else {
                            b.ivSettingsAvatar.setImageResource(R.drawable.ic_person_placeholder)
                        }
                    }
                }
            }
        }

        viewModel.updateStatus.observe(viewLifecycleOwner) { result ->
            result?.let {
                if (it.isSuccess) {
                    context?.let { ctx -> Toast.makeText(ctx, getString(R.string.msg_profile_updated), Toast.LENGTH_SHORT).show() }
                }
                viewModel.resetUpdateStatus()
            }
        }

        viewModel.photoUpdateStatus.observe(viewLifecycleOwner) { result ->
            result?.let {
                if (it.isSuccess) {
                    context?.let { ctx -> Toast.makeText(ctx, "Profile photo updated successfully!", Toast.LENGTH_SHORT).show() }
                    (activity as? MainActivity)?.refreshUserHeader()
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
        context?.let { ctx ->
            AlertDialog.Builder(ctx)
                .setTitle(R.string.btn_logout)
                .setMessage(R.string.msg_logout_confirm)
                .setPositiveButton(R.string.btn_logout) { _, _ ->
                    viewModel.logout()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun navigateToAuth() {
        val ctx = context ?: return
        val intent = Intent(ctx, AuthActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        activity?.finish()
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
