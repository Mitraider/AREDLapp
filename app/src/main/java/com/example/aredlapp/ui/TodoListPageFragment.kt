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
import com.example.aredlapp.databinding.FragmentTodoListPageBinding
import com.example.aredlapp.viewmodel.AredlViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class TodoListPageFragment : Fragment() {

    private var _binding: FragmentTodoListPageBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AredlViewModel by activityViewModels()
    
    private var listType: Int = 0 // 0: Favorites, 1: To-Do, 2: Completed

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        listType = arguments?.getInt(ARG_TYPE) ?: 0
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTodoListPageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerTodoPage.layoutManager = LinearLayoutManager(context)
        binding.recyclerTodoPage.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            combine(
                viewModel.levels,
                viewModel.favoriteLevels,
                viewModel.todoLevels,
                viewModel.completedLevels,
                viewModel.submissionInfoByLevel
            ) { levels, favs, todos, done, submissions ->
                adapter.updateStates(favs, todos, done, submissions)
                val filtered = when (listType) {
                    0 -> levels.filter { favs.contains(it.id) }
                    1 -> levels.filter { todos.contains(it.id) }
                    else -> levels.filter { done.contains(it.id) }
                }
                adapter.submitList(filtered)
            }.collect {}
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_TYPE = "list_type"
        fun newInstance(type: Int) = TodoListPageFragment().apply {
            arguments = Bundle().apply { putInt(ARG_TYPE, type) }
        }
    }
}
