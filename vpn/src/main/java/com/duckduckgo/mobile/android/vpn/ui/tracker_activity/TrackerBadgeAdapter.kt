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
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.mobile.android.ui.TextDrawable
import com.duckduckgo.mobile.android.ui.view.hide
import com.duckduckgo.mobile.android.ui.view.show
import com.duckduckgo.mobile.android.vpn.databinding.ViewDeviceShieldActivityTrackerBadgeBinding
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.model.TrackerCompanyBadge
import java.util.*

class TrackerBadgeAdapter : RecyclerView.Adapter<TrackerBadgeAdapter.TrackerBadgeViewHolder>() {

    private var trackers = mutableListOf<TrackerCompanyBadge>()

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): TrackerBadgeViewHolder {
        val binding = ViewDeviceShieldActivityTrackerBadgeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TrackerBadgeViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: TrackerBadgeViewHolder,
        position: Int
    ) {
        holder.bind(trackers[position])
    }

    override fun getItemCount() = trackers.size

    fun updateData(newData: List<TrackerCompanyBadge>) {
        val oldData = trackers
        val newData = newData
        val diffResult = DiffCallback(oldData, newData).run { DiffUtil.calculateDiff(this) }

        trackers.clear().also { trackers.addAll(newData) }
        diffResult.dispatchUpdatesTo(this)
    }

    class TrackerBadgeViewHolder(var binding: ViewDeviceShieldActivityTrackerBadgeBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(trackerInfo: TrackerCompanyBadge) {
            when (trackerInfo) {
                is TrackerCompanyBadge.Company -> displayTrackerCompany(trackerInfo)
                is TrackerCompanyBadge.Extra -> displayExtraBadge(trackerInfo)
            }
        }

        private fun displayTrackerCompany(trackerInfo: TrackerCompanyBadge.Company) {
            val badge = badgeIcon(binding.trackerCompanyBadgeIcon.context, trackerInfo.companyName)
            if (badge == null) {
                binding.trackerCompanyBadgeIcon.setImageDrawable(
                    TextDrawable.builder()
                        .beginConfig()
                        .fontSize(50)
                        .endConfig()
                        .buildRound(trackerInfo.companyName.take(1), Color.DKGRAY)
                )
            } else {
                binding.trackerCompanyBadgeIcon.setImageResource(badge)
            }
            binding.trackerCompanyBadgeIcon.show()
            binding.trackerCompanyBadgeText.hide()
            binding.trackerCompanyLargeBadgeText.hide()
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

        private fun displayExtraBadge(trackerInfo: TrackerCompanyBadge.Extra) {
            binding.trackerCompanyBadgeIcon.hide()
            if (trackerInfo.amount > 9) {
                binding.trackerCompanyLargeBadgeText.text = "+${trackerInfo.amount}"
                binding.trackerCompanyLargeBadgeText.show()
                binding.trackerCompanyBadgeText.hide()
            } else {
                binding.trackerCompanyBadgeText.text = "+${trackerInfo.amount}"
                binding.trackerCompanyBadgeText.show()
                binding.trackerCompanyLargeBadgeText.hide()
            }
        }
    }

    private class DiffCallback(
        private val oldList: List<TrackerCompanyBadge>,
        private val newList: List<TrackerCompanyBadge>
    ) : DiffUtil.Callback() {
        override fun getOldListSize() = oldList.size

        override fun getNewListSize() = newList.size

        override fun areItemsTheSame(
            oldItemPosition: Int,
            newItemPosition: Int
        ): Boolean {
            val oldItem = oldList[oldItemPosition]
            val newItem = newList[newItemPosition]
            if (oldItem is TrackerCompanyBadge.Company && newItem is TrackerCompanyBadge.Company) {
                return oldItem.companyName == newItem.companyName
            } else {
                return false
            }
        }

        override fun areContentsTheSame(
            oldItemPosition: Int,
            newItemPosition: Int
        ): Boolean {
            return oldList[oldItemPosition] == newList[newItemPosition]
        }
    }
}
