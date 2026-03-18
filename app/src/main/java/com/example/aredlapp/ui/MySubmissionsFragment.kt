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
import com.example.aredlapp.databinding.FragmentMySubmissionsBinding
import com.example.aredlapp.viewmodel.AredlViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class MySubmissionsFragment : Fragment() {

    private var _binding: FragmentMySubmissionsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AredlViewModel by activityViewModels()

    private val adapter: LevelsAdapter by lazy {
        LevelsAdapter(
            onFavoriteClick = { viewModel.toggleFavorite(it) },
            onTodoClick = { viewModel.toggleTodo(it) },
            onItemClick = { level ->
                viewModel.selectLevel(level)
                findNavController().navigate(R.id.nav_level_detail)
            },
            showSubmissionStatus = true
        )
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMySubmissionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerMySubmissions.layoutManager = LinearLayoutManager(context)
        binding.recyclerMySubmissions.adapter = adapter

        if (viewModel.authState.value.isAuthenticated) {
            viewModel.refreshAuthenticatedSubmissions()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val levelState = combine(
                viewModel.mySubmissionLevels,
                viewModel.favoriteLevels,
                viewModel.todoLevels,
                viewModel.completedLevels,
                viewModel.submissionInfoByLevel
            ) { submissionsLevels, favs, todos, done, submissions ->
                SubmissionScreenState(submissionsLevels, favs, todos, done, submissions)
            }

            combine(viewModel.authState, levelState) { auth, state ->
                adapter.updateStates(state.favorites, state.todos, state.completed, state.submissions)
                val submittedLevels = state.submissionLevels.map { it.level }

                binding.textMySubmissionsEmpty.visibility =
                    if (!auth.isAuthenticated || submittedLevels.isEmpty()) View.VISIBLE else View.GONE
                binding.recyclerMySubmissions.visibility =
                    if (auth.isAuthenticated && submittedLevels.isNotEmpty()) View.VISIBLE else View.GONE

                binding.textMySubmissionsEmpty.text = when {
                    !auth.isAuthenticated -> "Log in to see your submissions."
                    else -> "No submissions found yet."
                }

                adapter.submitList(submittedLevels)
            }.collect {}
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private data class SubmissionScreenState(
        val submissionLevels: List<com.example.aredlapp.models.UserSubmissionLevelItem>,
        val favorites: Set<String>,
        val todos: Set<String>,
        val completed: Set<String>,
        val submissions: Map<String, com.example.aredlapp.models.UserSubmissionInfo>
    )
}
