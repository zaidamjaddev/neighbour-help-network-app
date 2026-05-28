package com.example.neighbour_help_network.ui.main.feed

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.neighbour_help_network.R
import com.example.neighbour_help_network.data.model.HelpRequest
import com.example.neighbour_help_network.databinding.ItemHelpRequestBinding
import com.google.firebase.auth.FirebaseAuth

/**
 * HelpRequestAdapter — Binds HelpRequest data to the feed list.
 */
class HelpRequestAdapter(
    private val onAcceptClicked: (HelpRequest) -> Unit,
    private val onEditClicked: (HelpRequest) -> Unit,
    private val onDeleteClicked: (HelpRequest) -> Unit
) : ListAdapter<HelpRequest, HelpRequestAdapter.ViewHolder>(DIFF_CALLBACK) {

    private val currentUserId: String? = FirebaseAuth.getInstance().currentUser?.uid

    inner class ViewHolder(private val binding: ItemHelpRequestBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(request: HelpRequest) {
            binding.apply {
                tvRequestTitle.text = request.title
                tvRequestDescription.text = request.description
                tvCategory.text = request.category
                
                // Show the poster's name clearly
                val posterName = request.userDisplayName.ifBlank { "Someone" }
                tvPostedBy.text = "Posted by $posterName"

                // Distance placeholder
                tvDistance.text = "Nearby"

                // Urgency level color coding
                val context = itemView.context
                val urgencyColor = when {
                    request.urgencyScore >= 75 -> context.getColor(R.color.colorEmergency)
                    request.urgencyScore >= 45 -> context.getColor(R.color.colorPrimary)
                    else -> context.getColor(R.color.colorAccent)
                }
                tvUrgencyLevel.text = request.urgencyLevel
                tvUrgencyLevel.setTextColor(urgencyColor)

                // Accept button state and text visibility fix
                val isAcceptedByMe = request.acceptedBy == currentUserId
                val isAccepted = request.status == "accepted"
                val isMyPost = request.userId == currentUserId

                // Show/Hide Edit & Delete for owner ONLY if not accepted
                if (isMyPost && !isAccepted) {
                    btnEditRequest.visibility = View.VISIBLE
                    btnDeleteRequest.visibility = View.VISIBLE
                    btnAcceptHelp.visibility = View.GONE
                    
                    btnEditRequest.setOnClickListener { onEditClicked(request) }
                    btnDeleteRequest.setOnClickListener { onDeleteClicked(request) }
                } else {
                    btnEditRequest.visibility = View.GONE
                    btnDeleteRequest.visibility = View.GONE
                    btnAcceptHelp.visibility = View.VISIBLE
                }

                btnAcceptHelp.isEnabled = !isAccepted
                btnAcceptHelp.alpha = if (isAccepted) 0.6f else 1.0f

                when {
                    isAcceptedByMe -> {
                        btnAcceptHelp.text = "✓ You Accepted"
                    }
                    isAccepted -> {
                        btnAcceptHelp.text = "Helping..."
                    }
                    else -> {
                        btnAcceptHelp.text = context.getString(R.string.btn_accept_help)
                        btnAcceptHelp.setOnClickListener { onAcceptClicked(request) }
                    }
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHelpRequestBinding.inflate(
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
