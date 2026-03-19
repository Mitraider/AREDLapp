package com.example.aredlapp.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.text.TextUtils
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import coil.transform.CircleCropTransformation
import com.example.aredlapp.R
import com.example.aredlapp.databinding.FragmentPlayerDetailBinding
import com.example.aredlapp.viewmodel.AredlViewModel
import com.example.aredlapp.models.LevelResponse
import com.example.aredlapp.models.RoleResponse
import com.example.aredlapp.models.UserInfo
import com.example.aredlapp.utils.CountryUtils
import com.example.aredlapp.utils.FlagUtils
import com.example.aredlapp.utils.ThemeUtils
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class PlayerDetailFragment : Fragment() {

    private var _binding: FragmentPlayerDetailBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AredlViewModel by activityViewModels()
    
    private lateinit var recordsAdapter: RecordsAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPlayerDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        applySecondaryColors()

        viewLifecycleOwner.lifecycleScope.launch {
            combine(viewModel.selectedPlayer, viewModel.roles) { player, roles ->
                player to roles
            }.collect { (player, roles) ->
                player?.let { p ->
                    binding.detailName.text = p.user?.global_name ?: p.user?.username ?: "Unknown"
                    binding.detailCountry.text = CountryUtils.getCountryName(p.country)
                    FlagUtils.loadFlag(binding.detailCountryFlag, p.country)
                    binding.detailPoints.text = String.format("Points: %.1f", p.total_points ?: 0.0)
                    binding.detailClan.text = p.clan?.global_name ?: p.clan?.name ?: ""
                    binding.detailExtremeCount.text = "Extremes: ${p.extremes ?: 0}"
                    binding.detailHardest.text = "Hardest: ${p.hardest?.name ?: "-"}"
                    binding.detailRank.text = "Rank: #${p.rank ?: 0}"

                    val avatarUrl = if (p.user?.discord_id != null && p.user?.discord_avatar != null) {
                        "https://cdn.discordapp.com/avatars/${p.user.discord_id}/${p.user.discord_avatar}.webp?size=256"
                    } else p.user?.avatar

                    binding.detailAvatar.load(avatarUrl) {
                        crossfade(true)
                        placeholder(R.drawable.aredl_logo)
                        error(R.drawable.aredl_logo)
                        transformations(CircleCropTransformation())
                    }
                    
                    setupRoles(p.user, roles)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.selectedPlayerProfile.collect { profile ->
                profile?.let {
                    recordsAdapter.submitList(it.records)
                    val bgId = it.background_level ?: viewModel.selectedPlayer.value?.hardest?.id
                    if (bgId != null) {
                        binding.playerBackground.load("https://raw.githubusercontent.com/All-Rated-Extreme-Demon-List/Thumbnails/main/levels/cards/${bgId}.webp") {
                            crossfade(true)
                        }
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isAuthenticatedProfileView.collect { isOwnProfile ->
                binding.btnBack.visibility = if (isOwnProfile) View.GONE else View.VISIBLE
            }
        }

        binding.btnBack.setOnClickListener {
            if (!findNavController().navigateUp()) {
                findNavController().navigate(R.id.nav_leaderboard)
            }
        }
    }

    private fun setupRoles(user: UserInfo?, roles: List<RoleResponse>) {
        binding.detailRolesContainer.removeAllViews()
        if (user == null || roles.isEmpty()) return

        val username = user.username?.lowercase()?.trim() ?: ""
        val globalName = user.global_name?.lowercase()?.trim() ?: ""
        val discordId = user.discord_id?.trim() ?: ""
        val aredlId = user.id?.trim() ?: ""

        val userRoles = roles.filter { role -> 
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

        userRoles.forEach { role ->
            binding.detailRolesContainer.addView(createRoleBadge(role))
        }
    }

    private fun createRoleBadge(role: RoleResponse): View {
        val density = resources.displayMetrics.density
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

        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((8 * density).toInt(), (3 * density).toInt(), (10 * density).toInt(), (3 * density).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 10 * density
                setColor(finalColor)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins((4 * density).toInt(), 0, (4 * density).toInt(), 0) }

            if (iconRes != null) {
                addView(ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams((14 * density).toInt(), (14 * density).toInt()).apply {
                        marginEnd = (6 * density).toInt()
                    }
                    setImageResource(iconRes)
                    imageTintList = ColorStateList.valueOf(Color.WHITE)
                })
            }

            addView(TextView(context).apply {
                text = roleName.uppercase()
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
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

    private fun setupRecyclerView() {
        recordsAdapter = RecordsAdapter { record ->
            val levelId = record.level?.id ?: record.id ?: ""
            val minimalLevel = LevelResponse(
                id = levelId,
                level_id = record.level?.level_id ?: record.level_id,
                name = record.level?.name ?: record.name ?: "Unknown",
                position = record.level?.position ?: record.position ?: 0,
                points = record.points ?: record.list_points ?: record.level?.points ?: 0.0
            )
            viewModel.selectLevel(minimalLevel)
            findNavController().navigate(R.id.nav_level_detail)
        }

        binding.recyclerRecords.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = recordsAdapter
            setOnTouchListener { v, event ->
                v.parent.requestDisallowInterceptTouchEvent(true)
                if (event.action == MotionEvent.ACTION_UP) v.performClick()
                false
            }
        }
    }

    private fun applySecondaryColors() {
        val color = ThemeUtils.getSecondaryColor(requireContext())
        binding.btnBack.imageTintList = ColorStateList.valueOf(color)
        binding.detailName.setTextColor(color)
        binding.detailHardest.setTextColor(color)
        binding.labelExtremes.setTextColor(color)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
