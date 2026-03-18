package com.example.aredlapp.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.aredlapp.databinding.ItemPlayerHeaderBinding
import com.example.aredlapp.models.LeaderboardResponse
import com.example.aredlapp.models.RoleResponse

// This adapter is no longer used for the detailed view but kept for safety/other usages
class PlayerHeaderAdapter : RecyclerView.Adapter<PlayerHeaderAdapter.HeaderViewHolder>() {

    private var player: LeaderboardResponse? = null

    fun setPlayer(newPlayer: LeaderboardResponse?, roles: List<RoleResponse>, rank: Int = 0) {
        player = newPlayer
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HeaderViewHolder {
        val binding = ItemPlayerHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HeaderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HeaderViewHolder, position: Int) {}

    override fun getItemCount(): Int = 0

    inner class HeaderViewHolder(binding: ItemPlayerHeaderBinding) : RecyclerView.ViewHolder(binding.root)
}
