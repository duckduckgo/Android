/*
 * Copyright (c) 2022 DuckDuckGo
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
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.quietlySetIsChecked
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.utils.extensions.safeGetApplicationIcon
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.apps.AppsProtectionType
import com.duckduckgo.mobile.android.vpn.apps.AppsProtectionType.AppInfoType
import com.duckduckgo.mobile.android.vpn.apps.AppsProtectionType.FilterType
import com.duckduckgo.mobile.android.vpn.apps.AppsProtectionType.InfoPanelType
import com.duckduckgo.mobile.android.vpn.apps.BannerContent
import com.duckduckgo.mobile.android.vpn.apps.TrackingProtectionAppInfo
import com.duckduckgo.mobile.android.vpn.apps.ViewState
import com.duckduckgo.mobile.android.vpn.databinding.RowExclusionListAppBinding
import com.duckduckgo.mobile.android.vpn.databinding.RowExclusionListFilterBinding
import com.duckduckgo.mobile.android.vpn.databinding.RowExclusionListInfoPanelBinding

class ExclusionListAdapter(val listener: ExclusionListListener) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val PANEL_TYPE = 0
        private const val FILTER_TYPE = 1
        private const val APP_TYPE = 2
        private const val HEADER_ITEMS = 2
    }

    private val exclusionListItems = mutableListOf<AppsProtectionType>()

    fun update(viewState: ViewState) {
        val oldData = exclusionListItems
        val newList = viewState.excludedApps
        val diffResult = DiffCallback(oldData, newList).run { DiffUtil.calculateDiff(this) }
        exclusionListItems.clear().also { exclusionListItems.addAll(newList) }
        diffResult.dispatchUpdatesTo(this)
    }

    override fun getItemCount(): Int {
        return exclusionListItems.size
    }

    override fun getItemViewType(position: Int): Int {
        return when (exclusionListItems[position]) {
            is InfoPanelType -> PANEL_TYPE
            is FilterType -> FILTER_TYPE
            else -> APP_TYPE
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): RecyclerView.ViewHolder {
        return when (viewType) {
            PANEL_TYPE -> {
                InfoPanelViewHolder.create(parent)
            }

            FILTER_TYPE -> {
                FilterViewHolder.create(parent)
            }

            else -> {
                AppViewHolder.create(parent)
            }
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
    ) {
        when (holder) {
            is InfoPanelViewHolder -> {
                val infoPanel = exclusionListItems[position] as InfoPanelType
                holder.bind(infoPanel.bannerContent, listener)
            }

            is FilterViewHolder -> {
                val filterInfo = exclusionListItems[position] as FilterType
                holder.bind(filterInfo.filterResId, exclusionListItems.size - HEADER_ITEMS, listener)
            }

            is AppViewHolder -> {
                val appInfoType = exclusionListItems[position] as AppInfoType
                holder.bind(appInfoType.appInfo, position, listener)
            }
        }
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

    interface ExclusionListListener {
        fun onAppProtectionChanged(
            excludedAppInfo: TrackingProtectionAppInfo,
            enabled: Boolean,
            position: Int,
        )

        fun onLaunchFAQ()
        fun onLaunchFeedback()
        fun onFilterClick(anchorView: View)
    }

    private class InfoPanelViewHolder(val binding: RowExclusionListInfoPanelBinding) : RecyclerView.ViewHolder(binding.root) {
        companion object {
            fun create(parent: ViewGroup): InfoPanelViewHolder {
                val binding = RowExclusionListInfoPanelBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                return InfoPanelViewHolder(binding)
            }
        }

        fun bind(
            bannerContent: BannerContent,
            listener: ExclusionListListener,
        ) {
            when (bannerContent) {
                BannerContent.ALL_OR_PROTECTED_APPS -> binding.excludedAppsEnabledVPNLabel.apply {
                    setImageResource(com.duckduckgo.mobile.android.R.drawable.ic_info_panel_info)
                    setClickableLink(
                        TrackingProtectionExclusionListActivity.LEARN_WHY_ANNOTATION,
                        context.resources.getText(R.string.atp_ExcludedAppsEnabledLearnWhyLabel),
                    ) { listener.onLaunchFAQ() }
                }

                BannerContent.UNPROTECTED_APPS -> binding.excludedAppsEnabledVPNLabel.apply {
                    setImageResource(com.duckduckgo.mobile.android.R.drawable.ic_info_panel_info)
                    setClickableLink(
                        TrackingProtectionExclusionListActivity.LEARN_WHY_ANNOTATION,
                        context.resources.getText(R.string.atp_ExcludedAppsDisabledLearnWhyLabel),
                    ) { listener.onLaunchFAQ() }
                }

                BannerContent.CUSTOMISED_PROTECTION -> binding.excludedAppsEnabledVPNLabel.apply {
                    setImageResource(com.duckduckgo.mobile.android.R.drawable.ic_info_panel_link)
                    setText(context.resources.getString(R.string.atp_ExcludedAppsEnabledLabel))
                }
            }
            binding.excludedAppsEnabledVPNLabel.minimumHeight = 0
            binding.excludedAppsDisabledVPNLabel.apply {
                setClickableLink(
                    TrackingProtectionExclusionListActivity.REPORT_ISSUES_ANNOTATION,
                    context.resources.getText(R.string.atp_ActivityDisabledLabel),
                ) { listener.onLaunchFeedback() }
            }

            binding.excludedAppsEnabledVPNLabel.show()
            binding.excludedAppsDisabledVPNLabel.gone()
        }
    }

    private class FilterViewHolder(val binding: RowExclusionListFilterBinding) : RecyclerView.ViewHolder(binding.root) {
        companion object {
            fun create(parent: ViewGroup): FilterViewHolder {
                val binding = RowExclusionListFilterBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                return FilterViewHolder(binding)
            }
        }

        private val context: Context = binding.root.context

        fun bind(
            filterResId: Int,
            appsFiltered: Int,
            listener: ExclusionListListener,
        ) {
            binding.excludedAppsFilterText.text = context.resources.getString(filterResId, appsFiltered)
            binding.excludedAppsFilterText.setOnClickListener {
                listener.onFilterClick(it)
            }
        }
    }

    private class AppViewHolder(val binding: RowExclusionListAppBinding) : RecyclerView.ViewHolder(binding.root) {
        companion object {
            fun create(parent: ViewGroup): AppViewHolder {
                val binding = RowExclusionListAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                return AppViewHolder(binding)
            }
        }

        private val context: Context = binding.root.context

        fun bind(
            excludedAppInfo: TrackingProtectionAppInfo,
            position: Int,
            listener: ExclusionListListener,
        ) {
            val appIcon = itemView.context.packageManager.safeGetApplicationIcon(excludedAppInfo.packageName)
            binding.deviceShieldAppEntryIcon.setImageDrawable(appIcon)
            binding.deviceShieldAppEntryName.text = excludedAppInfo.name
            binding.handleToggleState(excludedAppInfo.knownProblem)

            if (excludedAppInfo.isProblematic()) {
                if (excludedAppInfo.isExcluded) {
                    binding.deviceShieldAppExclusionReason.text =
                        getAppExcludingReasonText(context, excludedAppInfo.knownProblem)
                    binding.deviceShieldAppExclusionReason.show()
                    binding.deviceShieldAppEntryWarningIcon.setImageDrawable(
                        ContextCompat.getDrawable(
                            context,
                            getAppExcludingReasonIcon(excludedAppInfo.knownProblem),
                        ),
                    )
                    binding.deviceShieldAppEntryWarningIcon.show()
                } else {
                    binding.deviceShieldAppExclusionReason.text = itemView.context.getString(R.string.atp_ExcludedReasonManuallyEnabled)
                    binding.deviceShieldAppExclusionReason.show()
                    binding.deviceShieldAppEntryWarningIcon.setImageDrawable(
                        ContextCompat.getDrawable(
                            context,
                            R.drawable.ic_apptp_link,
                        ),
                    )
                    binding.deviceShieldAppEntryWarningIcon.show()
                }
            } else {
                if (excludedAppInfo.isExcluded) {
                    binding.deviceShieldAppExclusionReason.text = itemView.context.getString(R.string.atp_ExcludedReasonManuallyDisabled)
                    binding.deviceShieldAppExclusionReason.show()
                    binding.deviceShieldAppEntryWarningIcon.setImageDrawable(
                        ContextCompat.getDrawable(
                            context,
                            R.drawable.ic_apptp_link,
                        ),
                    )
                    binding.deviceShieldAppEntryWarningIcon.show()
                } else {
                    binding.deviceShieldAppExclusionReason.gone()
                    binding.deviceShieldAppEntryWarningIcon.gone()
                }
            }

            binding.deviceShieldAppEntryShieldEnabled.quietlySetIsChecked(!excludedAppInfo.isExcluded) { _, enabled ->
                listener.onAppProtectionChanged(excludedAppInfo, enabled, position)
            }
        }

        private fun getAppExcludingReasonText(
            context: Context,
            excludingReason: Int,
        ): String {
            return when (excludingReason) {
                TrackingProtectionAppInfo.LOADS_WEBSITES_EXCLUSION_REASON, TrackingProtectionAppInfo.KNOWN_ISSUES_EXCLUSION_REASON ->
                    context.getString(R.string.atp_ExcludedReasonIssuesMayOccur)

                TrackingProtectionAppInfo.EXCLUDED_THROUGH_NETP -> context.getString(R.string.atp_ExcludedReasonExcludedThroughNetP)
                else -> ""
            }
        }

        private fun getAppExcludingReasonIcon(excludingReason: Int): Int {
            return when (excludingReason) {
                TrackingProtectionAppInfo.KNOWN_ISSUES_EXCLUSION_REASON,
                TrackingProtectionAppInfo.LOADS_WEBSITES_EXCLUSION_REASON,
                TrackingProtectionAppInfo.EXCLUDED_THROUGH_NETP,
                -> R.drawable.ic_apptp_alert

                else -> 0
            }
        }
    }
}
