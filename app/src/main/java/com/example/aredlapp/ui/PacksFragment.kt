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
import com.example.aredlapp.R
import com.example.aredlapp.databinding.FragmentPacksBinding
import com.example.aredlapp.utils.ThemeUtils
import com.example.aredlapp.viewmodel.AredlViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class PacksFragment : Fragment() {

    private var _binding: FragmentPacksBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AredlViewModel by activityViewModels()
    private var currentSearchQuery: String = ""

    private val adapter = TierPacksAdapter { item ->
        viewModel.selectPack(item.pack)
        findNavController().navigate(R.id.nav_pack_detail)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPacksBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerPacks.layoutManager = LinearLayoutManager(context)
        binding.recyclerPacks.adapter = adapter
        applySecondaryColors()
        viewModel.refreshPackTiers()
        viewModel.refreshAuthenticatedCompletions()

        viewLifecycleOwner.lifecycleScope.launch {
            combine(viewModel.packTiers, viewModel.completedLevels, viewModel.completedLevelGdIds) { tiers, completed, gdIds ->
                adapter.updateCompletedLevels(completed, gdIds)
                renderPackSections(tiers)
            }.collect {}
        }

        binding.searchPacks.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentSearchQuery = s?.toString().orEmpty()
                renderPackSections(viewModel.packTiers.value)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.recyclerPacks.addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                if (!recyclerView.canScrollVertically(-1)) {
                    binding.fabBackToTopPacks.hide()
                } else if (dy > 0) {
                    binding.fabBackToTopPacks.show()
                }
            }
        })

        binding.fabBackToTopPacks.setOnClickListener {
            binding.recyclerPacks.smoothScrollToPosition(0)
        }
    }

    private fun applySecondaryColors() {
        val color = ThemeUtils.getSecondaryColor(requireContext())
        binding.textPacksTitle.setTextColor(color)
        binding.fabBackToTopPacks.backgroundTintList = ColorStateList.valueOf(color)
    }

    private fun renderPackSections(tiers: List<com.example.aredlapp.models.PackTierResolvedResponse>) {
        val normalizedQuery = currentSearchQuery.trim()
        val items = tiers
            .sortedBy { it.placement }
            .map { tier ->
                val filteredPacks = tier.packs
                    .sortedBy { it.name.lowercase() }
                    .filter { normalizedQuery.isBlank() || it.name.contains(normalizedQuery, ignoreCase = true) }
                PackTierSection(tier, filteredPacks)
            }
            .filter { it.packs.isNotEmpty() }

        binding.textPacksEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        binding.recyclerPacks.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
        adapter.submitList(items)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
