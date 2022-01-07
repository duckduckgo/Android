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
import com.duckduckgo.mobile.android.vpn.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

class AppTPCompanyDetailsAdapter() : RecyclerView.Adapter<AppTPCompanyDetailsAdapter.CompanyDetailsViewHolder>() {

    private val items = mutableListOf<AppTPCompanyTrackersViewModel.CompanyTrackingDetails>()

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): CompanyDetailsViewHolder {
        return CompanyDetailsViewHolder.create(parent)
    }

    override fun onBindViewHolder(
        holder: CompanyDetailsViewHolder,
        position: Int
    ) {
        val companyTrackingDetails = items[position]
        holder.bind(companyTrackingDetails)
    }

    override fun getItemCount() = items.size

    suspend fun updateData(data: List<AppTPCompanyTrackersViewModel.CompanyTrackingDetails>) {
        val newData = data
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

    class CompanyDetailsViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        companion object {
            fun create(parent: ViewGroup): CompanyDetailsViewHolder {
                val inflater = LayoutInflater.from(parent.context)
                val view = inflater.inflate(R.layout.item_apptp_company_details, parent, false)
                return CompanyDetailsViewHolder(view)
            }
        }

        var badgeImage: ImageView = view.findViewById(R.id.tracking_company_icon)
        var companyName: TextView = view.findViewById(R.id.tracking_company_name)
        var trackingAttempts: TextView = view.findViewById(R.id.tracking_company_attempts)

        fun bind(trackerInfo: AppTPCompanyTrackersViewModel.CompanyTrackingDetails) {
            val badge = badgeIcon(view.context, trackerInfo.companyName)
            if (badge == null) {
                badgeImage.setImageDrawable(
                    TextDrawable.builder()
                        .beginConfig()
                        .fontSize(50)
                        .endConfig()
                        .buildRound(trackerInfo.companyName.take(1), Color.DKGRAY)
                )
            } else {
                badgeImage.setImageResource(badge)
            }

            companyName.text = trackerInfo.companyDisplayName
            trackingAttempts.text = view.context.resources.getQuantityString(
                R.plurals.atp_CompanyDetailsTrackingAttempts,
                trackerInfo.trackingAttempts,
                trackerInfo.trackingAttempts
            )
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
                .toLowerCase(Locale.ROOT)
            val resource = context.resources.getIdentifier(drawable, "drawable", context.packageName)
            return if (resource != 0) resource else null
        }
    }
}
