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
import com.example.aredlapp.R
import com.example.aredlapp.databinding.FragmentTodoBinding
import com.example.aredlapp.viewmodel.AredlViewModel
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class TodoFragment : Fragment() {

    private var _binding: FragmentTodoBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AredlViewModel by activityViewModels()
    
    private val adapter: LevelsAdapter by lazy {
        LevelsAdapter(
            onFavoriteClick = { viewModel.toggleFavorite(it) },
            onTodoClick = { viewModel.toggleTodo(it) },
            onCompletedClick = { viewModel.toggleCompleted(it) },
            onItemClick = { level ->
                viewModel.selectLevel(level)
                findNavController().navigate(R.id.nav_level_detail)
            }
        )
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTodoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.recyclerTodo.layoutManager = LinearLayoutManager(context)
        binding.recyclerTodo.adapter = adapter

        binding.tabTodo.apply {
            removeAllTabs()
            addTab(newTab().setText("To-Do"))
            addTab(newTab().setText("Favorites"))
            addTab(newTab().setText("Completed"))
            addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab?) { updateList() }
                override fun onTabUnselected(tab: TabLayout.Tab?) {}
                override fun onTabReselected(tab: TabLayout.Tab?) {}
            })
        }

        viewLifecycleOwner.lifecycleScope.launch {
            combine(
                viewModel.levels,
                viewModel.favoriteLevels,
                viewModel.todoLevels,
                viewModel.completedLevels
            ) { _, favs, todos, done ->
                adapter.updateStates(favs, todos, done)
                updateList()
            }.collect {}
        }
    }

    private fun updateList() {
        val position = binding.tabTodo.selectedTabPosition
        val filtered = when (position) {
            0 -> viewModel.levels.value.filter { viewModel.todoLevels.value.contains(it.id) }
            1 -> viewModel.levels.value.filter { viewModel.favoriteLevels.value.contains(it.id) }
            else -> viewModel.levels.value.filter { viewModel.completedLevels.value.contains(it.id) }
        }
        adapter.submitList(filtered)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
