package com.example.aredlapp.ui

import android.content.res.ColorStateList
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
import com.example.aredlapp.databinding.FragmentLeaderboardBinding
import com.example.aredlapp.viewmodel.AredlViewModel
import com.example.aredlapp.utils.ThemeUtils
import kotlinx.coroutines.launch

class LeaderboardFragment : Fragment() {

    private var _binding: FragmentLeaderboardBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AredlViewModel by activityViewModels()
    
    private val adapter: LeaderboardAdapter by lazy {
        LeaderboardAdapter { player ->
            viewModel.selectPlayer(player)
            findNavController().navigate(R.id.action_leaderboard_to_detail)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLeaderboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.recyclerLeaderboard.layoutManager = LinearLayoutManager(context)
        binding.recyclerLeaderboard.adapter = adapter

        applySecondaryColors()

        // Sync leaderboard list
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.leaderboard.collect { players ->
                adapter.submitList(players)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isLoading.collect { isLoading ->
                binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.currentPage.collect { updatePageInfo() }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.totalPages.collect { updatePageInfo() }
        }

        // Animation de la barre de recherche au scroll
        binding.recyclerLeaderboard.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy > 20) {
                    binding.layoutSearch.animate().translationY(-binding.layoutSearch.height.toFloat()).alpha(0f).setDuration(200).start()
                    binding.fabBackToTop.show()
                } else if (dy < -20) {
                    binding.layoutSearch.animate().translationY(0f).alpha(1f).setDuration(200).start()
                    if (recyclerView.computeVerticalScrollOffset() < 100) binding.fabBackToTop.hide()
                }
            }
        })

        binding.fabBackToTop.setOnClickListener {
            binding.recyclerLeaderboard.smoothScrollToPosition(0)
            binding.layoutSearch.animate().translationY(0f).alpha(1f).setDuration(200).start()
        }

        binding.btnNext.setOnClickListener { viewModel.nextPage() }
        binding.btnPrev.setOnClickListener { viewModel.previousPage() }

        binding.searchPlayers.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString()
                viewModel.searchLeaderboard(query)
                binding.layoutPagination.visibility = if (query.isBlank()) View.VISIBLE else View.GONE
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun applySecondaryColors() {
        val color = ThemeUtils.getSecondaryColor(requireContext())
        val colorStateList = ColorStateList.valueOf(color)
        
        binding.btnNext.setTextColor(color)
        binding.btnNext.iconTint = colorStateList
        binding.btnPrev.setTextColor(color)
        binding.btnPrev.iconTint = colorStateList
        binding.fabBackToTop.backgroundTintList = colorStateList
    }

    private fun updatePageInfo() {
        binding.textPageInfo.text = "Page ${viewModel.currentPage.value} / ${viewModel.totalPages.value}"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
