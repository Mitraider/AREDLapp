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
import com.example.aredlapp.R
import com.example.aredlapp.databinding.FragmentPlayerDetailBinding
import com.example.aredlapp.viewmodel.AredlViewModel
import com.example.aredlapp.models.LevelResponse
import com.example.aredlapp.utils.ThemeUtils
import kotlinx.coroutines.launch

class PlayerDetailFragment : Fragment() {

    private var _binding: FragmentPlayerDetailBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AredlViewModel by activityViewModels()
    
    private val headerAdapter = PlayerHeaderAdapter()
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
            viewModel.selectedPlayer.collect { player ->
                headerAdapter.setPlayer(player)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.selectedPlayerProfile.collect { profile ->
                profile?.let {
                    recordsAdapter.submitList(it.records)
                }
            }
        }

        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.recyclerPlayerProfile.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (!recyclerView.canScrollVertically(-1)) {
                    binding.fabBackToTop.hide()
                } else if (dy > 10) {
                    binding.fabBackToTop.show()
                }
            }
        })

        binding.fabBackToTop.setOnClickListener {
            binding.recyclerPlayerProfile.smoothScrollToPosition(0)
        }
    }

    private fun applySecondaryColors() {
        val color = ThemeUtils.getSecondaryColor(requireContext())
        val colorStateList = ColorStateList.valueOf(color)
        binding.btnBack.imageTintList = colorStateList
        binding.fabBackToTop.backgroundTintList = colorStateList
    }

    private fun setupRecyclerView() {
        recordsAdapter = RecordsAdapter { record ->
            // On récupère l'ID AREDL en priorité pour que selectLevel charge tout
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

        val concatAdapter = ConcatAdapter(headerAdapter, recordsAdapter)
        binding.recyclerPlayerProfile.apply {
            adapter = concatAdapter
            layoutManager = LinearLayoutManager(context)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
