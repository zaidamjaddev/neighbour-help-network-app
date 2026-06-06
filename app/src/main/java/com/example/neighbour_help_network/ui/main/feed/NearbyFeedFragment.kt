package com.example.neighbour_help_network.ui.main.feed

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.neighbour_help_network.R
import com.example.neighbour_help_network.data.model.HelpRequest
import com.example.neighbour_help_network.databinding.FragmentNearbyFeedBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.google.android.material.snackbar.Snackbar

/**
 * NearbyFeedFragment — Real-time scrolling feed of nearby help requests.
 * Displays help requests and handles the transition to chat.
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
        binding.toolbarFeed.setupWithNavController(navController, appBarConfiguration)
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
                val navController = findNavController()
                if (navController.currentDestination?.id == R.id.nearbyFeedFragment) {
                    Snackbar.make(binding.root, "Accepted. Open it from My Ongoing Help to start chat.", Snackbar.LENGTH_SHORT).show()
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
        val bundle = Bundle().apply {
            putString("chatId", request.id)
            putString("chatTitle", "Chat: ${request.title}")
        }
        findNavController().navigate(R.id.requestChatFragment, bundle)
    }

    private fun showCompleteConfirmation(request: HelpRequest) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Complete Request")
            .setMessage("Mark this request as completed? It will be moved to history.")
            .setPositiveButton("Complete") { _, _ ->
                viewModel.completeRequest(request.id)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showUpdateDialog(request: HelpRequest) {
        val context = requireContext()
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
        }
        val etTitle = EditText(context).apply { hint = "Title"; setText(request.title) }
        val etDesc = EditText(context).apply { hint = "Description"; setText(request.description); minLines = 3 }
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
            .setMessage("Are you sure you want to delete this help request?")
            .setPositiveButton("Delete") { _, _ -> viewModel.deleteRequest(request.id) }
            .setNegativeButton("Cancel", null)
            .show()
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
                Toast.makeText(requireContext(), "Error: ${result.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
            }
            viewModel.resetActionResult()
        }
    }

    private fun updateUiState(loading: Boolean, list: List<HelpRequest>) {
        if (loading) {
            binding.progressFeed.visibility = View.VISIBLE
            binding.layoutEmptyState.visibility = View.GONE
            binding.rvRequests.visibility = View.GONE
        } else {
            binding.progressFeed.visibility = View.GONE
            if (list.isEmpty()) {
                binding.layoutEmptyState.visibility = View.VISIBLE
                binding.rvRequests.visibility = View.GONE
                val activeTab = binding.tabLayout.selectedTabPosition
                binding.tvEmptyMessage.text = if (activeTab == 0) getString(R.string.label_no_requests) else "No completed requests found."
            } else {
                binding.layoutEmptyState.visibility = View.GONE
                binding.rvRequests.visibility = View.VISIBLE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
