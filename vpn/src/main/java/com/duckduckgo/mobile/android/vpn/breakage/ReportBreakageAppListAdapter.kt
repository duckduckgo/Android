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

package com.duckduckgo.mobile.android.vpn.breakage

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.mobile.android.ui.view.quietlySetIsChecked
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.apps.ui.safeGetApplicationIcon
import com.duckduckgo.mobile.android.vpn.ui.notification.applyBoldSpanTo
import kotlinx.android.synthetic.main.view_device_shield_report_app_breakage_entry.view.*

class ReportBreakageAppListAdapter(private val listener: Listener) : RecyclerView.Adapter<ReportBreakageAppListViewHolder>() {

    private val installedApps: MutableList<InstalledApp> = mutableListOf()

    fun update(updatedData: List<InstalledApp>) {
        val oldList = installedApps
        val diffResult = DiffUtil.calculateDiff(DiffCallback(oldList, updatedData))
        installedApps.clear()
        installedApps.addAll(updatedData)
        diffResult.dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ReportBreakageAppListViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val view = inflater.inflate(R.layout.view_device_shield_report_app_breakage_entry, parent, false)
        return ReportBreakageAppListViewHolder(view)
    }

    override fun onBindViewHolder(
        holder: ReportBreakageAppListViewHolder,
        position: Int
    ) {
        holder.bind(installedApps[position], position, listener)
    }

    override fun getItemCount(): Int {
        return installedApps.size
    }

    override fun getItemId(position: Int): Long {
        return installedApps[position].packageName.hashCode().toLong()
    }

    private class DiffCallback(
        private val oldList: List<InstalledApp>,
        private val newList: List<InstalledApp>,
    ) : DiffUtil.Callback() {
        override fun getOldListSize(): Int {
            return oldList.size
        }

        override fun getNewListSize(): Int {
            return newList.size
        }

        override fun areItemsTheSame(
            oldItemPosition: Int,
            newItemPosition: Int
        ): Boolean {
            return oldList[oldItemPosition].packageName == newList[newItemPosition].packageName
        }

        override fun areContentsTheSame(
            oldItemPosition: Int,
            newItemPosition: Int
        ): Boolean {
            return oldList[oldItemPosition] == newList[newItemPosition]
        }
    }

    interface Listener {
        fun onInstalledAppSelected(
            installedApp: InstalledApp,
            position: Int
        )
    }
}

class ReportBreakageAppListViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    fun bind(
        installedApp: InstalledApp,
        position: Int,
        listener: ReportBreakageAppListAdapter.Listener
    ) {
        itemView.deviceShieldInstalledAppEntryName.text =
            String.format(itemView.context.resources.getString(R.string.atp_ReportBreakageAppEntry), installedApp.name)
                .applyBoldSpanTo(listOf(installedApp.name))

        itemView.deviceShieldInstalledAppSelector.quietlySetIsChecked(installedApp.isSelected) { _, _ ->
            listener.onInstalledAppSelected(installedApp, position)
        }

        // also set the listener in the container view
        itemView.setOnClickListener {
            listener.onInstalledAppSelected(installedApp, position)
        }

        val appIcon = itemView.context.packageManager.safeGetApplicationIcon(installedApp.packageName)
        itemView.deviceShieldInstalledAppEntryIcon.setImageDrawable(appIcon)
    }
}
