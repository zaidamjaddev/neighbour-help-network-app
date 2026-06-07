package com.example.neighbour_help_network.ui.main.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.neighbour_help_network.data.model.ChatMessage
import com.example.neighbour_help_network.databinding.ItemChatMessageBinding
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * ChatMessageAdapter — DiffUtil ListAdapter for real-time chat messages.
 * Now displays Gemini AI translations when enabled.
 */
class ChatMessageAdapter : ListAdapter<ChatMessage, ChatMessageAdapter.ViewHolder>(DIFF_CALLBACK) {

    private val currentUserId: String? = FirebaseAuth.getInstance().currentUser?.uid
    private val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

    var isTranslateEnabled: Boolean = false
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    inner class ViewHolder(private val binding: ItemChatMessageBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(message: ChatMessage) {
            val isSentByMe = message.senderId == currentUserId
            val timeStr = message.timestamp?.let { timeFormat.format(it) } ?: ""

            // Use pre-calculated Gemini translation if available and enabled
            val displayText = if (isTranslateEnabled && !message.translatedText.isNullOrBlank()) {
                message.translatedText
            } else {
                message.text
            }

            val wasTranslated = isTranslateEnabled && !message.translatedText.isNullOrBlank()

            if (isSentByMe) {
                binding.layoutSent.visibility = View.VISIBLE
                binding.layoutReceived.visibility = View.GONE

                binding.tvSentText.text = displayText
                binding.tvSentName.text = "You"
                binding.tvSentTime.text = timeStr
                binding.tvSentTranslated.visibility = if (wasTranslated) View.VISIBLE else View.GONE

            } else {
                binding.layoutSent.visibility = View.GONE
                binding.layoutReceived.visibility = View.VISIBLE

                binding.tvReceivedText.text = displayText
                binding.tvReceivedName.text = message.senderName.ifBlank { "Neighbour" }
                binding.tvReceivedTime.text = timeStr
                binding.tvReceivedTranslated.visibility = if (wasTranslated) View.VISIBLE else View.GONE
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemChatMessageBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<ChatMessage>() {
            override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage) =
                oldItem.messageId == newItem.messageId

            override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage) =
                oldItem == newItem
        }
    }
}
