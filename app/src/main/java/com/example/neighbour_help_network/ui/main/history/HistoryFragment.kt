package com.example.neighbour_help_network.ui.main.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.neighbour_help_network.R
import com.example.neighbour_help_network.data.model.HelpRequest
import com.example.neighbour_help_network.databinding.FragmentHistoryBinding
import com.example.neighbour_help_network.ui.main.feed.HelpRequestAdapter

/**
 * HistoryFragment — Shows resolved help requests that the user either posted or helped with.
 */
class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HistoryViewModel by viewModels()

    private lateinit var adapter: HelpRequestAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        // In history, we don't need accept/complete/edit/delete actions typically.
        // Or we might want chat to see old logs.
        adapter = HelpRequestAdapter(
            onAcceptClicked = {},
            onCompleteClicked = {},
            onChatClicked = { request -> openChat(request) },
            onEditClicked = {},
            onDeleteClicked = {}
        )
        binding.rvHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.rvHistory.adapter = adapter
    }

    private fun openChat(request: HelpRequest) {
        val bundle = Bundle().apply {
            putString("chatId", request.id)
            putString("chatTitle", "Chat: ${request.title}")
        }
        findNavController().navigate(R.id.liveChatFragment, bundle)
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressHistory.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.historyRequests.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            binding.layoutEmptyHistory.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
