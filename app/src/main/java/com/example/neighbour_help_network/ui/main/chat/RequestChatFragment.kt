package com.example.neighbour_help_network.ui.main.chat

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.neighbour_help_network.R
import com.example.neighbour_help_network.databinding.FragmentRequestChatBinding
import com.example.neighbour_help_network.ui.main.MainActivity
import com.google.android.material.chip.Chip

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

        setupToolbar(chatTitle)
        setupRecyclerView()
        setupSendButton()
        setupTranslateButton()
        setupQuickReplies()
        observeViewModel()

        viewModel.fetchPartnerPhoneNumber(chatId)
        viewModel.startListening(chatId)
    }

    private fun setupToolbar(title: String) {
        binding.toolbarChat.title = title
        
        // Manual toolbar setup to prevent ConcurrentModificationException in NavController
        binding.toolbarChat.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        binding.toolbarChat.setNavigationOnClickListener {
            findNavController().popBackStack()
        }

        binding.toolbarChat.inflateMenu(R.menu.chat_menu)
        val callItem = binding.toolbarChat.menu.findItem(R.id.action_call)
        callItem?.isVisible = false

        binding.toolbarChat.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_call -> {
                    val phone = viewModel.partnerPhoneNumber.value
                    if (!phone.isNullOrBlank()) {
                        val intent = Intent(Intent.ACTION_DIAL).apply {
                            data = Uri.parse("tel:$phone")
                        }
                        startActivity(intent)
                    } else {
                        context?.let { Toast.makeText(it, "Phone number not available.", Toast.LENGTH_SHORT).show() }
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun setupQuickReplies() {
        val quickReplyChips = listOf(
            binding.chipOnMyWay,
            binding.chipBeThereSoon,
            binding.chipImHere,
            binding.chipThankYou,
            binding.chipOkay
        )

        quickReplyChips.forEach { chip ->
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
                context?.let { Toast.makeText(it, "Failed to send message", Toast.LENGTH_SHORT).show() }
            }
        }

        viewModel.partnerPhoneNumber.observe(viewLifecycleOwner) { phone ->
            _binding?.toolbarChat?.menu?.findItem(R.id.action_call)?.isVisible = !phone.isNullOrBlank()
        }
    }
}
