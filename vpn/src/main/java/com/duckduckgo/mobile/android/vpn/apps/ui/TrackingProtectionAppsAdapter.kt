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

package com.duckduckgo.mobile.android.vpn.apps.ui

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.mobile.android.ui.view.gone
import com.duckduckgo.mobile.android.ui.view.leftDrawable
import com.duckduckgo.mobile.android.ui.view.quietlySetIsChecked
import com.duckduckgo.mobile.android.ui.view.show
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.apps.TrackingProtectionAppInfo
import com.duckduckgo.mobile.android.vpn.ui.notification.applyBoldSpanTo
import kotlinx.android.synthetic.main.view_device_shield_excluded_app_entry.view.*

class TrackingProtectionAppsAdapter(val listener: AppProtectionListener) :
    RecyclerView.Adapter<TrackingProtectionAppViewHolder>() {

    private var isListEnabled: Boolean = false
    private val excludedApps: MutableList<TrackingProtectionAppInfo> = mutableListOf()

    fun update(
        newList: List<TrackingProtectionAppInfo>,
        isListStateEnabled: Boolean
    ) {
        isListEnabled = isListStateEnabled
        val oldData = excludedApps
        val diffResult = DiffCallback(oldData, newList).run { DiffUtil.calculateDiff(this) }
        excludedApps.clear().also { excludedApps.addAll(newList) }
        diffResult.dispatchUpdatesTo(this)
    }

    override fun getItemId(position: Int): Long {
        return excludedApps[position].packageName.hashCode().toLong()
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): TrackingProtectionAppViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val view = inflater.inflate(R.layout.view_device_shield_excluded_app_entry, parent, false)
        return TrackingProtectionAppViewHolder(view)
    }

    override fun onBindViewHolder(
        holder: TrackingProtectionAppViewHolder,
        position: Int
    ) {
        val excludedAppInfo = excludedApps[position]
        holder.bind(isListEnabled, excludedAppInfo, position, listener)
    }

    override fun getItemCount(): Int {
        return excludedApps.size
    }

    fun updateSwitchPosition(position: Int) {
        notifyItemChanged(position)
    }

    private class DiffCallback(
        private val oldList: List<TrackingProtectionAppInfo>,
        private val newList: List<TrackingProtectionAppInfo>
    ) :
        DiffUtil.Callback() {
        override fun getOldListSize() = oldList.size

        override fun getNewListSize() = newList.size

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
}

interface AppProtectionListener {
    fun onAppProtectionChanged(
        excludedAppInfo: TrackingProtectionAppInfo,
        enabled: Boolean,
        position: Int
    )
}

class TrackingProtectionAppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    fun bind(
        isListEnabled: Boolean,
        excludedAppInfo: TrackingProtectionAppInfo,
        position: Int,
        listener: AppProtectionListener
    ) {
        val appIcon = itemView.context.packageManager.safeGetApplicationIcon(excludedAppInfo.packageName)
        itemView.deviceShieldAppEntryIcon.setImageDrawable(appIcon)
        itemView.deviceShieldAppEntryName.text =
            String.format(itemView.context.resources.getString(R.string.atp_ExcludedAppEntry), excludedAppInfo.name)
                .applyBoldSpanTo(listOf(excludedAppInfo.name!!))

        if (excludedAppInfo.isProblematic()) {
            if (excludedAppInfo.isExcluded) {
                itemView.deviceShieldAppExclusionReason.text =
                    getAppExcludingReasonText(itemView.context, excludedAppInfo.knownProblem)
                itemView.deviceShieldAppExclusionReason.leftDrawable(getAppExcludingReasonIcon(excludedAppInfo.knownProblem))
                itemView.deviceShieldAppExclusionReason.show()
            } else {
                itemView.deviceShieldAppExclusionReason.text = itemView.context.getString(R.string.atp_ExcludedReasonManuallyEnabled)
                itemView.deviceShieldAppExclusionReason.leftDrawable(com.duckduckgo.mobile.android.R.drawable.ic_link_blue_16)
                itemView.deviceShieldAppExclusionReason.show()
            }
        } else {
            if (excludedAppInfo.isExcluded) {
                itemView.deviceShieldAppExclusionReason.text = itemView.context.getString(R.string.atp_ExcludedReasonManuallyDisabled)
                itemView.deviceShieldAppExclusionReason.leftDrawable(com.duckduckgo.mobile.android.R.drawable.ic_link_blue_16)
                itemView.deviceShieldAppExclusionReason.show()
            } else {
                itemView.deviceShieldAppExclusionReason.gone()
            }
        }

        if (isListEnabled) {
            itemView.deviceShieldAppEntryShieldEnabled.quietlySetIsChecked(!excludedAppInfo.isExcluded) { _, enabled ->
                listener.onAppProtectionChanged(excludedAppInfo, enabled, position)
            }
        } else {
            itemView.deviceShieldAppEntryShieldEnabled.isClickable = false
            itemView.deviceShieldAppEntryShieldEnabled.quietlySetIsChecked(!excludedAppInfo.isExcluded, null)
        }
    }

    private fun getAppExcludingReasonText(
        context: Context,
        excludingReason: Int
    ): String {
        return when (excludingReason) {
            TrackingProtectionAppInfo.LOADS_WEBSITES_EXCLUSION_REASON -> context.getString(R.string.atp_ExcludedReasonLoadsWebsites)
            TrackingProtectionAppInfo.KNOWN_ISSUES_EXCLUSION_REASON -> context.getString(R.string.atp_ExcludedReasonKnownIssues)
            else -> ""
        }
    }

    private fun getAppExcludingReasonIcon(excludingReason: Int): Int {
        return when (excludingReason) {
            TrackingProtectionAppInfo.KNOWN_ISSUES_EXCLUSION_REASON -> com.duckduckgo.mobile.android.R.drawable.ic_alert_yellow_16
            TrackingProtectionAppInfo.LOADS_WEBSITES_EXCLUSION_REASON -> com.duckduckgo.mobile.android.R.drawable.ic_alert_yellow_16
            else -> 0
        }
    }
}

fun PackageManager.safeGetApplicationIcon(packageName: String): Drawable? {
    return runCatching {
        getApplicationIcon(packageName)
    }.getOrNull()
}
