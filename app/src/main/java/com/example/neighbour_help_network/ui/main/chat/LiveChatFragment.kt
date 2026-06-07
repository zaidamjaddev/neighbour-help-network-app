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
import androidx.navigation.fragment.findNavController
import com.example.neighbour_help_network.R
import com.example.neighbour_help_network.ui.main.MainActivity
import com.google.android.material.chip.Chip

/**
 * LiveChatFragment — Real-time community chat screen (Global Chat).
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

        val chatId = "global_chat"
        
        setupToolbar()
        setupRecyclerView()
        setupSendButton()
        setupTranslateButton()
        setupQuickReplies()
        observeViewModel()

        viewModel.startListening(chatId)
    }

    private fun setupToolbar() {
        val mainActivity = activity as? MainActivity ?: return
        binding.toolbarChat.title = "Community Chat"
        binding.toolbarChat.setNavigationIcon(R.drawable.ic_home)
        binding.toolbarChat.setNavigationOnClickListener {
            mainActivity.openDrawer()
        }
    }

    private fun setupQuickReplies() {
        val chips = listOf(
            binding.chipOnMyWay,
            binding.chipBeThereSoon,
            binding.chipImHere,
            binding.chipThankYou,
            binding.chipOkay
        )
        
        chips.forEach { chip ->
            chip.setOnClickListener {
                val text = (it as Chip).text.toString()
                viewModel.sendMessage(text)
            }
        }
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
            _binding?.let { b ->
                adapter.submitList(messages) {
                    if (messages.isNotEmpty()) {
                        b.rvMessages.scrollToPosition(messages.size - 1)
                    }
                }
            }
        }

        viewModel.translateEnabled.observe(viewLifecycleOwner) { enabled ->
            adapter.isTranslateEnabled = enabled
            _binding?.btnTranslate?.text = if (enabled) "Urdu ON" else "Urdu OFF"
        }

        viewModel.sendResult.observe(viewLifecycleOwner) { result ->
            result ?: return@observe
            if (result.isFailure) {
                context?.let { ctx ->
                    Toast.makeText(ctx, "Failed to send: ${result.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
