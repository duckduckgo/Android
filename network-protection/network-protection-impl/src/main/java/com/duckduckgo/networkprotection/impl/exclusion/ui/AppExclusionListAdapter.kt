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
import com.duckduckgo.app.global.extensions.safeGetApplicationIcon
import com.duckduckgo.mobile.android.ui.view.gone
import com.duckduckgo.mobile.android.ui.view.show
import com.duckduckgo.networkprotection.impl.R
import com.duckduckgo.networkprotection.impl.databinding.ItemExclusionListAppBinding
import com.duckduckgo.networkprotection.impl.databinding.ItemExclusionListFilterBinding
import com.duckduckgo.networkprotection.impl.databinding.ItemExclusionListHeaderBinding
import com.duckduckgo.networkprotection.impl.exclusion.ui.AppsProtectionType.AppType
import com.duckduckgo.networkprotection.impl.exclusion.ui.AppsProtectionType.FilterType
import com.duckduckgo.networkprotection.impl.exclusion.ui.AppsProtectionType.HeaderType

class AppExclusionListAdapter(val listener: ExclusionListListener) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val HEADER_TYPE = 0
        private const val FILTER_TYPE = 1
        private const val APP_TYPE = 2
        private const val HEADER_ITEMS = 3
    }

    private var isListEnabled: Boolean = false
    private val exclusionListItems = mutableListOf<AppsProtectionType>()

    fun update(
        viewState: ViewState,
        isListStateEnabled: Boolean = true,
    ) {
        isListEnabled = isListStateEnabled
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
        else -> APP_TYPE
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): RecyclerView.ViewHolder = when (viewType) {
        HEADER_TYPE -> HeaderViewHolder.create(parent)
        FILTER_TYPE -> FilterViewHolder.create(parent)
        else -> AppViewHolder.create(parent)
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
    ) {
        when (holder) {
            is HeaderViewHolder -> {
                val header = exclusionListItems[position] as HeaderType
                holder.bind(header.headerContent, isListEnabled)
            }

            is FilterViewHolder -> {
                val filterInfo = exclusionListItems[position] as FilterType
                holder.bind(filterInfo.filterResId, exclusionListItems.size - HEADER_ITEMS, listener)
            }

            is AppViewHolder -> {
                val appInfoType = exclusionListItems[position] as AppType
                holder.bind(isListEnabled, appInfoType.appInfo, position, listener)
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

        fun onFilterClick(anchorView: View)
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
            isListEnabled: Boolean,
        ) {
            when (headerContent) {
                HeaderContent.DEFAULT -> binding.exclusionItemHeaderText.apply {
                    setText(R.string.netpExclusionListHeaderDefault)
                }

                HeaderContent.NETP_DISABLED -> binding.exclusionItemHeaderBanner.apply {
                    setText(context.getString(R.string.netpExclusionListHeaderDisabled))
                }
            }

            if (isListEnabled) {
                binding.exclusionItemHeaderText.show()
                binding.exclusionItemHeaderBanner.gone()
            } else {
                binding.exclusionItemHeaderBanner.gone()
                binding.exclusionItemHeaderText.show()
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
            isListEnabled: Boolean,
            app: NetpExclusionListApp,
            position: Int,
            listener: ExclusionListListener,
        ) {
            val appIcon = itemView.context.packageManager.safeGetApplicationIcon(app.packageName)

            binding.exclusionItem.apply {
                appIcon?.let { setLeadingIconDrawable(it) }

                setPrimaryText(app.name)

                if (isListEnabled) {
                    quietlySetIsChecked(app.isProtected) { _, enabled ->
                        listener.onAppProtectionChanged(app, enabled, position)
                    }
                } else {
                    isClickable = false
                    isEnabled = false
                    quietlySetIsChecked(app.isProtected, null)
                }
            }
        }
    }
}
