package com.example.aredlapp.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.aredlapp.databinding.ItemLevelBinding
import com.example.aredlapp.models.RecordInfo
import coil.load

class RecordsAdapter(private val onItemClick: (RecordInfo) -> Unit) :
    ListAdapter<RecordInfo, RecordsAdapter.RecordViewHolder>(RecordDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = 
        RecordViewHolder(ItemLevelBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: RecordViewHolder, position: Int) = holder.bind(getItem(position))

    inner class RecordViewHolder(private val binding: ItemLevelBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(record: RecordInfo) {
            val level = record.level
            binding.levelRank.text = "#${level?.position ?: record.position ?: "?"}"
            binding.levelName.text = level?.name ?: record.name ?: "Unknown"
            
            // Logique de créateur unifiée : on évite le placeholder AREDL
            val creator = level?.global_name ?: level?.creator?.global_name ?: level?.creator?.username ?: level?.publisher?.global_name
            if (creator != null && creator != "AREDL" && creator.isNotBlank()) {
                binding.levelCreator.text = "by $creator"
                binding.levelCreator.visibility = View.VISIBLE
            } else {
                binding.levelCreator.visibility = View.GONE
            }
            
            val points = record.points ?: record.list_points ?: level?.points ?: 0.0
            binding.levelPoints.text = String.format("%.1f points", points)
            
            val levelId = record.level_id ?: level?.level_id
            binding.levelThumbnail.load("https://raw.githubusercontent.com/All-Rated-Extreme-Demon-List/Thumbnails/main/levels/cards/${levelId}.webp") {
                crossfade(true)
            }

            binding.btnFavorite.visibility = View.GONE
            binding.btnTodo.visibility = View.GONE
            binding.btnCompleted.visibility = View.GONE
            
            binding.root.setOnClickListener { onItemClick(record) }
        }
    }

    class RecordDiffCallback : DiffUtil.ItemCallback<RecordInfo>() {
        override fun areItemsTheSame(old: RecordInfo, new: RecordInfo) = (old.id ?: old.level_id) == (new.id ?: new.level_id)
        override fun areContentsTheSame(old: RecordInfo, new: RecordInfo) = 
            old.points == new.points && 
            old.level?.global_name == new.level?.global_name &&
            old.level?.name == new.level?.name
    }
}
