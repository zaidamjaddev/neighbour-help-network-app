package com.example.neighbour_help_network.ui.main.post

import android.Manifest
import android.animation.ObjectAnimator
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.neighbour_help_network.R
import com.example.neighbour_help_network.data.local.LocalAiEngine
import com.example.neighbour_help_network.databinding.FragmentPostRequestBinding
import com.google.android.gms.location.LocationServices
import com.google.android.material.chip.Chip

/**
 * PostRequestFragment — Multi-step form for posting a help request.
 */
class PostRequestFragment : Fragment() {

    private var _binding: FragmentPostRequestBinding? = null
    private val binding get() = _binding!!
    private val viewModel: PostRequestViewModel by viewModels()

    private val fusedLocationClient by lazy {
        LocationServices.getFusedLocationProviderClient(requireActivity())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPostRequestBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupAnalyzeButton()
        setupSubmitButton()
        observeViewModel()
    }

    private fun setupAnalyzeButton() {
        binding.btnAnalyzeAi.setOnClickListener {
            val description = binding.etPostDescription.text.toString().trim()
            if (description.isBlank()) {
                binding.tilPostDescription.error = getString(R.string.error_field_required)
                return@setOnClickListener
            }
            binding.tilPostDescription.error = null
            viewModel.analyzeDescription(description)
        }
    }

    private fun setupSubmitButton() {
        binding.btnSubmitRequest.setOnClickListener {
            val title = binding.etPostTitle.text.toString().trim()
            val description = binding.etPostDescription.text.toString().trim()

            var isValid = true
            if (title.isBlank()) {
                binding.tilPostTitle.error = getString(R.string.error_field_required)
                isValid = false
            } else {
                binding.tilPostTitle.error = null
            }
            if (description.isBlank()) {
                binding.tilPostDescription.error = getString(R.string.error_field_required)
                isValid = false
            } else {
                binding.tilPostDescription.error = null
            }

            if (!isValid) return@setOnClickListener

            // Get location before submitting
            if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    val lat = location?.latitude ?: 0.0
                    val lon = location?.longitude ?: 0.0
                    viewModel.submitRequest(title, description, lat, lon)
                }.addOnFailureListener {
                    viewModel.submitRequest(title, description, 0.0, 0.0)
                }
            } else {
                // Submit without location if permission missing (or request it)
                viewModel.submitRequest(title, description, 0.0, 0.0)
            }
        }
    }

    private fun observeViewModel() {
        viewModel.aiResult.observe(viewLifecycleOwner) { result ->
            result ?: return@observe
            showAiResultCard(result)
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressSubmit.visibility = if (loading) View.VISIBLE else View.GONE
            binding.btnSubmitRequest.isEnabled = !loading
        }

        viewModel.submitResult.observe(viewLifecycleOwner) { result ->
            result ?: return@observe
            if (result.isSuccess) {
                Toast.makeText(requireContext(), getString(R.string.msg_request_submitted), Toast.LENGTH_LONG).show()
                clearForm()
            } else {
                Toast.makeText(requireContext(), "Submission failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
            }
            viewModel.consumeSubmitResult()
        }
    }

    private fun showAiResultCard(result: LocalAiEngine.AiAnalysisResult) {
        binding.tvAiCategory.text = result.predictedCategory
        binding.tvAiUrgencyLevel.text = result.urgencyLevel
        binding.tvAiScore.text = "${result.urgencyScore}/100"
        binding.progressAiScore.progress = result.urgencyScore

        val urgencyColor = requireContext().getColor(
            when {
                result.urgencyScore >= 75 -> R.color.colorEmergency      // Red (#DC2626)
                result.urgencyScore >= 45 -> R.color.colorPrimary       // Blue (#2563EB)
                else                      -> R.color.colorAccent         // Emerald Green (#059669)
            }
        )
        binding.tvAiUrgencyLevel.setTextColor(urgencyColor)
        binding.progressAiScore.setIndicatorColor(urgencyColor)

        binding.chipGroupTags.removeAllViews()
        result.automatedTags.forEach { tag ->
            val chip = Chip(requireContext()).apply {
                text = "#$tag"
                setChipBackgroundColorResource(R.color.colorSurfaceVariant)
                setTextColor(requireContext().getColor(R.color.colorPrimary))
                isClickable = false
            }
            binding.chipGroupTags.addView(chip)
        }

        if (binding.cardAiResult.visibility != View.VISIBLE) {
            binding.cardAiResult.alpha = 0f
            binding.cardAiResult.visibility = View.VISIBLE
            ObjectAnimator.ofFloat(binding.cardAiResult, "alpha", 0f, 1f)
                .apply { duration = 350 }
                .start()
        }
    }

    private fun clearForm() {
        binding.etPostTitle.text?.clear()
        binding.etPostDescription.text?.clear()
        binding.cardAiResult.visibility = View.GONE
        binding.chipGroupTags.removeAllViews()
        viewModel.aiResult.value = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
