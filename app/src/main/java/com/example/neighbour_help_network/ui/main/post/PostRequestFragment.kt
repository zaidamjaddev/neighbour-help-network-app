package com.example.neighbour_help_network.ui.main.post

import android.Manifest
import android.animation.ObjectAnimator
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.neighbour_help_network.R
import com.example.neighbour_help_network.data.local.LocalAiEngine
import com.example.neighbour_help_network.databinding.FragmentPostRequestBinding
import com.example.neighbour_help_network.utils.ImageUtils
import com.google.android.gms.location.LocationServices
import com.google.android.material.chip.Chip
import com.example.neighbour_help_network.ui.main.MainActivity

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

    private var selectedRequestImagePath: String? = null
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null || !isAdded) return@registerForActivityResult

        val bytes = ImageUtils.compressImageFromUri(requireContext(), uri)
        if (bytes == null) {
            Toast.makeText(context, "Failed to read image", Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }

        val filename = "request_${System.currentTimeMillis()}.jpg"
        val savedPath = ImageUtils.saveProfileImage(requireContext(), filename, bytes)
        if (savedPath != null) {
            selectedRequestImagePath = savedPath
            viewModel.setRequestImagePath(savedPath)
            _binding?.ivRequestPreview?.setImageBitmap(BitmapFactory.decodeFile(savedPath))
            _binding?.layoutRequestImagePreview?.visibility = View.VISIBLE
        } else {
            Toast.makeText(context, "Failed to save image", Toast.LENGTH_SHORT).show()
        }
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
        setupToolbar()
        setupImagePicker()
        setupAnalyzeButton()
        setupSubmitButton()
        observeViewModel()
    }

    private fun setupImagePicker() {
        binding.btnAddRequestImage.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        binding.btnRemoveRequestImage.setOnClickListener {
            selectedRequestImagePath = null
            viewModel.setRequestImagePath(null)
            binding.layoutRequestImagePreview.visibility = View.GONE
            binding.ivRequestPreview.setImageDrawable(null)
        }
    }

    private fun setupToolbar() {
        val mainActivity = activity as? MainActivity ?: return
        binding.toolbarPost.title = getString(R.string.label_post_title)
        binding.toolbarPost.setNavigationIcon(R.drawable.ic_home)
        binding.toolbarPost.setNavigationOnClickListener {
            mainActivity.openDrawer()
        }
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

            viewModel.setRequestImagePath(selectedRequestImagePath)

            // Get location before submitting
            if (context?.let { ActivityCompat.checkSelfPermission(it, Manifest.permission.ACCESS_FINE_LOCATION) } == PackageManager.PERMISSION_GRANTED) {
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
            _binding?.let { b ->
                b.progressSubmit.visibility = if (loading) View.VISIBLE else View.GONE
                b.btnSubmitRequest.isEnabled = !loading
            }
        }

        viewModel.submitResult.observe(viewLifecycleOwner) { result ->
            result ?: return@observe
            if (result.isSuccess) {
                context?.let { Toast.makeText(it, getString(R.string.msg_request_submitted), Toast.LENGTH_LONG).show() }
                clearForm()
            } else {
                context?.let { Toast.makeText(it, "Submission failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show() }
            }
            viewModel.consumeSubmitResult()
        }
    }

    private fun showAiResultCard(result: LocalAiEngine.AiAnalysisResult) {
        val ctx = context ?: return
        _binding?.let { b ->
            b.tvAiCategory.text = result.predictedCategory
            b.tvAiUrgencyLevel.text = result.urgencyLevel
            b.tvAiScore.text = "${result.urgencyScore}/100"
            b.progressAiScore.progress = result.urgencyScore

            val urgencyColor = ctx.getColor(
                when {
                    result.urgencyScore >= 75 -> R.color.colorEmergency
                    result.urgencyScore >= 45 -> R.color.colorPrimary
                    else                      -> R.color.colorAccent
                }
            )
            b.tvAiUrgencyLevel.setTextColor(urgencyColor)
            b.progressAiScore.setIndicatorColor(urgencyColor)

            b.chipGroupTags.removeAllViews()
            result.automatedTags.forEach { tag ->
                val chip = Chip(ctx).apply {
                    text = "#$tag"
                    setChipBackgroundColorResource(R.color.colorSurfaceVariant)
                    setTextColor(ctx.getColor(R.color.colorPrimary))
                    isClickable = false
                }
                b.chipGroupTags.addView(chip)
            }

            if (b.cardAiResult.visibility != View.VISIBLE) {
                b.cardAiResult.alpha = 0f
                b.cardAiResult.visibility = View.VISIBLE
                ObjectAnimator.ofFloat(b.cardAiResult, "alpha", 0f, 1f)
                    .apply { duration = 350 }
                    .start()
            }
        }
    }

    private fun clearForm() {
        _binding?.let { b ->
            b.etPostTitle.text?.clear()
            b.etPostDescription.text?.clear()
            b.cardAiResult.visibility = View.GONE
            b.chipGroupTags.removeAllViews()
        }
        viewModel.aiResult.value = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
