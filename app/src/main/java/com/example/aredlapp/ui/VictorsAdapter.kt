package com.example.aredlapp.ui

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.example.aredlapp.R
import com.example.aredlapp.databinding.ItemVictorBinding
import com.example.aredlapp.models.LevelRecord
import com.example.aredlapp.utils.ThemeUtils

class VictorsAdapter(
    private val onVictorClick: (LevelRecord) -> Unit
) : ListAdapter<LevelRecord, VictorsAdapter.VictorViewHolder>(VictorDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = 
        VictorViewHolder(ItemVictorBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VictorViewHolder, position: Int) = holder.bind(getItem(position))

    inner class VictorViewHolder(private val binding: ItemVictorBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(record: LevelRecord) {
            val user = record.user ?: record.player
            binding.victorName.text = user?.global_name ?: user?.username ?: "Unknown"
            
            // Nouveau système : Discord ID + Avatar Hash -> .webp size 256
            val avatarUrl = if (user?.discord_id != null && user.discord_avatar != null) {
                "https://cdn.discordapp.com/avatars/${user.discord_id}/${user.discord_avatar}.webp?size=256"
            } else if (user?.avatar != null) {
                user.avatar
            } else null

            binding.victorAvatar.load(avatarUrl) {
                crossfade(true)
                placeholder(R.drawable.aredl_logo)
                error(R.drawable.aredl_logo)
                transformations(CircleCropTransformation())
            }

            val color = ThemeUtils.getSecondaryColor(binding.root.context)
            binding.btnVictorVideo.imageTintList = android.content.res.ColorStateList.valueOf(color)

            val videoUrl = record.video_url ?: record.video
            binding.btnVictorVideo.apply {
                visibility = if (videoUrl.isNullOrBlank()) View.GONE else View.VISIBLE
                setOnClickListener {
                    try {
                        it.context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl)))
                    } catch (e: Exception) { }
                }
            }
            binding.root.setOnClickListener { onVictorClick(record) }
        }
    }

    class VictorDiffCallback : DiffUtil.ItemCallback<LevelRecord>() {
        override fun areItemsTheSame(old: LevelRecord, new: LevelRecord): Boolean {
            val oldUser = old.user ?: old.player
            val newUser = new.user ?: new.player
            return (oldUser?.id ?: old.id) == (newUser?.id ?: new.id)
        }
        override fun areContentsTheSame(old: LevelRecord, new: LevelRecord) = 
            old.user?.discord_avatar == new.user?.discord_avatar && old.video_url == new.video_url
    }
}
