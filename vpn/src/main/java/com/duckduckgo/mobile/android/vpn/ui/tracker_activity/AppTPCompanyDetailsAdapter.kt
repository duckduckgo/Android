/*
 * Copyright (c) 2021 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.mobile.android.vpn.ui.tracker_activity

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.mobile.android.ui.TextDrawable
import com.duckduckgo.mobile.android.ui.view.Chip
import com.duckduckgo.mobile.android.ui.view.gone
import com.duckduckgo.mobile.android.ui.view.show
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.databinding.ItemApptpCompanyDetailsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

class AppTPCompanyDetailsAdapter : RecyclerView.Adapter<AppTPCompanyDetailsAdapter.CompanyDetailsViewHolder>() {

    private val items = mutableListOf<AppTPCompanyTrackersViewModel.CompanyTrackingDetails>()

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): CompanyDetailsViewHolder {
        val binding = ItemApptpCompanyDetailsBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CompanyDetailsViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: CompanyDetailsViewHolder,
        position: Int
    ) {
        val companyTrackingDetails = items[position]
        holder.bind(companyTrackingDetails) { expanded ->
            items.forEachIndexed { index, companyDetails ->
                companyDetails.takeIf { it.companyName == companyTrackingDetails.companyName }?.let {
                    items[index] = it.copy(expanded = expanded)
                }
            }
        }
    }

    override fun getItemCount() = items.size

    suspend fun updateData(data: List<AppTPCompanyTrackersViewModel.CompanyTrackingDetails>) {
        val newData = mutableListOf<AppTPCompanyTrackersViewModel.CompanyTrackingDetails>()
        data.forEach { updateDataItem ->
            val existingItem = items.find { it.companyName == updateDataItem.companyName }
            val itemToAdd = if (existingItem != null) {
                existingItem.copy(trackingAttempts = updateDataItem.trackingAttempts)
            } else {
                updateDataItem
            }

            newData.add(itemToAdd)
        }
        val oldData = items
        val diffResult = withContext(Dispatchers.IO) {
            DiffCallback(oldData, newData).run { DiffUtil.calculateDiff(this) }
        }

        items.clear().also { items.addAll(newData) }

        diffResult.dispatchUpdatesTo(this@AppTPCompanyDetailsAdapter)
    }

    private class DiffCallback(
        private val old: List<AppTPCompanyTrackersViewModel.CompanyTrackingDetails>,
        private val new: List<AppTPCompanyTrackersViewModel.CompanyTrackingDetails>
    ) :
        DiffUtil.Callback() {
        override fun getOldListSize() = old.size

        override fun getNewListSize() = new.size

        override fun areItemsTheSame(
            oldItemPosition: Int,
            newItemPosition: Int
        ): Boolean {
            return old[oldItemPosition].companyName == new[newItemPosition].companyName
        }

        override fun areContentsTheSame(
            oldItemPosition: Int,
            newItemPosition: Int
        ): Boolean {
            return old[oldItemPosition] == new[newItemPosition]
        }
    }

    class CompanyDetailsViewHolder(val binding: ItemApptpCompanyDetailsBinding) : RecyclerView.ViewHolder(binding.root) {
        companion object {
            const val TOP_SIGNALS = 5
        }
        fun bind(
            companyDetails: AppTPCompanyTrackersViewModel.CompanyTrackingDetails,
            onExpanded: (Boolean) -> Unit
        ) {
            val badge = badgeIcon(binding.root.context, companyDetails.companyName)
            if (badge == null) {
                binding.trackingCompanyIcon.setImageDrawable(
                    TextDrawable.builder()
                        .beginConfig()
                        .fontSize(50)
                        .endConfig()
                        .buildRound(companyDetails.companyName.take(1), Color.DKGRAY)
                )
            } else {
                binding.trackingCompanyIcon.setImageResource(badge)
            }

            binding.trackingCompanyName.text = companyDetails.companyDisplayName
            binding.trackingCompanyAttempts.text = binding.root.context.resources.getQuantityString(
                R.plurals.atp_CompanyDetailsTrackingAttempts,
                companyDetails.trackingAttempts, companyDetails.trackingAttempts
            )

            val inflater = LayoutInflater.from(binding.root.context)
            binding.trackingCompanyTopSignals.removeAllViews()
            binding.trackingCompanyBottomSignals.removeAllViews()

            val topSignals = companyDetails.trackingSignals.take(TOP_SIGNALS)
            val bottomSignals = companyDetails.trackingSignals.drop(TOP_SIGNALS)

            topSignals.forEach {
                val topSignal = inflater.inflate(com.duckduckgo.mobile.android.R.layout.view_chip, binding.trackingCompanyTopSignals, false) as Chip
                topSignal.setChipText(it.signalDisplayName)
                topSignal.setChipIcon(it.signalIcon)
                binding.trackingCompanyTopSignals.addView(topSignal)
            }

            if (bottomSignals.isNotEmpty()) {
                bottomSignals.forEach {
                    val bottomSignal =
                        inflater.inflate(com.duckduckgo.mobile.android.R.layout.view_chip, binding.trackingCompanyBottomSignals, false) as Chip
                    bottomSignal.setChipText(it.signalDisplayName)
                    bottomSignal.setChipIcon(it.signalIcon)
                    binding.trackingCompanyBottomSignals.addView(bottomSignal)
                }
                binding.trackingCompanyShowMore.show()
            } else {
                binding.trackingCompanyShowMore.gone()
            }

            binding.trackingCompanyShowMore.text =
                String.format(binding.root.context.getString(R.string.atp_CompanyDetailsTrackingShowMore, bottomSignals.size))
            binding.trackingCompanyShowMore.setOnClickListener {
                if (!binding.trackingCompanyBottomSignals.isVisible) {
                    showMore()
                    onExpanded.invoke(true)
                }
            }
            binding.trackingCompanyShowLess.setOnClickListener {
                showLess()
                onExpanded.invoke(false)
            }
            if (companyDetails.expanded) {
                showMore()
            } else {
                showLess()
            }
        }

        private fun showMore() {
            binding.trackingCompanyBottomSignals.show()
            binding.trackingCompanyShowMore.gone()
            binding.trackingCompanyShowLess.show()
        }

        private fun showLess() {
            binding.trackingCompanyBottomSignals.gone()
            binding.trackingCompanyShowMore.show()
            binding.trackingCompanyShowLess.show()
        }

        private fun badgeIcon(
            context: Context,
            networkName: String,
            prefix: String = "tracking_network_logo_"
        ): Int? {
            val drawable = "$prefix$networkName"
                .replace(" ", "_")
                .replace(".", "")
                .replace(",", "")
                .lowercase(Locale.ROOT)
            val resource = context.resources.getIdentifier(drawable, "drawable", context.packageName)
            return if (resource != 0) resource else null
        }
    }
}





