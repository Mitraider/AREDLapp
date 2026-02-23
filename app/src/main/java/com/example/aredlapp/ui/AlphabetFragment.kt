package com.example.aredlapp.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import com.example.aredlapp.databinding.FragmentAlphabetBinding
import com.example.aredlapp.models.RecordInfo
import com.example.aredlapp.viewmodel.AredlViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class AlphabetFragment : Fragment() {

    private var _binding: FragmentAlphabetBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AredlViewModel by activityViewModels()
    private val historyAdapter = RecordsAdapter { _ -> }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAlphabetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.recyclerHistory.apply {
            adapter = historyAdapter
            layoutManager = LinearLayoutManager(context)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.alphabetProgress.collect { index ->
                binding.textCurrentLetter.text = "Current Letter: ${('A' + index)}"
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.alphabetWon.collect { won ->
                binding.viewWinnerOverlay.visibility = if (won) View.VISIBLE else View.GONE
                binding.textWinnerMessage.visibility = if (won) View.VISIBLE else View.GONE
                binding.layoutLevelDetails.visibility = if (won) View.GONE else View.VISIBLE
                binding.btnCompleteAlphabet.isEnabled = !won
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.currentAlphabetLevel.collect { level ->
                level?.let {
                    binding.textLevelName.text = it.name
                    binding.textLevelRank.text = "#${it.position}"
                    binding.textLevelPoints.text = String.format("%.1f points", it.points)
                    
                    // Add creator name
                    val creator = it.global_name ?: "AREDL"
                    binding.textLevelCreator.text = "by $creator"
                    binding.textLevelCreator.visibility = if (creator != "AREDL") View.VISIBLE else View.GONE
                    
                    binding.imageLevelThumbnail.load("https://raw.githubusercontent.com/All-Rated-Extreme-Demon-List/Thumbnails/main/levels/cards/${it.level_id}.webp")
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            combine(viewModel.alphabetHistory, viewModel.levels) { historyIds, allLevels ->
                historyIds.mapNotNull { hid ->
                    allLevels.find { it.id == hid }?.let { l ->
                        RecordInfo(
                            id = l.id, 
                            level_id = l.level_id, 
                            name = l.name, 
                            position = l.position, 
                            points = l.points, // Points are already /10 in levels list
                            level = l
                        )
                    }
                }
            }.collect { items ->
                historyAdapter.submitList(items)
                binding.labelHistory.visibility = if (items.isNotEmpty()) View.VISIBLE else View.GONE
            }
        }

        binding.btnCompleteAlphabet.setOnClickListener { viewModel.advanceAlphabet() }
        binding.btnResetAlphabet.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Reset Challenge?")
                .setMessage("Delete all progress?")
                .setPositiveButton("Reset") { _, _ -> viewModel.resetAlphabet() }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.btnBackGames.setOnClickListener { findNavController().navigateUp() }
        binding.scrollAlphabet.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            if (scrollY > 100) binding.fabBackToTop.show() else binding.fabBackToTop.hide()
        }
        binding.fabBackToTop.setOnClickListener { binding.scrollAlphabet.smoothScrollTo(0, 0) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
