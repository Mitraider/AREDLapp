package com.example.aredlapp.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.aredlapp.R
import com.example.aredlapp.databinding.ItemLevelHeaderBinding
import com.example.aredlapp.models.LevelResponse
import com.example.aredlapp.utils.ThemeUtils
import com.example.aredlapp.utils.YouTubeUtils
import com.google.android.material.chip.Chip
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener

class LevelHeaderAdapter(
    private val onSearchChanged: (String) -> Unit
) : RecyclerView.Adapter<LevelHeaderAdapter.HeaderViewHolder>() {

    private var level: LevelResponse? = null
    private var youtubePlayer: YouTubePlayer? = null

    fun setLevel(newLevel: LevelResponse?) {
        val hadLevel = level != null
        level = newLevel
        val hasLevel = level != null
        
        if (!hadLevel && hasLevel) {
            notifyItemInserted(0)
        } else if (hadLevel && !hasLevel) {
            notifyItemRemoved(0)
        } else if (hadLevel && hasLevel) {
            notifyItemChanged(0)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        HeaderViewHolder(ItemLevelHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: HeaderViewHolder, position: Int) {
        level?.let { holder.bind(it) }
    }

    override fun getItemCount(): Int = if (level != null) 1 else 0

    inner class HeaderViewHolder(private val binding: ItemLevelHeaderBinding) : RecyclerView.ViewHolder(binding.root) {
        
        private var textWatcher: TextWatcher? = null

        fun bind(l: LevelResponse) {
            val context = binding.root.context
            val color = ThemeUtils.getSecondaryColor(context)
            val colorStateList = ColorStateList.valueOf(color)

            binding.detailLevelName.text = l.name
            binding.detailLevelName.setTextColor(color)
            
            // an ugly way to get a level's creator
            val creator = l.global_name ?: "AREDL"
            binding.detailLevelCreator.text = "by $creator"
            
            binding.detailLevelRank.text = "Rank #${l.position}"
            binding.detailLevelPoints.text = "Points: ${String.format("%.1f", l.points)}"
            binding.detailLevelId.text = "Level ID: ${l.level_id ?: l.id}"
            binding.detailLevelSong.text = "Song ID: ${l.song ?: l.song_id ?: "-"}"
            
            binding.detailLevelTiers.text = "Edel: ${l.edel_enjoyment ?: "-"} | GDDL: ${l.gddl_tier ?: "-"} | NLW: ${l.nlw_tier ?: "-"}"
            binding.detailLevelTiers.setTextColor(color)
            
            binding.detailLevelDescription.text = l.description?.takeIf { it.isNotBlank() } ?: "No description available."
            
            listOf(binding.labelTags, binding.labelDescription, binding.labelVictors).forEach { it.setTextColor(color) }

            YouTubeUtils.extractVideoId(l.video)?.let { videoId ->
                binding.cardVideo.visibility = View.VISIBLE
                if (youtubePlayer == null) {
                    binding.youtubePlayerView.initialize(object : AbstractYouTubePlayerListener() {
                        override fun onReady(player: YouTubePlayer) {
                            youtubePlayer = player
                            player.cueVideo(videoId, 0f)
                        }
                    })
                } else {
                    youtubePlayer?.cueVideo(videoId, 0f)
                }
            } ?: run { binding.cardVideo.visibility = View.GONE }

            binding.tagsChipGroup.removeAllViews()
            l.tags?.forEach { tagName ->
                val chip = Chip(context).apply {
                    text = tagName
                    setTextColor(color)
                    chipStrokeColor = colorStateList
                    chipStrokeWidth = 2f
                    chipBackgroundColor = ColorStateList.valueOf(context.resources.getColor(R.color.aredl_dark_grey, null))
                }
                binding.tagsChipGroup.addView(chip)
            }

            binding.inputLayoutSearchVictors.setBoxStrokeColor(color)
            binding.inputLayoutSearchVictors.setHintTextColor(colorStateList)
            
            binding.searchVictors.removeTextChangedListener(textWatcher)
            textWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    onSearchChanged(s.toString())
                }
                override fun afterTextChanged(s: Editable?) {}
            }
            binding.searchVictors.addTextChangedListener(textWatcher)
        }
    }
}
