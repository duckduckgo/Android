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
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.mobile.android.ui.TextDrawable
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.model.TrackerInfo
import java.util.*

class TrackerBadgeAdapter : RecyclerView.Adapter<TrackerBadgeAdapter.TrackerBadgeViewHolder>() {

    private var trackers = mutableListOf<TrackerInfo>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrackerBadgeViewHolder {
        return TrackerBadgeViewHolder.create(parent)
    }

    override fun onBindViewHolder(holder: TrackerBadgeViewHolder, position: Int) {
        holder.bind(trackers[position])
    }

    override fun getItemCount() = trackers.size

    fun updateData(dataUpdate: List<TrackerInfo>) {
        val oldData = trackers
        val newData = dataUpdate
        val diffResult = DiffCallback(oldData, newData).run { DiffUtil.calculateDiff(this) }

        trackers.clear().also { trackers.addAll(newData) }
        diffResult.dispatchUpdatesTo(this)
    }

    class TrackerBadgeViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        companion object {
            fun create(parent: ViewGroup): TrackerBadgeViewHolder {
                val inflater = LayoutInflater.from(parent.context)
                val view = inflater.inflate(R.layout.view_device_shield_activity_tracker_badge, parent, false)
                return TrackerBadgeViewHolder(view)
            }
        }

        fun bind(trackerInfo: TrackerInfo) {
            val badge = badgeIcon(view.context, trackerInfo.companyName)
            if (badge == null) {
                (view as ImageView).setImageDrawable(
                    TextDrawable.builder()
                        .beginConfig()
                        .fontSize(50)
                        .endConfig()
                        .buildRound(trackerInfo.companyName.take(1), Color.DKGRAY)
                )
            } else {
                (view as ImageView).setImageResource(badge)
            }
        }

        private fun badgeIcon(context: Context, networkName: String, prefix: String = "network_logo_"): Int? {
            val drawable = "$prefix$networkName"
                .replace(" ", "_")
                .replace(".", "")
                .replace(",", "")
                .toLowerCase(Locale.ROOT)
            val resource = context.resources.getIdentifier(drawable, "drawable", context.packageName)
            return if (resource != 0) resource else null
        }
    }

    private class DiffCallback(private val old: List<TrackerInfo>, private val new: List<TrackerInfo>) : DiffUtil.Callback() {
        override fun getOldListSize() = old.size

        override fun getNewListSize() = new.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return old[oldItemPosition].companyName == new[newItemPosition].companyName
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return old == new
        }
    }
}
