package com.example.neighbour_help_network.ui.main.leaderboard

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.neighbour_help_network.R
import com.example.neighbour_help_network.data.model.User
import com.example.neighbour_help_network.databinding.ItemLeaderboardBinding
import java.io.File

class LeaderboardAdapter : ListAdapter<User, LeaderboardAdapter.LeaderboardViewHolder>(UserDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LeaderboardViewHolder {
        val binding = ItemLeaderboardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return LeaderboardViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LeaderboardViewHolder, position: Int) {
        holder.bind(getItem(position), position + 1)
    }

    class LeaderboardViewHolder(private val binding: ItemLeaderboardBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(user: User, rank: Int) {
            binding.tvRank.text = rank.toString()
            binding.tvUserName.text = user.displayName
            binding.tvPoints.text = user.totalPoints.toString()
            binding.tvBadge.text = resolveBadge(user.totalPoints)

            if (user.photoUrl.isNotBlank()) {
                val file = File(user.photoUrl)
                if (file.exists()) {
                    Glide.with(binding.ivUserPhoto)
                        .load(file)
                        .circleCrop()
                        .placeholder(R.drawable.ic_person_placeholder)
                        .into(binding.ivUserPhoto)
                } else {
                    Glide.with(binding.ivUserPhoto)
                        .load(user.photoUrl)
                        .circleCrop()
                        .placeholder(R.drawable.ic_person_placeholder)
                        .into(binding.ivUserPhoto)
                }
            } else {
                binding.ivUserPhoto.setImageResource(R.drawable.ic_person_placeholder)
            }
        }

        private fun resolveBadge(points: Int): String {
            return when {
                points >= 100 -> "Top Neighbour"
                points >= 30 -> "Trusted Helper"
                else -> "New Helper"
            }
        }
    }

    class UserDiffCallback : DiffUtil.ItemCallback<User>() {
        override fun areItemsTheSame(oldItem: User, newItem: User): Boolean = oldItem.uid == newItem.uid
        override fun areContentsTheSame(oldItem: User, newItem: User): Boolean = oldItem == newItem
    }
}
