package com.example.aredlapp.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.request.CachePolicy
import com.example.aredlapp.databinding.ItemSubmissionBinding
import com.example.aredlapp.models.LevelResponse
import com.example.aredlapp.models.UserSubmissionInfo
import com.example.aredlapp.utils.ThemeUtils
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

class SubmissionLevelsAdapter(
    private val onFavoriteClick: (String) -> Unit,
    private val onTodoClick: (String) -> Unit,
    private val onItemClick: (LevelResponse, UserSubmissionInfo?) -> Unit
) : ListAdapter<LevelResponse, SubmissionLevelsAdapter.SubmissionViewHolder>(LevelDiffCallback()) {

    private var favoriteIds: Set<String> = emptySet()
    private var todoIds: Set<String> = emptySet()
    private var submissionInfoByLevel: Map<String, UserSubmissionInfo> = emptyMap()

    fun updateStates(
        favorites: Set<String>,
        todos: Set<String>,
        submissions: Map<String, UserSubmissionInfo>
    ) {
        favoriteIds = favorites
        todoIds = todos
        submissionInfoByLevel = submissions
        notifyItemRangeChanged(0, itemCount, "state_update")
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SubmissionViewHolder {
        return SubmissionViewHolder(ItemSubmissionBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: SubmissionViewHolder, position: Int) = holder.bind(getItem(position))

    override fun onBindViewHolder(holder: SubmissionViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) super.onBindViewHolder(holder, position, payloads) else holder.bind(getItem(position))
    }

    inner class SubmissionViewHolder(private val binding: ItemSubmissionBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(level: LevelResponse) {
            val context = binding.root.context
            val secondaryColor = ThemeUtils.getSecondaryColor(context)

            binding.submissionRank.text = "#${level.position}"
            binding.submissionName.text = level.name

            val creatorName = level.global_name
            if (creatorName != null && creatorName != "AREDL" && creatorName.isNotBlank()) {
                binding.submissionCreator.text = "by $creatorName"
            } else {
                val fallback = level.creator?.global_name ?: level.creator?.username ?: level.publisher?.global_name ?: level.publisher?.username
                binding.submissionCreator.text = if (!fallback.isNullOrBlank() && fallback != "AREDL") "by $fallback" else ""
            }

            binding.submissionPoints.text = String.format("%.1f points", level.points)
            binding.submissionThumbnail.load("https://raw.githubusercontent.com/All-Rated-Extreme-Demon-List/Thumbnails/main/levels/cards/${level.level_id}.webp") {
                crossfade(true)
                diskCachePolicy(CachePolicy.ENABLED)
                size(400, 200)
            }

            val submissionInfo = submissionInfoByLevel[level.id] ?: level.level_id?.toString()?.let { submissionInfoByLevel[it] }
            binding.submissionUpdatedAt.text = formatUpdatedAt(submissionInfo?.updatedAt)
            if (submissionInfo != null) {
                binding.submissionStatus.text = submissionInfo.displayStatus
                binding.submissionStatus.background = GradientDrawable().apply {
                    cornerRadius = 10f
                    setColor(
                        when (submissionInfo.displayStatus) {
                            "Accepted" -> Color.parseColor("#1B8F3A")
                            "Refused" -> Color.parseColor("#C62828")
                            else -> Color.parseColor("#1565C0")
                        }
                    )
                }
            } else {
                binding.submissionStatus.text = "Pending"
                binding.submissionStatus.background = GradientDrawable().apply {
                    cornerRadius = 10f
                    setColor(Color.parseColor("#1565C0"))
                }
            }

            val isFav = favoriteIds.contains(level.id)
            val isTodo = todoIds.contains(level.id)
            setupButton(binding.btnFavorite, if (isFav) android.R.drawable.btn_star_big_on else android.R.drawable.btn_star_big_off, isFav, secondaryColor)
            setupButton(binding.btnTodo, android.R.drawable.ic_menu_save, isTodo, secondaryColor)

            binding.btnFavorite.setOnClickListener { onFavoriteClick(level.id) }
            binding.btnTodo.setOnClickListener { onTodoClick(level.id) }
            binding.root.setOnClickListener { onItemClick(level, submissionInfo) }
        }

        private fun setupButton(view: android.widget.ImageButton, res: Int, active: Boolean, color: Int) {
            view.setImageResource(res)
            view.imageTintList = ColorStateList.valueOf(if (active) color else Color.WHITE)
        }

        @RequiresApi(Build.VERSION_CODES.O)
        private fun formatUpdatedAt(updatedAt: String?): String {
            if (updatedAt.isNullOrBlank()) return "Updated recently"
            return try {
                val parsed = OffsetDateTime.parse(updatedAt)
                val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault())
                "Last updated : ${parsed.format(formatter)}"
            } catch (_: Exception) {
                "Last updated : ${updatedAt.substringBefore('T')}"
            }
        }
    }

    class LevelDiffCallback : DiffUtil.ItemCallback<LevelResponse>() {
        override fun areItemsTheSame(old: LevelResponse, new: LevelResponse) = old.id == new.id
        override fun areContentsTheSame(old: LevelResponse, new: LevelResponse) =
            old.global_name == new.global_name && old.name == new.name && old.points == new.points
    }
}
