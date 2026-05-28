package com.example.neighbour_help_network.ui.main.feed

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.neighbour_help_network.R
import com.example.neighbour_help_network.data.model.HelpRequest
import com.example.neighbour_help_network.databinding.FragmentNearbyFeedBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * NearbyFeedFragment — Real-time scrolling feed of nearby help requests.
 */
class NearbyFeedFragment : Fragment() {

    private var _binding: FragmentNearbyFeedBinding? = null
    private val binding get() = _binding!!
    private val viewModel: NearbyFeedViewModel by viewModels()

    private lateinit var adapter: HelpRequestAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNearbyFeedBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        adapter = HelpRequestAdapter(
            onAcceptClicked = { request -> viewModel.acceptRequest(request.id) },
            onEditClicked = { request -> showUpdateDialog(request) },
            onDeleteClicked = { request -> showDeleteConfirmation(request) }
        )
        binding.rvRequests.layoutManager = LinearLayoutManager(requireContext())
        binding.rvRequests.adapter = adapter
    }

    private fun showUpdateDialog(request: HelpRequest) {
        val context = requireContext()
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
        }

        val etTitle = EditText(context).apply {
            hint = "Title"
            setText(request.title)
        }
        val etDesc = EditText(context).apply {
            hint = "Description"
            setText(request.description)
            minLines = 3
        }

        layout.addView(etTitle)
        layout.addView(etDesc)

        MaterialAlertDialogBuilder(context)
            .setTitle("Update Help Request")
            .setView(layout)
            .setPositiveButton("Update") { _, _ ->
                val newTitle = etTitle.text.toString().trim()
                val newDesc = etDesc.text.toString().trim()
                if (newTitle.isNotEmpty() && newDesc.isNotEmpty()) {
                    viewModel.updateRequest(request.id, newTitle, newDesc)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteConfirmation(request: HelpRequest) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Request")
            .setMessage("Are you sure you want to delete this help request? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteRequest(request.id)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressFeed.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.requests.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            binding.layoutEmptyState.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.actionResult.observe(viewLifecycleOwner) { result ->
            result ?: return@observe
            if (result.isSuccess) {
                Toast.makeText(requireContext(), "Action successful", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Error: ${result.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
            }
            viewModel.resetActionResult()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
