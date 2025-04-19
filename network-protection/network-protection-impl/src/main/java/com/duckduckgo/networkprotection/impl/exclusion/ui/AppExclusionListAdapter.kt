/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.networkprotection.impl.exclusion.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.common.ui.view.divider.HorizontalDivider
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.utils.extensions.safeGetApplicationIcon
import com.duckduckgo.networkprotection.impl.R
import com.duckduckgo.networkprotection.impl.databinding.ItemExclusionListAppBinding
import com.duckduckgo.networkprotection.impl.databinding.ItemExclusionListFilterBinding
import com.duckduckgo.networkprotection.impl.databinding.ItemExclusionListHeaderBinding
import com.duckduckgo.networkprotection.impl.databinding.ItemExclusionListSystemappCategoryBinding
import com.duckduckgo.networkprotection.impl.databinding.ItemExclusionListSystemappHeaderBinding
import com.duckduckgo.networkprotection.impl.exclusion.ui.AppsProtectionType.AppType
import com.duckduckgo.networkprotection.impl.exclusion.ui.AppsProtectionType.DividerType
import com.duckduckgo.networkprotection.impl.exclusion.ui.AppsProtectionType.FilterType
import com.duckduckgo.networkprotection.impl.exclusion.ui.AppsProtectionType.HeaderType
import com.duckduckgo.networkprotection.impl.exclusion.ui.AppsProtectionType.SystemAppCategoryType
import com.duckduckgo.networkprotection.impl.exclusion.ui.AppsProtectionType.SystemAppHeaderType
import com.duckduckgo.networkprotection.impl.exclusion.ui.HeaderContent.Default
import com.duckduckgo.networkprotection.impl.exclusion.ui.HeaderContent.NetpDisabled
import com.duckduckgo.networkprotection.impl.exclusion.ui.HeaderContent.WithToggle

class AppExclusionListAdapter(val listener: ExclusionListListener) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val HEADER_TYPE = 0
        private const val FILTER_TYPE = 1
        private const val APP_TYPE = 2
        private const val SYSTEM_APP_HEADER_TYPE = 3
        private const val SYSTEM_APP_CATEGORY_TYPE = 4
        private const val DIVIDER_TYPE = 5
        private const val DEFAULT_PRE_APPS_ITEMS = 4
    }

    private val exclusionListItems = mutableListOf<AppsProtectionType>()

    fun update(viewState: ViewState) {
        val oldData = exclusionListItems
        val newList = viewState.apps
        val diffResult = DiffCallback(oldData, newList).run { DiffUtil.calculateDiff(this) }
        exclusionListItems.clear().also { exclusionListItems.addAll(newList) }
        diffResult.dispatchUpdatesTo(this)
    }

    override fun getItemCount(): Int {
        return exclusionListItems.size
    }

    override fun getItemViewType(position: Int): Int = when (exclusionListItems[position]) {
        is HeaderType -> HEADER_TYPE
        is FilterType -> FILTER_TYPE
        is SystemAppHeaderType -> SYSTEM_APP_HEADER_TYPE
        is SystemAppCategoryType -> SYSTEM_APP_CATEGORY_TYPE
        is DividerType -> DIVIDER_TYPE
        else -> APP_TYPE
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): RecyclerView.ViewHolder = when (viewType) {
        HEADER_TYPE -> HeaderViewHolder.create(parent)
        FILTER_TYPE -> FilterViewHolder.create(parent)
        SYSTEM_APP_HEADER_TYPE -> SystemAppHeaderHolder.create(parent)
        SYSTEM_APP_CATEGORY_TYPE -> SystemAppCategoryHolder.create(parent)
        DIVIDER_TYPE -> DividerHolder(HorizontalDivider(parent.context))
        else -> AppViewHolder.create(parent)
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
    ) {
        when (holder) {
            is HeaderViewHolder -> {
                val header = exclusionListItems[position] as HeaderType
                holder.bind(header.headerContent, listener)
            }

            is FilterViewHolder -> {
                val filterInfo = exclusionListItems[position] as FilterType
                holder.bind(filterInfo.filterResId, exclusionListItems.size - DEFAULT_PRE_APPS_ITEMS, listener)
            }

            is AppViewHolder -> {
                val appInfoType = exclusionListItems[position] as AppType
                holder.bind(appInfoType.appInfo, position, listener)
            }

            is SystemAppCategoryHolder -> {
                val appInfoType = exclusionListItems[position] as SystemAppCategoryType
                holder.bind(appInfoType.category, position, listener)
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
            app: NetpExclusionListApp,
            enabled: Boolean,
            position: Int,
        )

        fun onSystemAppCategoryStateChanged(
            category: NetpExclusionListSystemAppCategory,
            enabled: Boolean,
            position: Int,
        )

        fun onFilterClick(anchorView: View)

        fun onHeaderToggleClicked(enabled: Boolean)
    }

    private class HeaderViewHolder(val binding: ItemExclusionListHeaderBinding) : RecyclerView.ViewHolder(binding.root) {
        companion object {
            fun create(parent: ViewGroup): HeaderViewHolder {
                val binding = ItemExclusionListHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                return HeaderViewHolder(binding)
            }
        }

        fun bind(
            headerContent: HeaderContent,
            listener: ExclusionListListener,
        ) {
            when (headerContent) {
                Default -> {
                    binding.exclusionItemHeaderText.apply {
                        setText(R.string.netpExclusionListHeaderDefault)
                    }

                    binding.exclusionItemHeaderText.show()
                    binding.sectionAutoExclude.root.gone()
                    binding.exclusionItemHeaderBanner.gone()
                }

                NetpDisabled -> {
                    binding.exclusionItemHeaderBanner.apply {
                        setText(context.getString(R.string.netpExclusionListHeaderDisabled))
                    }

                    binding.exclusionItemHeaderText.gone()
                    binding.sectionAutoExclude.root.gone()
                    binding.exclusionItemHeaderBanner.show()
                }

                is WithToggle -> {
                    binding.sectionAutoExclude.autoExcludeToggle.quietlySetIsChecked(headerContent.enabled) { _, enabled ->
                        listener.onHeaderToggleClicked(enabled)
                    }
                    binding.sectionAutoExclude.root.show()
                    binding.exclusionItemHeaderText.gone()
                    binding.exclusionItemHeaderBanner.gone()
                }
            }
        }
    }

    private class FilterViewHolder(val binding: ItemExclusionListFilterBinding) : RecyclerView.ViewHolder(binding.root) {
        companion object {
            fun create(parent: ViewGroup): FilterViewHolder {
                val binding = ItemExclusionListFilterBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                return FilterViewHolder(binding)
            }
        }

        private val context: Context = binding.root.context

        fun bind(
            filterResId: Int,
            appsFiltered: Int,
            listener: ExclusionListListener,
        ) {
            binding.exclusionItemFilterText.text = context.resources.getString(filterResId, appsFiltered)
            binding.exclusionItemFilterText.setOnClickListener {
                listener.onFilterClick(it)
            }
        }
    }

    private class AppViewHolder(val binding: ItemExclusionListAppBinding) : RecyclerView.ViewHolder(binding.root) {
        companion object {
            fun create(parent: ViewGroup): AppViewHolder {
                val binding = ItemExclusionListAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                return AppViewHolder(binding)
            }
        }

        fun bind(
            app: NetpExclusionListApp,
            position: Int,
            listener: ExclusionListListener,
        ) {
            val appIcon = itemView.context.packageManager.safeGetApplicationIcon(app.packageName)

            if (app.isNotCompatibleWithVPN) {
                binding.basicApp.gone()
                binding.incompatibleApp.root.show()

                binding.incompatibleApp.apply {
                    appIcon?.let { incompatibleAppIcon.setImageDrawable(it) }

                    incompatibleAppInfo.setPrimaryText(app.name)

                    incompatibleAppInfo.quietlySetIsChecked(app.isProtected) { _, enabled ->
                        listener.onAppProtectionChanged(app, enabled, position)
                    }
                    incompatibleAppInfo.setSecondaryText(this.root.context.getString(R.string.netpExclusionListLabelAutoExclude))
                }
            } else {
                binding.basicApp.show()
                binding.incompatibleApp.root.gone()

                binding.basicApp.apply {
                    appIcon?.let { setLeadingIconDrawable(it) }

                    setPrimaryText(app.name)

                    quietlySetIsChecked(app.isProtected) { _, enabled ->
                        listener.onAppProtectionChanged(app, enabled, position)
                    }
                }
            }
        }
    }

    private class SystemAppCategoryHolder(val binding: ItemExclusionListSystemappCategoryBinding) : RecyclerView.ViewHolder(binding.root) {
        companion object {
            fun create(parent: ViewGroup): SystemAppCategoryHolder {
                val binding = ItemExclusionListSystemappCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                return SystemAppCategoryHolder(binding)
            }
        }

        fun bind(
            category: NetpExclusionListSystemAppCategory,
            position: Int,
            listener: ExclusionListListener,
        ) {
            binding.root.apply {
                setPrimaryText(category.text)

                quietlySetIsChecked(category.isEnabled) { _, enabled ->
                    listener.onSystemAppCategoryStateChanged(category, enabled, position)
                }
            }
        }
    }

    private class SystemAppHeaderHolder(binding: ItemExclusionListSystemappHeaderBinding) : RecyclerView.ViewHolder(binding.root) {
        companion object {
            fun create(parent: ViewGroup): SystemAppHeaderHolder {
                val binding = ItemExclusionListSystemappHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                return SystemAppHeaderHolder(binding)
            }
        }
    }

    private class DividerHolder(view: View) : RecyclerView.ViewHolder(view)
}
