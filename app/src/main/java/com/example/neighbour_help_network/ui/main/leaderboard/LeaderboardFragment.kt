package com.example.neighbour_help_network.ui.main.leaderboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.neighbour_help_network.databinding.FragmentLeaderboardBinding
import com.example.neighbour_help_network.ui.main.MainActivity

/**
 * LeaderboardFragment — Displays the top neighbours ranked by their help points.
 */
class LeaderboardFragment : Fragment() {

    private var _binding: FragmentLeaderboardBinding? = null
    private val binding get() = _binding!!
    private val viewModel: LeaderboardViewModel by viewModels()
    private lateinit var adapter: LeaderboardAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLeaderboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        setupRecyclerView()
        observeViewModel()
    }

    private fun setupToolbar() {
        val mainActivity = activity as? MainActivity ?: return
        binding.toolbarLeaderboard.title = "Top Neighbours"
        binding.toolbarLeaderboard.setNavigationIcon(com.example.neighbour_help_network.R.drawable.ic_home)
        binding.toolbarLeaderboard.setNavigationOnClickListener {
            mainActivity.openDrawer()
        }
    }

    private fun setupRecyclerView() {
        adapter = LeaderboardAdapter()
        binding.rvLeaderboard.layoutManager = LinearLayoutManager(requireContext())
        binding.rvLeaderboard.adapter = adapter
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressLeaderboard.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.topUsers.observe(viewLifecycleOwner) { users ->
            adapter.submitList(users)
            binding.layoutEmptyLeaderboard.visibility = if (users.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
