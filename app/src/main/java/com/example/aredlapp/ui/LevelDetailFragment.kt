package com.example.aredlapp.ui

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import com.example.aredlapp.R
import com.example.aredlapp.databinding.FragmentLevelDetailBinding
import com.example.aredlapp.models.LeaderboardResponse
import com.example.aredlapp.models.LevelResponse
import com.example.aredlapp.models.LevelPackResponse
import com.example.aredlapp.utils.LevelUtils
import com.example.aredlapp.utils.PackUtils
import com.example.aredlapp.utils.ThemeUtils
import com.example.aredlapp.utils.YouTubeUtils
import com.example.aredlapp.viewmodel.AredlViewModel
import com.google.android.material.chip.Chip
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale

class LevelDetailFragment : Fragment() {

    private var _binding: FragmentLevelDetailBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AredlViewModel by activityViewModels()
    private var currentSearchQuery: String = ""
    private var youtubePlayer: YouTubePlayer? = null
    
    private val victorsAdapter = VictorsAdapter { record ->
        val user = record.user ?: record.player
        viewModel.selectPlayer(LeaderboardResponse(user = user))
        findNavController().navigate(R.id.nav_player_detail)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLevelDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        applySecondaryColors()

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.selectedLevel.collectLatest { level ->
                level?.let { updateUI(it) }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.currentLevelVictors.collectLatest { victors ->
                filterAndSubmitVictors(victors)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.currentLevelPacks.collectLatest { packs ->
                updatePackChips(packs)
            }
        }

        binding.btnBack.setOnClickListener { findNavController().navigateUp() }
        binding.fabBackToTop.setOnClickListener { 
            binding.scrollLevel.smoothScrollTo(0, 0)
        }

        binding.searchVictors.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentSearchQuery = s.toString()
                filterAndSubmitVictors(viewModel.currentLevelVictors.value)
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    private fun updateUI(l: LevelResponse) {
        val color = ThemeUtils.getSecondaryColor(requireContext())
        
        binding.detailLevelName.text = l.name
        binding.detailLevelCreator.text = LevelUtils.resolveCreatorName(l)?.let { "by $it" } ?: "by Unknown"
        binding.detailLevelRank.text = "Rank #${l.position}"
        binding.detailLevelPoints.text = "Points: ${String.format("%.1f", l.points)}"
        binding.detailLevelId.text = "Level ID: ${l.level_id ?: l.id}"
        binding.detailLevelSong.text = "Song ID: ${l.song ?: l.song_id ?: "-"}"
        binding.detailLevelTiers.text =
            "Edel: ${formatTier(l.edel_enjoyment)} | GDDL: ${formatTier(l.gddl_tier)} | NLW: ${l.nlw_tier ?: "-"}"
        binding.detailLevelTiers.setTextColor(color)
        binding.detailLevelDescription.text = l.description?.takeIf { it.isNotBlank() } ?: "No description available."

        binding.levelBackground.load("https://raw.githubusercontent.com/All-Rated-Extreme-Demon-List/Thumbnails/main/levels/cards/${l.level_id}.webp") { 
            crossfade(true) 
        }

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
            val chip = Chip(requireContext()).apply {
                text = tagName
                setTextColor(color)
                chipStrokeColor = ColorStateList.valueOf(color)
                chipStrokeWidth = 2f
                chipBackgroundColor = ColorStateList.valueOf(requireContext().resources.getColor(R.color.aredl_dark_grey, null))
            }
            binding.tagsChipGroup.addView(chip)
        }
    }

    private fun updatePackChips(packs: List<LevelPackResponse>) {
        binding.packsChipGroup.removeAllViews()
        binding.labelPacks.visibility = if (packs.isEmpty()) View.GONE else View.VISIBLE
        binding.packsChipGroup.visibility = if (packs.isEmpty()) View.GONE else View.VISIBLE

        packs.forEach { pack ->
            val chip = Chip(requireContext()).apply {
                text = pack.name
                isClickable = true
                isCheckable = false
                setTextColor(android.graphics.Color.WHITE)
                chipBackgroundColor = ColorStateList.valueOf(PackUtils.resolveTierColor(pack.tier.color))
                setOnClickListener {
                    viewModel.selectPackFromLevelPack(pack)
                    findNavController().navigate(R.id.nav_pack_detail)
                }
            }
            binding.packsChipGroup.addView(chip)
        }
    }

    private fun setupRecyclerView() {
        binding.recyclerVictors.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = victorsAdapter
            
            // Allow inner scrolling without conflict with main NestedScrollView
            setOnTouchListener { v, event ->
                v.parent.requestDisallowInterceptTouchEvent(true)
                if (event.action == MotionEvent.ACTION_UP) {
                    v.performClick()
                }
                false
            }
        }
    }

    private fun applySecondaryColors() {
        val color = ThemeUtils.getSecondaryColor(requireContext())
        binding.btnBack.imageTintList = ColorStateList.valueOf(color)
        binding.fabBackToTop.backgroundTintList = ColorStateList.valueOf(color)
        binding.detailLevelName.setTextColor(color)
        binding.detailLevelTiers.setTextColor(color)
        listOf(binding.labelTags, binding.labelPacks, binding.labelDescription, binding.labelVictors).forEach { it.setTextColor(color) }
    }

    private fun filterAndSubmitVictors(victors: List<com.example.aredlapp.models.LevelRecord>) {
        val filtered = if (currentSearchQuery.isEmpty()) {
            victors 
        } else {
            victors.filter { record ->
                val user = record.user ?: record.player
                val name = user?.global_name ?: user?.username ?: ""
                name.contains(currentSearchQuery, ignoreCase = true)
            }
        }
        victorsAdapter.submitList(filtered.toList())
    }

    private fun formatTier(value: Double?): String {
        return value?.let { String.format(Locale.US, "%.2f", it) } ?: "-"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
