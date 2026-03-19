package com.example.aredlapp.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.request.CachePolicy
import coil.transform.CircleCropTransformation
import com.example.aredlapp.R
import com.example.aredlapp.databinding.ItemPlayerBinding
import com.example.aredlapp.models.LeaderboardResponse
import com.example.aredlapp.models.RoleResponse
import com.example.aredlapp.models.UserInfo
import com.example.aredlapp.utils.CountryUtils
import com.example.aredlapp.utils.FlagUtils
import com.example.aredlapp.utils.ThemeUtils
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class LeaderboardAdapter(private val onItemClick: (LeaderboardResponse) -> Unit) :
    ListAdapter<LeaderboardResponse, LeaderboardAdapter.PlayerViewHolder>(PlayerDiffCallback()) {

    private var allRoles: List<RoleResponse> = emptyList()

    fun setRoles(roles: List<RoleResponse>) {
        allRoles = roles
        notifyDataSetChanged()
    }

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
            binding.playerPoints.text = String.format("%.1f pts", player.total_points ?: 0.0)
            binding.playerPoints.setTextColor(color)
            FlagUtils.loadFlag(binding.playerFlag, player.country)

            val user = player.user
            val avatarUrl = if (user?.discord_id != null && user.discord_avatar != null) {
                "https://cdn.discordapp.com/avatars/${user.discord_id}/${user.discord_avatar}.webp?size=256"
            } else user?.avatar

            binding.playerAvatar.load(avatarUrl) {
                crossfade(true)
                placeholder(R.drawable.aredl_logo)
                error(R.drawable.aredl_logo)
                transformations(CircleCropTransformation())
                size(128, 128)
                memoryCachePolicy(CachePolicy.ENABLED)
                diskCachePolicy(CachePolicy.ENABLED)
            }

            setupRoles(user)

            binding.root.setOnClickListener { onItemClick(player) }
        }

        private fun setupRoles(user: UserInfo?) {
            binding.playerRolesContainer.removeAllViews()
            if (user == null || allRoles.isEmpty()) return

            val username = user.username?.lowercase()?.trim() ?: ""
            val globalName = user.global_name?.lowercase()?.trim() ?: ""
            val discordId = user.discord_id?.trim() ?: ""
            val aredlId = user.id?.trim() ?: ""

            val userRoles = allRoles.filter { role -> 
                role.users.any { userElement ->
                    try {
                        val candidate = when (userElement) {
                            is JsonPrimitive -> userElement.content.lowercase().trim()
                            else -> {
                                val obj = userElement.jsonObject
                                (obj["username"]?.jsonPrimitive?.content 
                                    ?: obj["global_name"]?.jsonPrimitive?.content
                                    ?: obj["name"]?.jsonPrimitive?.content 
                                    ?: obj["discord_id"]?.jsonPrimitive?.content
                                    ?: obj["id"]?.jsonPrimitive?.content)?.lowercase()?.trim()
                            }
                        }
                        !candidate.isNullOrBlank() && (candidate == username || candidate == globalName || candidate == discordId || candidate == aredlId)
                    } catch (e: Exception) { false }
                }
            }

            val density = binding.root.resources.displayMetrics.density
            userRoles.forEach { role ->
                binding.playerRolesContainer.addView(createRoleBadge(role, density))
            }
        }

        private fun createRoleBadge(role: RoleResponse, density: Float): View {
            val roleName = role.name.takeIf { it.isNotBlank() } ?: "User"
            val rawName = roleName.lowercase()
            
            var iconRes: Int? = null
            var defaultColor: String? = null

            when {
                rawName.contains("owner") || rawName.contains("awner") -> {
                    iconRes = R.drawable.ic_role_owner
                    defaultColor = "#FFD700"
                }
                rawName.contains("admin") || rawName.contains("administrator") -> {
                    iconRes = R.drawable.ic_role_admin
                    defaultColor = "#E74C3C"
                }
                rawName.contains("moderator") || rawName.contains("mod") -> {
                    iconRes = R.drawable.ic_role_mod
                    defaultColor = "#FF7A45"
                }
                rawName.contains("developer") || rawName.contains("dev") -> {
                    iconRes = R.drawable.ic_role_dev
                    defaultColor = "#5DADE2"
                }
                rawName.contains("helper") -> {
                    iconRes = R.drawable.ic_role_helper
                    defaultColor = "#2ECC71"
                }
                rawName.contains("plus") -> {
                    iconRes = R.drawable.ic_role_plus
                    defaultColor = "#9B59B6"
                }
            }

            val finalColor = parseColor(role.color) ?: parseColor(defaultColor) ?: Color.GRAY
            val resolvedIcon = iconRes ?: R.drawable.ic_role_shield

            return LinearLayout(binding.root.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding((4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt())
                background = GradientDrawable().apply {
                    cornerRadius = 8 * density
                    setColor(finalColor)
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins((4 * density).toInt(), 0, 0, 0) }

                addView(ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams((12 * density).toInt(), (12 * density).toInt())
                    setImageResource(resolvedIcon)
                    imageTintList = ColorStateList.valueOf(Color.WHITE)
                    contentDescription = roleName
                })
            }
        }

        private fun parseColor(colorStr: String?): Int? {
            if (colorStr.isNullOrBlank()) return null
            val s = colorStr.trim()
            return try {
                if (s.startsWith("#")) {
                    Color.parseColor(s)
                } else if (s.startsWith("0x")) {
                    Color.parseColor("#" + s.substring(2))
                } else {
                    val decimal = s.toLongOrNull()
                    if (decimal != null) {
                        if (decimal in 0..0xFFFFFF) (0xFF000000.toLong() or decimal).toInt()
                        else decimal.toInt()
                    } else {
                        val withHash = if (s.length == 6) "#$s" else s
                        Color.parseColor(withHash)
                    }
                }
            } catch (e: Exception) { null }
        }
    }

    class PlayerDiffCallback : DiffUtil.ItemCallback<LeaderboardResponse>() {
        override fun areItemsTheSame(old: LeaderboardResponse, new: LeaderboardResponse) = old.user?.id == new.user?.id
        override fun areContentsTheSame(old: LeaderboardResponse, new: LeaderboardResponse) = 
            old.user?.discord_avatar == new.user?.discord_avatar && old.total_points == new.total_points
    }
}
