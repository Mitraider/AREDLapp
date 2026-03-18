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
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.aredlapp.R
import com.example.aredlapp.databinding.FragmentPackDetailBinding
import com.example.aredlapp.models.LevelResponse
import com.example.aredlapp.models.PackTierResolvedResponse
import com.example.aredlapp.utils.PackUtils
import com.example.aredlapp.utils.ThemeUtils
import com.example.aredlapp.viewmodel.AredlViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class PackDetailFragment : Fragment() {

    private var _binding: FragmentPackDetailBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AredlViewModel by activityViewModels()

    private val adapter: LevelsAdapter by lazy {
        LevelsAdapter(
            onFavoriteClick = { viewModel.toggleFavorite(it) },
            onTodoClick = { viewModel.toggleTodo(it) },
            onItemClick = { level ->
                viewModel.selectLevel(level)
                findNavController().navigate(R.id.nav_level_detail)
            }
        )
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPackDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerPackLevels.layoutManager = LinearLayoutManager(context)
        binding.recyclerPackLevels.adapter = adapter
        binding.btnBackPack.setOnClickListener { findNavController().navigateUp() }

        viewLifecycleOwner.lifecycleScope.launch {
            val baseUiState = combine(
                viewModel.levels,
                viewModel.favoriteLevels,
                viewModel.todoLevels,
                viewModel.completedLevels
            ) { allLevels, favs, todos, completed ->
                PackDetailBaseUiState(allLevels, favs, todos, completed)
            }

            val packUiState = combine(
                baseUiState,
                viewModel.completedLevelGdIds,
                viewModel.submissionInfoByLevel
            ) { base, completedGdIds, submissions ->
                PackDetailUiState(base.levels, base.favorites, base.todos, base.completed, completedGdIds, submissions)
            }

            combine(viewModel.selectedPack, viewModel.packTiers, packUiState) { pack, tiers, state ->
                val tier = tiers.firstOrNull { candidate -> candidate.packs.any { it.id == pack?.id } }

                adapter.updateStates(state.favorites, state.todos, state.completed, state.submissions)

                if (pack == null || tier == null) {
                    binding.textPackMissing.visibility = View.VISIBLE
                    binding.contentPackDetail.visibility = View.GONE
                    return@combine
                }

                binding.textPackMissing.visibility = View.GONE
                binding.contentPackDetail.visibility = View.VISIBLE

                val tierColor = PackUtils.resolveTierColor(tier.color)
                binding.packDetailTier.text = tier.name
                binding.packDetailTier.backgroundTintList = ColorStateList.valueOf(tierColor)
                binding.packDetailName.text = pack.name

                val done = PackUtils.completedCount(pack, state.completed, state.completedGdIds)
                val total = pack.levels.size
                binding.packDetailProgress.max = total.coerceAtLeast(1)
                binding.packDetailProgress.progress = done
                binding.packDetailProgressText.text = "$done / $total completed"

                val levelMap = state.levels.associateBy { it.id }
                val displayLevels = pack.levels
                    .sortedWith(compareBy({ if (it.position > 0) it.position else Int.MAX_VALUE }, { it.name.lowercase() }))
                    .map { packLevel ->
                    val fullLevel = levelMap[packLevel.id]
                    if (fullLevel != null) {
                        fullLevel
                    } else {
                        LevelResponse(
                            id = packLevel.id,
                            level_id = packLevel.level_id,
                            name = packLevel.name,
                            position = packLevel.position,
                            points = packLevel.points
                        )
                    }
                }
                adapter.submitList(displayLevels)
            }.collect {}
        }

        val color = ThemeUtils.getSecondaryColor(requireContext())
        binding.btnBackPack.imageTintList = ColorStateList.valueOf(color)
        binding.packDetailName.setTextColor(color)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private data class PackDetailUiState(
        val levels: List<LevelResponse>,
        val favorites: Set<String>,
        val todos: Set<String>,
        val completed: Set<String>,
        val completedGdIds: Set<Int>,
        val submissions: Map<String, com.example.aredlapp.models.UserSubmissionInfo>
    )

    private data class PackDetailBaseUiState(
        val levels: List<LevelResponse>,
        val favorites: Set<String>,
        val todos: Set<String>,
        val completed: Set<String>
    )
}
