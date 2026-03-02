package com.example.aredlapp.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import coil.load
import com.example.aredlapp.databinding.FragmentRandomDemonBinding
import com.example.aredlapp.models.LevelResponse
import com.example.aredlapp.viewmodel.AredlViewModel
import kotlin.random.Random

class RandomDemonFragment : Fragment() {

    private var _binding: FragmentRandomDemonBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AredlViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRandomDemonBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnGenerateRandom.setOnClickListener {
            val minRank = binding.editMinRank.text.toString().toIntOrNull() ?: 1
            val maxRank = binding.editMaxRank.text.toString().toIntOrNull() ?: 500
            generateRandomLevel(minRank, maxRank)
        }

        binding.btnBackGames.setOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun generateRandomLevel(min: Int, max: Int) {
        val allLevels = viewModel.levels.value
        val filtered = allLevels.filter { it.position in min..max }
        if (filtered.isNotEmpty()) {
            val randomLevel = filtered[Random.nextInt(filtered.size)]
            displayLevel(randomLevel)
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
        _binding = null
    }
}
