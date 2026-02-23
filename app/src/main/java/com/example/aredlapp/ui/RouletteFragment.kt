package com.example.aredlapp.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import com.example.aredlapp.databinding.FragmentRouletteBinding
import com.example.aredlapp.models.RecordInfo
import com.example.aredlapp.viewmodel.AredlViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class RouletteFragment : Fragment() {

    private var _binding: FragmentRouletteBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AredlViewModel by activityViewModels()
    private lateinit var historyAdapter: RecordsAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRouletteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupHistoryList()

        // Sync Goal
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.roulettePercent.collect { percent ->
                binding.textCurrentPercent.text = "$percent%"
                binding.textCompletedBadge.visibility = View.VISIBLE
                binding.textCompletedBadge.text = "GOAL: $percent%"
                binding.inputLayoutPercent.placeholderText = "Min $percent%"
            }
        }

        // Sync Won State
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.rouletteWon.collect { won ->
                if (won) {
                    binding.viewWinnerOverlay.visibility = View.VISIBLE
                    binding.textWinnerMessage.visibility = View.VISIBLE
                    binding.inputLayoutPercent.isEnabled = false
                    binding.btnComplete.isEnabled = false
                } else {
                    binding.viewWinnerOverlay.visibility = View.GONE
                    binding.textWinnerMessage.visibility = View.GONE
                    binding.inputLayoutPercent.isEnabled = true
                    binding.btnComplete.isEnabled = true
                }
            }
        }

        // Sync Current Level
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.currentRouletteLevel.collect { level ->
                level?.let {
                    binding.textLevelName.text = it.name
                    binding.textLevelRank.text = "#${it.position}"
                    binding.textLevelPoints.text = String.format("%.1f points", it.points)
                    
                    // Add creator name
                    val creator = it.global_name ?: "AREDL"
                    binding.textLevelCreator.text = "by $creator"
                    binding.textLevelCreator.visibility = if (creator != "AREDL") View.VISIBLE else View.GONE

                    binding.imageLevelThumbnail.load("https://raw.githubusercontent.com/All-Rated-Extreme-Demon-List/Thumbnails/main/levels/cards/${it.level_id}.webp") {
                        crossfade(true)
                    }
                }
            }
        }

        // Sync History
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.rouletteHistory.collect { history ->
                historyAdapter.submitList(history)
                binding.labelHistory.visibility = if (history.isNotEmpty()) View.VISIBLE else View.GONE
            }
        }

        binding.btnComplete.setOnClickListener {
            val input = binding.editPercentDone.text.toString().toIntOrNull()
            val required = viewModel.roulettePercent.value
            if (input != null && input >= required) {
                viewModel.advanceRoulette(input)
                binding.editPercentDone.text?.clear()
            } else {
                Toast.makeText(context, "You need at least $required%", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnResetRoulette.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Reset Roulette?")
                .setMessage("Are you sure you want to delete all your progress?")
                .setPositiveButton("Reset") { _, _ -> viewModel.resetRoulette() }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.btnBackGames.setOnClickListener { findNavController().navigateUp() }
        
        binding.scrollRoulette.setOnScrollChangeListener(View.OnScrollChangeListener { _, _, scrollY, _, _ ->
            if (scrollY > 100) binding.fabBackToTop.show() else binding.fabBackToTop.hide()
        })
        binding.fabBackToTop.setOnClickListener { binding.scrollRoulette.smoothScrollTo(0, 0) }
    }

    private fun setupHistoryList() {
        historyAdapter = RecordsAdapter { _ -> }
        binding.recyclerHistory.apply {
            adapter = historyAdapter
            layoutManager = LinearLayoutManager(context)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
