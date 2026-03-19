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
import coil.load
import com.example.aredlapp.databinding.FragmentRandomDemonBinding
import com.example.aredlapp.models.LevelResponse
import com.example.aredlapp.viewmodel.AredlViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

class RandomDemonFragment : Fragment() {

    private var _binding: FragmentRandomDemonBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AredlViewModel by activityViewModels()
    private var spinJob: Job? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRandomDemonBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnGenerateRandom.setOnClickListener {
            val minRank = binding.editMinRank.text.toString().toIntOrNull() ?: 1
            val maxRank = binding.editMaxRank.text.toString().toIntOrNull() ?: 500
            startRoulette(minRank, maxRank)
        }

        binding.btnBackGames.setOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun startRoulette(min: Int, max: Int) {
        val allLevels = viewModel.levels.value
        val completed = viewModel.completedLevels.value
        val finalPool = allLevels.filter { it.position in min..max && it.id !in completed }
        val previewPool = allLevels.filter { it.position in min..max }

        if (finalPool.isEmpty()) {
            Toast.makeText(context, "No available unbeaten level in this range.", Toast.LENGTH_SHORT).show()
            return
        }

        spinJob?.cancel()
        spinJob = viewLifecycleOwner.lifecycleScope.launch {
            binding.btnGenerateRandom.isEnabled = false
            binding.btnGenerateRandom.text = "Rolling..."
            binding.cardRandomResult.visibility = View.VISIBLE

            repeat(30) {
                val previewLevel = previewPool.random()
                displayLevel(previewLevel)
                delay(100)
            }

            val finalLevel = finalPool.random()
            displayLevel(finalLevel)
            binding.btnGenerateRandom.isEnabled = true
            binding.btnGenerateRandom.text = "Generate Random Pick"
        }
    }

    private fun displayLevel(level: LevelResponse) {
        binding.cardRandomResult.visibility = View.VISIBLE
        binding.textRandomLevelName.text = level.name
        binding.textRandomLevelRank.text = "#${level.position}"
        
        // add creator
        val creator = level.global_name ?: "AREDL"
        binding.textRandomLevelCreator.text = "by $creator"
        binding.textRandomLevelCreator.visibility = if (creator != "AREDL") View.VISIBLE else View.GONE

        binding.imageRandomThumbnail.load("https://raw.githubusercontent.com/All-Rated-Extreme-Demon-List/Thumbnails/main/levels/cards/${level.level_id}.webp") {
            crossfade(true)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        spinJob?.cancel()
        _binding = null
    }
}
