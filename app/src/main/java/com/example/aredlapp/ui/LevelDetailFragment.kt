package com.example.aredlapp.ui

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.aredlapp.R
import com.example.aredlapp.databinding.FragmentLevelDetailBinding
import com.example.aredlapp.models.LeaderboardResponse
import com.example.aredlapp.models.LevelRecord
import com.example.aredlapp.utils.ThemeUtils
import com.example.aredlapp.viewmodel.AredlViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class LevelDetailFragment : Fragment() {

    private var _binding: FragmentLevelDetailBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AredlViewModel by activityViewModels()
    private var currentSearchQuery: String = ""
    
    private val headerAdapter = LevelHeaderAdapter { query ->
        currentSearchQuery = query
        filterAndSubmitVictors(viewModel.currentLevelVictors.value)
    }
    
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

        binding.recyclerLevelDetail.apply {
            adapter = ConcatAdapter(headerAdapter, victorsAdapter)
            layoutManager = LinearLayoutManager(context)
            
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    if (!recyclerView.canScrollVertically(-1)) {
                        binding.fabBackToTop.hide()
                    } else if (dy > 10) {
                        binding.fabBackToTop.show()
                    }
                }
            })
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.selectedLevel.collectLatest { level ->
                level?.let { 
                    headerAdapter.setLevel(it)
                    binding.levelDetailBackground.load("https://raw.githubusercontent.com/All-Rated-Extreme-Demon-List/Thumbnails/main/levels/cards/${it.level_id}.webp") { crossfade(true) }
                    viewModel.selectLevel(it)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.currentLevelVictors.collectLatest { victors ->
                filterAndSubmitVictors(victors)
            }
        }

        val color = ThemeUtils.getSecondaryColor(requireContext())
        binding.btnBack.imageTintList = ColorStateList.valueOf(color)
        binding.fabBackToTop.backgroundTintList = ColorStateList.valueOf(color)
        binding.btnBack.setOnClickListener { findNavController().navigateUp() }
        binding.fabBackToTop.setOnClickListener { binding.recyclerLevelDetail.smoothScrollToPosition(0) }
    }

    private fun filterAndSubmitVictors(victors: List<LevelRecord>) {
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
