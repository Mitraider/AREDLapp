package com.example.aredlapp.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.example.aredlapp.R
import com.example.aredlapp.databinding.ItemPlayerHeaderBinding
import com.example.aredlapp.models.LeaderboardResponse
import com.example.aredlapp.utils.CountryUtils
import com.example.aredlapp.utils.ThemeUtils

class PlayerHeaderAdapter : RecyclerView.Adapter<PlayerHeaderAdapter.HeaderViewHolder>() {

    private var player: LeaderboardResponse? = null

    fun setPlayer(newPlayer: LeaderboardResponse?) {
        player = newPlayer
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HeaderViewHolder {
        val binding = ItemPlayerHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HeaderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HeaderViewHolder, position: Int) {
        player?.let { holder.bind(it) }
    }

    override fun getItemCount(): Int = if (player != null) 1 else 0

    inner class HeaderViewHolder(private val binding: ItemPlayerHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(p: LeaderboardResponse) {
            binding.detailPlayerName.text = p.user?.global_name ?: p.user?.username ?: "Unknown"
            binding.detailPlayerCountry.text = CountryUtils.getCountryName(p.country)
            binding.detailPlayerPoints.text = "Points: ${String.format("%.2f", p.total_points ?: 0.0)}"
            binding.detailPlayerClan.text = p.clan?.name ?: p.clan?.global_name ?: ""
            binding.detailPlayerExtreme.text = "Extremes: ${p.extremes ?: 0}"
            binding.detailPlayerHardest.text = "Hardest: ${p.hardest?.name ?: "-"}"

            val discordId = p.user?.discord_id
            val avatarHash = p.user?.discord_avatar
            
            val avatarUrl = if (discordId != null && avatarHash != null) {
                "https://cdn.discordapp.com/avatars/$discordId/$avatarHash.webp?size=256"
            } else if (p.user?.avatar != null) {
                p.user.avatar
            } else null

            binding.detailPlayerAvatar.load(avatarUrl) {
                crossfade(true)
                placeholder(R.drawable.aredl_logo)
                error(R.drawable.aredl_logo)
                transformations(CircleCropTransformation())
            }
            
            val color = ThemeUtils.getSecondaryColor(binding.root.context)
            binding.detailPlayerName.setTextColor(color)
            binding.detailPlayerClan.setTextColor(color)
            binding.detailPlayerHardest.setTextColor(color)
            binding.labelRecords.setTextColor(color)
        }
    }
}
