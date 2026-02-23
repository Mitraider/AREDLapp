package com.example.aredlapp.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.example.aredlapp.R
import com.example.aredlapp.databinding.ItemPlayerBinding
import com.example.aredlapp.models.LeaderboardResponse
import com.example.aredlapp.utils.CountryUtils
import com.example.aredlapp.utils.ThemeUtils

class LeaderboardAdapter(private val onItemClick: (LeaderboardResponse) -> Unit) :
    ListAdapter<LeaderboardResponse, LeaderboardAdapter.PlayerViewHolder>(PlayerDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = 
        PlayerViewHolder(ItemPlayerBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: PlayerViewHolder, position: Int) = holder.bind(getItem(position))

    inner class PlayerViewHolder(private val binding: ItemPlayerBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(player: LeaderboardResponse) {
            val context = binding.root.context
            val color = ThemeUtils.getSecondaryColor(context)
            
            binding.playerRank.text = "#${player.rank ?: 0}"
            binding.playerRank.setTextColor(color)
            binding.playerName.text = player.user?.global_name ?: player.user?.username ?: "Unknown"
            binding.playerPoints.text = String.format("%.2f pts", player.total_points ?: 0.0)
            binding.playerPoints.setTextColor(color)
            binding.playerFlag.text = CountryUtils.getCountryName(player.country)

            val user = player.user
            // Utilisation du nouveau système : Discord ID + Avatar Hash -> .webp size 256
            val avatarUrl = if (user?.discord_id != null && user.discord_avatar != null) {
                "https://cdn.discordapp.com/avatars/${user.discord_id}/${user.discord_avatar}.webp?size=256"
            } else if (user?.avatar != null) {
                user.avatar // Fallback sur l'avatar AREDL direct si présent
            } else null

            binding.playerAvatar.load(avatarUrl) {
                crossfade(true)
                placeholder(R.drawable.aredl_logo)
                error(R.drawable.aredl_logo)
                transformations(CircleCropTransformation())
                size(128, 128)
            }
            binding.root.setOnClickListener { onItemClick(player) }
        }
    }

    class PlayerDiffCallback : DiffUtil.ItemCallback<LeaderboardResponse>() {
        override fun areItemsTheSame(old: LeaderboardResponse, new: LeaderboardResponse) = old.user?.id == new.user?.id
        override fun areContentsTheSame(old: LeaderboardResponse, new: LeaderboardResponse) = 
            old.user?.discord_avatar == new.user?.discord_avatar && old.total_points == new.total_points
    }
}
