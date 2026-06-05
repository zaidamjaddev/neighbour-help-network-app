package com.example.neighbour_help_network.ui.main.chat

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.neighbour_help_network.data.model.HelpRequest
import com.example.neighbour_help_network.databinding.ItemAcceptedChatBinding
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.Locale

class AcceptedRequestsAdapter(
    private val onItemClicked: (HelpRequest) -> Unit
) : ListAdapter<HelpRequest, AcceptedRequestsAdapter.ViewHolder>(DIFF_CALLBACK) {

    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

    inner class ViewHolder(private val binding: ItemAcceptedChatBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(request: HelpRequest) {
            binding.apply {
                // Determine if we are the requester or the helper to show the other person's name
                val otherPerson = if (request.userId == currentUserId) {
                    if (request.acceptedByName.isNotEmpty()) "Helper: ${request.acceptedByName}" else "Being helped..."
                } else {
                    "Request by: ${request.userDisplayName}"
                }
                
                tvChatPartnerName.text = otherPerson
                tvLastMessageSnippet.text = "Topic: ${request.title}"
                
                val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
                tvChatTime.text = request.timestamp?.let { sdf.format(it) } ?: "Just now"

                root.setOnClickListener { onItemClicked(request) }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAcceptedChatBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<HelpRequest>() {
            override fun areItemsTheSame(oldItem: HelpRequest, newItem: HelpRequest) =
                oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: HelpRequest, newItem: HelpRequest) =
                oldItem == newItem
        }
    }
}
