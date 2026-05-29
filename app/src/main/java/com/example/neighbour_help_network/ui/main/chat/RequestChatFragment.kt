package com.example.neighbour_help_network.ui.main.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.neighbour_help_network.databinding.FragmentRequestChatBinding

/**
 * RequestChatFragment — Isolated chat screen for a specific help request.
 */
class RequestChatFragment : Fragment() {

    private var _binding: FragmentRequestChatBinding? = null
    private val binding get() = _binding!!
    private val viewModel: LiveChatViewModel by viewModels()

    private lateinit var adapter: ChatMessageAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRequestChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val chatId = arguments?.getString("chatId") ?: return
        val chatTitle = arguments?.getString("chatTitle") ?: "Request Chat"

        binding.toolbarChat.title = chatTitle
        binding.toolbarChat.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        setupRecyclerView()
        setupSendButton()
        setupTranslateButton()
        observeViewModel()

        viewModel.startListening(chatId)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupRecyclerView() {
        adapter = ChatMessageAdapter()
        val layoutManager = LinearLayoutManager(requireContext()).apply {
            stackFromEnd = true
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

    private fun observeViewModel() {
        viewModel.messages.observe(viewLifecycleOwner) { messages ->
            adapter.submitList(messages) {
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
