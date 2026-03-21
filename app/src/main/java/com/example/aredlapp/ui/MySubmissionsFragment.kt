package com.example.aredlapp.ui

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.aredlapp.R
import com.example.aredlapp.databinding.FragmentMySubmissionsBinding
import com.example.aredlapp.utils.ThemeUtils
import com.example.aredlapp.viewmodel.AredlViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class MySubmissionsFragment : Fragment() {

    private var _binding: FragmentMySubmissionsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AredlViewModel by activityViewModels()

    private val adapter: SubmissionLevelsAdapter by lazy {
        SubmissionLevelsAdapter(
            onFavoriteClick = { viewModel.toggleFavorite(it) },
            onTodoClick = { viewModel.toggleTodo(it) },
            onItemClick = { _, submission ->
                submission?.submissionId?.let { submissionId ->
                    viewModel.selectSubmission(submissionId)
                    findNavController().navigate(R.id.nav_submission_detail)
                }
            }
        )
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMySubmissionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        applyColors()
        binding.recyclerMySubmissions.layoutManager = LinearLayoutManager(context)
        binding.recyclerMySubmissions.adapter = adapter
        binding.recyclerMySubmissions.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val shouldShow = recyclerView.computeVerticalScrollOffset() > 900
                binding.btnBackToTopSubmissions.isVisible = shouldShow
            }
        })
        binding.btnNewSubmission.setOnClickListener {
            if (viewModel.submissionsOpen.value == true) {
                viewModel.prepareNewSubmission()
                findNavController().navigate(R.id.nav_submission_detail)
            }
        }
        binding.btnBackToTopSubmissions.setOnClickListener {
            binding.recyclerMySubmissions.smoothScrollToPosition(0)
        }

        if (viewModel.authState.value.isAuthenticated) {
            viewModel.refreshAuthenticatedSubmissions()
        }
        viewModel.refreshSubmissionsStatus()

        viewLifecycleOwner.lifecycleScope.launch {
            combine(viewModel.authState, viewModel.levels) { auth, levels ->
                auth.isAuthenticated to levels.isNotEmpty()
            }.distinctUntilChanged().collect { (isAuthenticated, hasLevels) ->
                if (isAuthenticated && hasLevels) {
                    viewModel.refreshAuthenticatedSubmissions()
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val baseState = combine(
                viewModel.mySubmissionLevels,
                viewModel.favoriteLevels,
                viewModel.todoLevels,
                viewModel.submissionInfoByLevel
            ) { submissionLevels, favs, todos, submissions ->
                SubmissionBaseState(submissionLevels, favs, todos, submissions)
            }

            val levelState = combine(
                baseState,
                viewModel.levels
            ) { base, levels ->
                SubmissionScreenState(base.submissionLevels, base.favorites, base.todos, base.submissions, levels.size)
            }

            combine(viewModel.authState, levelState) { auth, state ->
                adapter.updateStates(state.favorites, state.todos, state.submissions)
                val submittedLevels = state.submissionLevels.map { it.level }
                val queueOpen = viewModel.submissionsOpen.value

                binding.textMySubmissionsQueueStatus.isVisible = queueOpen != null
                binding.textMySubmissionsQueueStatus.text = when (queueOpen) {
                    true -> "Submission queue is open."
                    false -> "Submission queue is closed."
                    null -> ""
                }
                binding.btnNewSubmission.isVisible = auth.isAuthenticated && queueOpen == true

                binding.textMySubmissionsEmpty.visibility =
                    if (!auth.isAuthenticated || submittedLevels.isEmpty()) View.VISIBLE else View.GONE
                binding.recyclerMySubmissions.visibility =
                    if (auth.isAuthenticated && submittedLevels.isNotEmpty()) View.VISIBLE else View.GONE
                if (!auth.isAuthenticated || submittedLevels.isEmpty()) {
                    binding.btnBackToTopSubmissions.isVisible = false
                }

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

    private fun applyColors() {
        val color = ThemeUtils.getSecondaryColor(requireContext())
        val colorStateList = ColorStateList.valueOf(color)
        binding.textMySubmissionsTitle.setTextColor(color)
        binding.btnNewSubmission.strokeColor = colorStateList
        binding.btnNewSubmission.setTextColor(color)
        binding.btnBackToTopSubmissions.backgroundTintList = colorStateList
    }

    private data class SubmissionScreenState(
        val submissionLevels: List<com.example.aredlapp.models.UserSubmissionLevelItem>,
        val favorites: Set<String>,
        val todos: Set<String>,
        val submissions: Map<String, com.example.aredlapp.models.UserSubmissionInfo>,
        val levelsCount: Int
    )

    private data class SubmissionBaseState(
        val submissionLevels: List<com.example.aredlapp.models.UserSubmissionLevelItem>,
        val favorites: Set<String>,
        val todos: Set<String>,
        val submissions: Map<String, com.example.aredlapp.models.UserSubmissionInfo>
    )
}
