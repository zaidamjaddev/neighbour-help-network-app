package com.example.neighbour_help_network.ui.main.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.neighbour_help_network.R
import com.example.neighbour_help_network.databinding.FragmentAcceptedRequestsBinding
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
/**
 * AcceptedRequestsFragment — Shows a list of ongoing help chats (WhatsApp-style).
 */
class AcceptedRequestsFragment : Fragment() {

    private var _binding: FragmentAcceptedRequestsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AcceptedRequestsViewModel by viewModels()
    private lateinit var adapter: AcceptedRequestsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAcceptedRequestsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        setupRecyclerView()
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
        binding.toolbarAccepted.setupWithNavController(navController, appBarConfiguration)
    }

    private fun setupRecyclerView() {
        adapter = AcceptedRequestsAdapter { request ->
            val bundle = Bundle().apply {
                putString("chatId", request.id)
                putString("chatTitle", "Chat: ${request.title}")
            }
            findNavController().navigate(R.id.requestChatFragment, bundle)
        }
        binding.rvAcceptedRequests.layoutManager = LinearLayoutManager(requireContext())
        binding.rvAcceptedRequests.adapter = adapter
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressAccepted.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.acceptedRequests.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            binding.layoutEmptyAccepted.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
