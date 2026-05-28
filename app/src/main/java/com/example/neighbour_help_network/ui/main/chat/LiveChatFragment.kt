package com.example.neighbour_help_network.ui.main.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.neighbour_help_network.databinding.FragmentLiveChatBinding

/**
 * LiveChatFragment — Real-time community chat screen.
 *
 * Features:
 * - Firestore-backed real-time message stream (via LiveChatViewModel)
 * - Sent/Received bubble layout differentiation
 * - "Translate (Urdu)" toggle button that passes all visible messages through
 *   LocalAiEngine.simulateTranslation() via the ChatMessageAdapter
 *
 * Defaults to the "global_chat" room. The chatId can be parameterized
 * in a future version to support per-request chat threads.
 */
class LiveChatFragment : Fragment() {

    private var _binding: FragmentLiveChatBinding? = null
    private val binding get() = _binding!!
    private val viewModel: LiveChatViewModel by viewModels()

    private lateinit var adapter: ChatMessageAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLiveChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupSendButton()
        setupTranslateButton()
        observeViewModel()

        viewModel.startListening("global_chat")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Setup
    // ─────────────────────────────────────────────────────────────────────────

    private fun setupRecyclerView() {
        adapter = ChatMessageAdapter()
        val layoutManager = LinearLayoutManager(requireContext()).apply {
            stackFromEnd = true   // newest message at bottom
        }
        binding.rvMessages.layoutManager = layoutManager
        binding.rvMessages.adapter = adapter
    }

    private fun setupSendButton() {
        binding.fabSendMessage.setOnClickListener {
            val text = binding.etMessage.text.toString().trim()
            if (text.isBlank()) return@setOnClickListener
            viewModel.sendMessage(text)
            binding.etMessage.text?.clear()
        }
    }

    private fun setupTranslateButton() {
        binding.btnTranslate.setOnClickListener {
            viewModel.toggleTranslate()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Observe ViewModel
    // ─────────────────────────────────────────────────────────────────────────

    private fun observeViewModel() {
        viewModel.messages.observe(viewLifecycleOwner) { messages ->
            adapter.submitList(messages) {
                // Scroll to latest message after list update
                if (messages.isNotEmpty()) {
                    binding.rvMessages.scrollToPosition(messages.size - 1)
                }
            }
        }

        viewModel.translateEnabled.observe(viewLifecycleOwner) { enabled ->
            adapter.isTranslateEnabled = enabled
            binding.btnTranslate.text = if (enabled) {
                "Urdu ON"
            } else {
                "Urdu OFF"
            }
        }

        viewModel.sendResult.observe(viewLifecycleOwner) { result ->
            result ?: return@observe
            if (result.isFailure) {
                Toast.makeText(
                    requireContext(),
                    "Failed to send: ${result.exceptionOrNull()?.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}
