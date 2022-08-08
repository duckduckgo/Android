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
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.mobile.android.ui.TextDrawable
import com.duckduckgo.mobile.android.ui.view.hide
import com.duckduckgo.mobile.android.ui.view.show
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.model.TrackerCompanyBadge
import java.util.*

class TrackerBadgeAdapter : RecyclerView.Adapter<TrackerBadgeAdapter.TrackerBadgeViewHolder>() {

    private var trackers = mutableListOf<TrackerCompanyBadge>()

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): TrackerBadgeViewHolder {
        return TrackerBadgeViewHolder.create(parent)
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

    class TrackerBadgeViewHolder(view: View) : RecyclerView.ViewHolder(view) {

        var iconBadge: ImageView = view.findViewById(R.id.tracker_company_badge_icon)
        var textBadge: TextView = view.findViewById(R.id.tracker_company_badge_text)
        var largeTextBadge: TextView = view.findViewById(R.id.tracker_company_large_badge_text)

        companion object {
            fun create(parent: ViewGroup): TrackerBadgeViewHolder {
                val inflater = LayoutInflater.from(parent.context)
                val view = inflater.inflate(R.layout.view_device_shield_activity_tracker_badge, parent, false)
                return TrackerBadgeViewHolder(view)
            }
        }

        fun bind(trackerInfo: TrackerCompanyBadge) {
            when (trackerInfo) {
                is TrackerCompanyBadge.Company -> displayTrackerCompany(trackerInfo)
                is TrackerCompanyBadge.Extra -> displayExtraBadge(trackerInfo)
            }

        }

        private fun displayTrackerCompany(trackerInfo: TrackerCompanyBadge.Company) {
            val badge = badgeIcon(iconBadge.context, trackerInfo.companyName)
            if (badge == null) {
                iconBadge.setImageDrawable(
                    TextDrawable.builder()
                        .beginConfig()
                        .fontSize(50)
                        .endConfig()
                        .buildRound(trackerInfo.companyName.take(1), Color.DKGRAY)
                )
            } else {
                iconBadge.setImageResource(badge)
            }
            iconBadge.show()
            textBadge.hide()
            largeTextBadge.hide()
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
            iconBadge.hide()
            if (trackerInfo.amount > 9) {
                largeTextBadge.text = "+${trackerInfo.amount}"
                largeTextBadge.show()
                textBadge.hide()
            } else {
                textBadge.text = "+${trackerInfo.amount}"
                textBadge.show()
                largeTextBadge.hide()
            }
        }
    }

    private class DiffCallback(private val oldList: List<TrackerCompanyBadge>, private val newList: List<TrackerCompanyBadge>) : DiffUtil.Callback() {
        override fun getOldListSize() = oldList.size

        override fun getNewListSize() = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldItem = oldList[oldItemPosition]
            val newItem = newList[newItemPosition]
            if (oldItem is TrackerCompanyBadge.Company && newItem is TrackerCompanyBadge.Company) {
                return oldItem.companyName == newItem.companyName
            } else {
                return false
            }
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition] == newList[newItemPosition]
        }
    }
}
