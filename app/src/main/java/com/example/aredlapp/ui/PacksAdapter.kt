package com.example.aredlapp.ui

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.aredlapp.databinding.ItemPackBinding
import com.example.aredlapp.databinding.ItemPackTierSectionBinding
import com.example.aredlapp.models.PackResponse
import com.example.aredlapp.models.PackTierResolvedResponse
import com.example.aredlapp.utils.PackUtils

data class PackListItem(
    val tier: PackTierResolvedResponse,
    val pack: PackResponse
)

data class PackTierSection(
    val tier: PackTierResolvedResponse,
    val packs: List<PackResponse>
)

class TierPacksAdapter(
    private val onPackClick: (PackListItem) -> Unit
) : ListAdapter<PackTierSection, TierPacksAdapter.TierSectionViewHolder>(TierSectionDiffCallback()) {

    private var completedLevels: Set<String> = emptySet()
    private var completedGdIds: Set<Int> = emptySet()

    fun updateCompletedLevels(completed: Set<String>, gdIds: Set<Int>) {
        completedLevels = completed
        completedGdIds = gdIds
        notifyItemRangeChanged(0, itemCount, "progress")
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TierSectionViewHolder {
        return TierSectionViewHolder(
            ItemPackTierSectionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }

    override fun onBindViewHolder(holder: TierSectionViewHolder, position: Int) = holder.bind(getItem(position))

    override fun onBindViewHolder(holder: TierSectionViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) super.onBindViewHolder(holder, position, payloads) else holder.bind(getItem(position))
    }

    inner class TierSectionViewHolder(private val binding: ItemPackTierSectionBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private val innerAdapter = PacksAdapter(onPackClick)

        init {
            binding.recyclerTierPacks.layoutManager =
                LinearLayoutManager(binding.root.context, LinearLayoutManager.HORIZONTAL, false)
            binding.recyclerTierPacks.adapter = innerAdapter
        }

        fun bind(section: PackTierSection) {
            val tierColor = PackUtils.resolveTierColor(section.tier.color)
            val totalPacks = section.packs.size
            val completedPacks = section.packs.count { PackUtils.isPackCompleted(it, completedLevels, completedGdIds) }

            binding.tierTitle.text = section.tier.name
            binding.tierSubtitle.text = "${section.packs.size} packs"
            binding.tierProgressText.text = "$completedPacks / $totalPacks packs completed"
            binding.tierProgress.max = totalPacks.coerceAtLeast(1)
            binding.tierProgress.progress = completedPacks
            binding.tierAccent.backgroundTintList = ColorStateList.valueOf(tierColor)
            binding.tierProgress.progressTintList = ColorStateList.valueOf(tierColor)
            binding.tierTitle.setTextColor(tierColor)
            binding.tierSubtitle.setTextColor(tierColor)

            innerAdapter.updateCompletedLevels(completedLevels, completedGdIds)
            innerAdapter.submitList(
                section.packs
                    .sortedWith(compareBy<PackResponse> { PackUtils.packRank(it) }.thenBy { it.name.lowercase() })
                    .map { PackListItem(section.tier, it) }
            )
            binding.recyclerTierPacks.visibility = if (section.packs.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    class TierSectionDiffCallback : DiffUtil.ItemCallback<PackTierSection>() {
        override fun areItemsTheSame(oldItem: PackTierSection, newItem: PackTierSection) = oldItem.tier.id == newItem.tier.id
        override fun areContentsTheSame(oldItem: PackTierSection, newItem: PackTierSection) = oldItem == newItem
    }
}

class PacksAdapter(
    private val onPackClick: (PackListItem) -> Unit
) : ListAdapter<PackListItem, PacksAdapter.PackViewHolder>(PackDiffCallback()) {

    private var completedLevels: Set<String> = emptySet()
    private var completedGdIds: Set<Int> = emptySet()

    fun updateCompletedLevels(completed: Set<String>, gdIds: Set<Int>) {
        completedLevels = completed
        completedGdIds = gdIds
        notifyItemRangeChanged(0, itemCount, "progress")
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PackViewHolder {
        return PackViewHolder(ItemPackBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: PackViewHolder, position: Int) = holder.bind(getItem(position))

    override fun onBindViewHolder(holder: PackViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) super.onBindViewHolder(holder, position, payloads) else holder.bind(getItem(position))
    }

    inner class PackViewHolder(private val binding: ItemPackBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: PackListItem) {
            val tierColor = PackUtils.resolveTierColor(item.tier.color)
            val done = PackUtils.completedCount(item.pack, completedLevels, completedGdIds)
            val total = item.pack.levels.size

            binding.packTier.text = item.tier.name
            binding.packTier.backgroundTintList = ColorStateList.valueOf(tierColor)
            binding.packName.text = item.pack.name
            binding.packProgressText.text = "$done / $total completed"
            binding.packProgress.max = total.coerceAtLeast(1)
            binding.packProgress.progress = done
            binding.packProgress.progressTintList = ColorStateList.valueOf(tierColor)
            binding.packName.setTextColor(tierColor)

            binding.root.setOnClickListener { onPackClick(item) }
        }
    }

    class PackDiffCallback : DiffUtil.ItemCallback<PackListItem>() {
        override fun areItemsTheSame(oldItem: PackListItem, newItem: PackListItem) = oldItem.pack.id == newItem.pack.id
        override fun areContentsTheSame(oldItem: PackListItem, newItem: PackListItem) = oldItem == newItem
    }
}
