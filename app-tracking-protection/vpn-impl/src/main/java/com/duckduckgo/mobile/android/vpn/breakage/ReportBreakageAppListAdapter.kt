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
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.common.ui.view.quietlySetIsChecked
import com.duckduckgo.common.utils.extensions.safeGetApplicationIcon
import com.duckduckgo.mobile.android.vpn.databinding.ViewDeviceShieldReportAppBreakageEntryBinding

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
        viewType: Int,
    ): ReportBreakageAppListViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return ReportBreakageAppListViewHolder(binding = ViewDeviceShieldReportAppBreakageEntryBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(
        holder: ReportBreakageAppListViewHolder,
        position: Int,
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
            newItemPosition: Int,
        ): Boolean {
            return oldList[oldItemPosition].packageName == newList[newItemPosition].packageName
        }

        override fun areContentsTheSame(
            oldItemPosition: Int,
            newItemPosition: Int,
        ): Boolean {
            return oldList[oldItemPosition] == newList[newItemPosition]
        }
    }

    interface Listener {
        fun onInstalledAppSelected(
            installedApp: InstalledApp,
            position: Int,
        )
    }
}

class ReportBreakageAppListViewHolder(val binding: ViewDeviceShieldReportAppBreakageEntryBinding) : RecyclerView.ViewHolder(binding.root) {
    fun bind(
        installedApp: InstalledApp,
        position: Int,
        listener: ReportBreakageAppListAdapter.Listener,
    ) {
        binding.deviceShieldInstalledAppEntryName.text = installedApp.name

        binding.deviceShieldInstalledAppSelector.quietlySetIsChecked(installedApp.isSelected) { _, _ ->
            listener.onInstalledAppSelected(installedApp, position)
        }

        // also set the listener in the container view
        binding.root.setOnClickListener {
            listener.onInstalledAppSelected(installedApp, position)
        }

        val appIcon = itemView.context.packageManager.safeGetApplicationIcon(installedApp.packageName)
        binding.deviceShieldInstalledAppEntryIcon.setImageDrawable(appIcon)
    }
}
