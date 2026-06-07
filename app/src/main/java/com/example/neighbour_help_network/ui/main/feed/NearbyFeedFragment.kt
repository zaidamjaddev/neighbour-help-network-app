package com.example.neighbour_help_network.ui.main.feed

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.neighbour_help_network.R
import com.example.neighbour_help_network.data.model.HelpRequest
import com.example.neighbour_help_network.databinding.FragmentNearbyFeedBinding
import com.example.neighbour_help_network.ui.main.MainActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.google.android.material.snackbar.Snackbar

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
        setupToolbar()
        setupRecyclerView()
        setupTabs()
        observeViewModel()
    }

    private fun setupToolbar() {
        val mainActivity = activity as? MainActivity ?: return
        binding.toolbarFeed.title = "Nearby Feed"
        // Use a standard hamburger icon if available, otherwise ic_home
        binding.toolbarFeed.setNavigationIcon(R.drawable.ic_home) 
        binding.toolbarFeed.setNavigationOnClickListener {
            mainActivity.openDrawer()
        }
    }

    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> viewModel.showActiveRequests()
                    1 -> viewModel.showHistoryRequests()
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupRecyclerView() {
        adapter = HelpRequestAdapter(
            onAcceptClicked = { request -> 
                viewModel.acceptRequest(request.id)
                _binding?.root?.let { 
                    Snackbar.make(it, "Accepted. Open it from My Ongoing Help to start chat.", Snackbar.LENGTH_SHORT).show()
                }
            },
            onCompleteClicked = { request -> showCompleteConfirmation(request) },
            onChatClicked = { request -> openChat(request) },
            onEditClicked = { request -> showUpdateDialog(request) },
            onDeleteClicked = { request -> showDeleteConfirmation(request) }
        )
        binding.rvRequests.layoutManager = LinearLayoutManager(requireContext())
        binding.rvRequests.adapter = adapter
    }

    private fun openChat(request: HelpRequest) {
        if (!isAdded) return
        val navController = findNavController()
        if (navController.currentDestination?.id == R.id.nearbyFeedFragment) {
            val bundle = Bundle().apply {
                putString("chatId", request.id)
                putString("chatTitle", "Chat: ${request.title}")
            }
            navController.navigate(R.id.requestChatFragment, bundle)
        }
    }

    private fun showCompleteConfirmation(request: HelpRequest) {
        context?.let { ctx ->
            MaterialAlertDialogBuilder(ctx)
                .setTitle("Complete Request")
                .setMessage("Mark this request as completed? It will be moved to history.")
                .setPositiveButton("Complete") { _, _ ->
                    viewModel.completeRequest(request.id)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun showUpdateDialog(request: HelpRequest) {
        val ctx = context ?: return
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
        }
        val etTitle = EditText(ctx).apply { hint = "Title"; setText(request.title) }
        val etDesc = EditText(ctx).apply { hint = "Description"; setText(request.description); minLines = 3 }
        layout.addView(etTitle)
        layout.addView(etDesc)

        MaterialAlertDialogBuilder(ctx)
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
        context?.let { ctx ->
            MaterialAlertDialogBuilder(ctx)
                .setTitle("Delete Request")
                .setMessage("Are you sure you want to delete this help request?")
                .setPositiveButton("Delete") { _, _ -> viewModel.deleteRequest(request.id) }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            updateUiState(loading, viewModel.requests.value ?: emptyList())
        }
        viewModel.requests.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            updateUiState(viewModel.isLoading.value ?: false, list)
        }
        viewModel.actionResult.observe(viewLifecycleOwner) { result ->
            result ?: return@observe
            if (result.isFailure) {
                context?.let { ctx ->
                    Toast.makeText(ctx, "Error: ${result.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                }
            }
            viewModel.resetActionResult()
        }
    }

    private fun updateUiState(loading: Boolean, list: List<HelpRequest>) {
        _binding?.let { b ->
            if (loading) {
                b.progressFeed.visibility = View.VISIBLE
                b.layoutEmptyState.visibility = View.GONE
                b.rvRequests.visibility = View.GONE
            } else {
                b.progressFeed.visibility = View.GONE
                if (list.isEmpty()) {
                    b.layoutEmptyState.visibility = View.VISIBLE
                    b.rvRequests.visibility = View.GONE
                    val activeTab = b.tabLayout.selectedTabPosition
                    b.tvEmptyMessage.text = if (activeTab == 0) "No active requests nearby." else "No completed requests found."
                } else {
                    b.layoutEmptyState.visibility = View.GONE
                    b.rvRequests.visibility = View.VISIBLE
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
