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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.app.global.extensions.safeGetApplicationIcon
import com.duckduckgo.mobile.android.ui.view.gone
import com.duckduckgo.mobile.android.ui.view.quietlySetIsChecked
import com.duckduckgo.mobile.android.ui.view.show
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.apps.AppsProtectionType
import com.duckduckgo.mobile.android.vpn.apps.AppsProtectionType.AppInfoType
import com.duckduckgo.mobile.android.vpn.apps.TrackingProtectionAppInfo
import kotlinx.android.synthetic.main.row_exclusion_list_app.view.*

class TrackingProtectionAppsAdapter(val listener: AppProtectionListener) :
    RecyclerView.Adapter<TrackingProtectionAppViewHolder>() {

    private var isListEnabled: Boolean = false
    private val excludedApps: MutableList<AppsProtectionType> = mutableListOf()

    fun update(
        newList: List<AppsProtectionType>,
        isListStateEnabled: Boolean = true,
    ) {
        isListEnabled = isListStateEnabled
        val oldData = excludedApps
        val diffResult = DiffCallback(oldData, newList).run { DiffUtil.calculateDiff(this) }
        excludedApps.clear().also { excludedApps.addAll(newList) }
        diffResult.dispatchUpdatesTo(this)
    }

    override fun getItemId(position: Int): Long {
        val appInfo = excludedApps[position] as AppInfoType
        return appInfo.appInfo.packageName.hashCode().toLong()
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): TrackingProtectionAppViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val view = inflater.inflate(R.layout.row_exclusion_list_app, parent, false)
        return TrackingProtectionAppViewHolder(view)
    }

    override fun onBindViewHolder(
        holder: TrackingProtectionAppViewHolder,
        position: Int,
    ) {
        val type = excludedApps[position] as AppInfoType
        holder.bind(isListEnabled, type.appInfo, position, listener)
    }

    override fun getItemCount(): Int {
        return excludedApps.size
    }

    private class DiffCallback(
        private val oldList: List<AppsProtectionType>,
        private val newList: List<AppsProtectionType>,
    ) :
        DiffUtil.Callback() {
        override fun getOldListSize() = oldList.size

        override fun getNewListSize() = newList.size

        override fun areItemsTheSame(
            oldItemPosition: Int,
            newItemPosition: Int,
        ): Boolean {
            return oldList[oldItemPosition] == newList[newItemPosition]
        }

        override fun areContentsTheSame(
            oldItemPosition: Int,
            newItemPosition: Int,
        ): Boolean {
            return oldList[oldItemPosition] == newList[newItemPosition]
        }
    }
}

interface AppProtectionListener {
    fun onAppProtectionChanged(
        excludedAppInfo: TrackingProtectionAppInfo,
        enabled: Boolean,
        position: Int,
    )
}

class TrackingProtectionAppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val context = itemView.context

    fun bind(
        isListEnabled: Boolean,
        excludedAppInfo: TrackingProtectionAppInfo,
        position: Int,
        listener: AppProtectionListener,
    ) {
        val appIcon = itemView.context.packageManager.safeGetApplicationIcon(excludedAppInfo.packageName)
        itemView.deviceShieldAppEntryIcon.setImageDrawable(appIcon)
        itemView.deviceShieldAppEntryName.text = excludedAppInfo.name

        if (excludedAppInfo.isProblematic()) {
            if (excludedAppInfo.isExcluded) {
                itemView.deviceShieldAppExclusionReason.text =
                    getAppExcludingReasonText(itemView.context, excludedAppInfo.knownProblem)
                itemView.deviceShieldAppExclusionReason.show()
                itemView.deviceShieldAppEntryWarningIcon.setImageDrawable(
                    ContextCompat.getDrawable(
                        context,
                        getAppExcludingReasonIcon(excludedAppInfo.knownProblem),
                    ),
                )
                itemView.deviceShieldAppEntryWarningIcon.show()
            } else {
                itemView.deviceShieldAppExclusionReason.text = itemView.context.getString(R.string.atp_ExcludedReasonManuallyEnabled)
                itemView.deviceShieldAppExclusionReason.show()
                itemView.deviceShieldAppEntryWarningIcon.setImageDrawable(
                    ContextCompat.getDrawable(
                        context,
                        R.drawable.ic_apptp_link,
                    ),
                )
                itemView.deviceShieldAppEntryWarningIcon.show()
            }
        } else {
            if (excludedAppInfo.isExcluded) {
                itemView.deviceShieldAppExclusionReason.text = itemView.context.getString(R.string.atp_ExcludedReasonManuallyDisabled)
                itemView.deviceShieldAppExclusionReason.show()
                itemView.deviceShieldAppEntryWarningIcon.setImageDrawable(
                    ContextCompat.getDrawable(
                        context,
                        R.drawable.ic_apptp_link,
                    ),
                )
                itemView.deviceShieldAppEntryWarningIcon.show()
            } else {
                itemView.deviceShieldAppExclusionReason.gone()
                itemView.deviceShieldAppEntryWarningIcon.gone()
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
        excludingReason: Int,
    ): String {
        return when (excludingReason) {
            TrackingProtectionAppInfo.LOADS_WEBSITES_EXCLUSION_REASON, TrackingProtectionAppInfo.KNOWN_ISSUES_EXCLUSION_REASON ->
                context.getString(R.string.atp_ExcludedReasonIssuesMayOccur)
            else -> ""
        }
    }

    private fun getAppExcludingReasonIcon(excludingReason: Int): Int {
        return when (excludingReason) {
            TrackingProtectionAppInfo.KNOWN_ISSUES_EXCLUSION_REASON -> R.drawable.ic_apptp_alert
            TrackingProtectionAppInfo.LOADS_WEBSITES_EXCLUSION_REASON -> R.drawable.ic_apptp_alert
            else -> 0
        }
    }
}
