package com.example.neighbour_help_network.ui.main.settings

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NotificationCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
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

        setupListeners()
        observeViewModel()
    }

    private fun setupListeners() {
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

//        binding.btnTestNotification.setOnClickListener {
//            sendTestNotification()
//        }

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

    private fun sendTestNotification() {
        val channelId = "neighbour_help_alerts"
        val notificationManager = requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Emergency & Help Alerts",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(requireContext(), MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            requireContext(), 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(requireContext(), channelId)
            .setSmallIcon(R.drawable.ic_sos)
            .setContentTitle("Test Notification")
            .setContentText("This is a test alert from Neighbour Help Network!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(1, notification)
        Toast.makeText(requireContext(), "Notification Sent!", Toast.LENGTH_SHORT).show()
    }

    private fun observeViewModel() {
        viewModel.userProfile.observe(viewLifecycleOwner) { user ->
            user?.let {
                binding.etFullName.setText(it.displayName)
                binding.etEmail.setText(it.email)
                binding.etPhone.setText(it.phone)
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
}
