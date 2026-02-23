package com.example.aredlapp.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.request.CachePolicy
import com.example.aredlapp.databinding.ItemLevelBinding
import com.example.aredlapp.models.LevelResponse
import com.example.aredlapp.utils.ThemeUtils

class LevelsAdapter(
    private val onFavoriteClick: (String) -> Unit,
    private val onTodoClick: (String) -> Unit,
    private val onCompletedClick: (String) -> Unit,
    private val onItemClick: (LevelResponse) -> Unit
) : ListAdapter<LevelResponse, LevelsAdapter.LevelViewHolder>(LevelDiffCallback()) {

    private var favoriteIds: Set<String> = emptySet()
    private var todoIds: Set<String> = emptySet()
    private var completedIds: Set<String> = emptySet()

    fun updateStates(favorites: Set<String>, todos: Set<String>, completed: Set<String>) {
        favoriteIds = favorites
        todoIds = todos
        completedIds = completed
        notifyItemRangeChanged(0, itemCount, "state_update")
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = 
        LevelViewHolder(ItemLevelBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: LevelViewHolder, position: Int) = holder.bind(getItem(position))

    override fun onBindViewHolder(holder: LevelViewHolder, position: Int, payloads: List<Any>) {
        if (payloads.isEmpty()) super.onBindViewHolder(holder, position, payloads)
        else holder.bind(getItem(position))
    }

    inner class LevelViewHolder(private val binding: ItemLevelBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(level: LevelResponse) {
            val context = binding.root.context
            val secondaryColor = ThemeUtils.getSecondaryColor(context)
            
            binding.levelRank.text = "#${level.position}"
            binding.levelName.text = level.name
            
            // On affiche le créateur seulement s'il est valide et différent de AREDL
            val creatorName = level.global_name
            if (creatorName != null && creatorName != "AREDL" && creatorName.isNotBlank()) {
                binding.levelCreator.text = "by $creatorName"
                binding.levelCreator.visibility = View.VISIBLE
            } else {
                // Si on a rien d'autre, on tente de fouiller dans les objets imbriqués au cas où
                val fallback = level.creator?.global_name ?: level.creator?.username ?: level.publisher?.global_name ?: level.publisher?.username
                if (fallback != null && fallback != "AREDL") {
                    binding.levelCreator.text = "by $fallback"
                    binding.levelCreator.visibility = View.VISIBLE
                } else {
                    binding.levelCreator.visibility = View.GONE
                }
            }

            binding.levelPoints.text = String.format("%.1f points", level.points)
            binding.levelThumbnail.load("https://raw.githubusercontent.com/All-Rated-Extreme-Demon-List/Thumbnails/main/levels/cards/${level.level_id}.webp") {
                crossfade(true)
                diskCachePolicy(CachePolicy.ENABLED)
                size(400, 200) 
            }

            val isFav = favoriteIds.contains(level.id)
            val isTodo = todoIds.contains(level.id)
            val isDone = completedIds.contains(level.id)

            setupButton(binding.btnFavorite, if (isFav) android.R.drawable.btn_star_big_on else android.R.drawable.btn_star_big_off, isFav, secondaryColor)
            setupButton(binding.btnTodo, android.R.drawable.ic_menu_save, isTodo, secondaryColor)
            setupButton(binding.btnCompleted, if (isDone) android.R.drawable.checkbox_on_background else android.R.drawable.checkbox_off_background, isDone, secondaryColor)
            
            binding.btnFavorite.setOnClickListener { onFavoriteClick(level.id) }
            binding.btnTodo.setOnClickListener { onTodoClick(level.id) }
            binding.btnCompleted.setOnClickListener { onCompletedClick(level.id) }
            binding.root.setOnClickListener { onItemClick(level) }
        }

        private fun setupButton(view: android.widget.ImageButton, res: Int, active: Boolean, color: Int) {
            view.setImageResource(res)
            view.imageTintList = ColorStateList.valueOf(if (active) color else Color.WHITE)
        }
    }

    class LevelDiffCallback : DiffUtil.ItemCallback<LevelResponse>() {
        override fun areItemsTheSame(old: LevelResponse, new: LevelResponse) = old.id == new.id
        override fun areContentsTheSame(old: LevelResponse, new: LevelResponse) = 
            old.global_name == new.global_name && old.name == new.name && old.points == new.points
    }
}
