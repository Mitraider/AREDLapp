package com.example.aredlapp.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aredlapp.R
import com.example.aredlapp.databinding.FragmentLevelsBinding
import com.example.aredlapp.viewmodel.AredlViewModel
import com.example.aredlapp.utils.ThemeUtils
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class LevelsFragment : Fragment() {

    private var _binding: FragmentLevelsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AredlViewModel by activityViewModels()
    private lateinit var adapter: LevelsAdapter
    private val selectedTags = mutableSetOf<String>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLevelsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = LevelsAdapter(
            onFavoriteClick = { id -> viewModel.toggleFavorite(id) },
            onTodoClick = { id -> viewModel.toggleTodo(id) },
            onCompletedClick = { id -> viewModel.toggleCompleted(id) },
            onItemClick = { level ->
                viewModel.selectLevel(level)
                findNavController().navigate(R.id.action_levels_to_detail)
            }
        )

        binding.recyclerLevels.layoutManager = LinearLayoutManager(context)
        binding.recyclerLevels.adapter = adapter

        applySecondaryColors()

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.favoriteLevels.collect { favs ->
                adapter.updateStates(favs, viewModel.todoLevels.value, viewModel.completedLevels.value)
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.todoLevels.collect { todos ->
                adapter.updateStates(viewModel.favoriteLevels.value, todos, viewModel.completedLevels.value)
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.completedLevels.collect { done ->
                adapter.updateStates(viewModel.favoriteLevels.value, viewModel.todoLevels.value, done)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.levels.collect { levels ->
                adapter.submitList(levels)
                applyFilters()
            }
        }

        binding.searchLevels.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                applyFilters()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnFilter.setOnClickListener { showFilterDialog() }

        binding.recyclerLevels.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy > 10) {
                    binding.layoutSearch.animate().translationY(-binding.layoutSearch.height.toFloat()).alpha(0f).setDuration(200).start()
                } else if (dy < -10) {
                    binding.layoutSearch.animate().translationY(0f).alpha(1f).setDuration(200).start()
                }

                if (!recyclerView.canScrollVertically(-1)) {
                    binding.fabBackToTop.hide()
                } else if (dy > 0) {
                    binding.fabBackToTop.show()
                }
            }
        })

        binding.fabBackToTop.setOnClickListener {
            binding.recyclerLevels.smoothScrollToPosition(0)
            binding.layoutSearch.animate().translationY(0f).alpha(1f).setDuration(200).start()
        }
    }

    private fun applySecondaryColors() {
        val color = ThemeUtils.getSecondaryColor(requireContext())
        binding.btnFilter.imageTintList = android.content.res.ColorStateList.valueOf(color)
        binding.fabBackToTop.backgroundTintList = android.content.res.ColorStateList.valueOf(color)
    }

    private fun applyFilters() {
        val query = binding.searchLevels.text.toString()
        val allLevels = viewModel.levels.value
        val filtered = allLevels.filter { level ->
            val matchesQuery = level.name.contains(query, ignoreCase = true)
            val matchesTags = selectedTags.isEmpty() || selectedTags.all { level.tags?.contains(it) == true }
            matchesQuery && matchesTags
        }
        adapter.submitList(filtered)
    }

    private fun showFilterDialog() {
        val allTags = viewModel.availableTags.value.toTypedArray()
        val checkedItems = BooleanArray(allTags.size) { index -> selectedTags.contains(allTags[index]) }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Select your filters below:")
            .setMultiChoiceItems(allTags, checkedItems) { _, which, isChecked ->
                if (isChecked) selectedTags.add(allTags[which]) else selectedTags.remove(allTags[which])
            }
            .setPositiveButton("Apply") { _, _ -> applyFilters() }
            .setNeutralButton("Clear All Filters") { _, _ ->
                selectedTags.clear()
                applyFilters()
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
