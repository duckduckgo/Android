/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.app.fire.fireproofwebsite.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ViewFireproofTitleBinding
import com.duckduckgo.app.browser.databinding.ViewFireproofWebsiteDescriptionBinding
import com.duckduckgo.app.browser.databinding.ViewFireproofWebsiteEmptyHintBinding
import com.duckduckgo.app.browser.databinding.ViewFireproofWebsiteSettingsSelectionBinding
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteEntity
import com.duckduckgo.app.fire.fireproofwebsite.data.website
import com.duckduckgo.app.fire.fireproofwebsite.ui.FireproofWebsitesViewModel.Command.ShowAutomaticFireproofSettingSelectionDialog
import com.duckduckgo.common.ui.menu.PopupMenu
import com.duckduckgo.mobile.android.databinding.RowOneLineListItemBinding
import kotlinx.coroutines.launch
import logcat.LogPriority.INFO
import logcat.logcat

class FireproofWebsiteAdapter(
    private val viewModel: FireproofWebsitesViewModel,
    private val lifecycleOwner: LifecycleOwner,
    private val faviconManager: FaviconManager,
) : RecyclerView.Adapter<FireproofWebSiteViewHolder>() {

    companion object {
        const val FIREPROOF_WEBSITE_TYPE = 0
        const val DESCRIPTION_TYPE = 1
        const val EMPTY_STATE_TYPE = 2
        const val SETTING_SELECTION_TYPE = 3
        const val SECTION_TITLE_TYPE = 4

        const val EMPTY_HINT_ITEM_SIZE = 1
    }

    private val sortedHeaderElements = listOf(DESCRIPTION_TYPE, SETTING_SELECTION_TYPE, SECTION_TITLE_TYPE)

    var fireproofWebsites: List<FireproofWebsiteEntity> = emptyList()
        set(value) {
            if (field != value) {
                field = value
                notifyDataSetChanged()
            }
        }

    var automaticFireproofSetting: AutomaticFireproofSetting = AutomaticFireproofSetting.NEVER
        set(value) {
            if (field != value) {
                field = value
                notifyDataSetChanged()
            }
        }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): FireproofWebSiteViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            DESCRIPTION_TYPE -> {
                val binding = ViewFireproofWebsiteDescriptionBinding.inflate(inflater, parent, false)
                FireproofWebSiteViewHolder.FireproofWebsiteSimpleViewViewHolder(binding)
            }
            SETTING_SELECTION_TYPE -> {
                val binding = ViewFireproofWebsiteSettingsSelectionBinding.inflate(inflater, parent, false)
                FireproofWebSiteViewHolder.FireproofWebsiteSettingSelectionViewHolder(
                    binding,
                    viewModel,
                )
            }
            SECTION_TITLE_TYPE -> {
                val binding = ViewFireproofTitleBinding.inflate(inflater, parent, false)
                binding.fireproofWebsiteSectionTitle.primaryText = binding.root.context.getString(R.string.fireproofWebsiteItemsSectionTitle)
                FireproofWebSiteViewHolder.FireproofWebsiteSimpleViewViewHolder(binding)
            }
            FIREPROOF_WEBSITE_TYPE -> {
                val binding = RowOneLineListItemBinding.inflate(inflater, parent, false)
                FireproofWebSiteViewHolder.FireproofWebsiteItemViewHolder(
                    inflater,
                    binding,
                    viewModel,
                    lifecycleOwner,
                    faviconManager,
                )
            }
            EMPTY_STATE_TYPE -> {
                val binding = ViewFireproofWebsiteEmptyHintBinding.inflate(inflater, parent, false)
                FireproofWebSiteViewHolder.FireproofWebsiteSimpleViewViewHolder(binding)
            }
            else -> throw IllegalArgumentException("viewType not found")
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (position < sortedHeaderElements.size) {
            sortedHeaderElements[position]
        } else {
            getListItemType()
        }
    }

    override fun onBindViewHolder(
        holder: FireproofWebSiteViewHolder,
        position: Int,
    ) {
        when (holder) {
            is FireproofWebSiteViewHolder.FireproofWebsiteSettingSelectionViewHolder -> {
                holder.bind(automaticFireproofSetting)
            }
            is FireproofWebSiteViewHolder.FireproofWebsiteItemViewHolder -> holder.bind(
                fireproofWebsites[
                    getWebsiteItemPosition(
                        position,
                    ),
                ],
            )
            else -> {}
        }
    }

    override fun getItemCount(): Int {
        return getItemsSize() + itemsOnTopOfList()
    }

    private fun getItemsSize() = if (fireproofWebsites.isEmpty()) {
        EMPTY_HINT_ITEM_SIZE
    } else {
        fireproofWebsites.size
    }

    private fun itemsOnTopOfList() = sortedHeaderElements.size

    private fun getWebsiteItemPosition(position: Int) = position - itemsOnTopOfList()

    private fun getListItemType(): Int {
        return if (fireproofWebsites.isEmpty()) {
            EMPTY_STATE_TYPE
        } else {
            FIREPROOF_WEBSITE_TYPE
        }
    }
}

sealed class FireproofWebSiteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    class FireproofWebsiteSettingSelectionViewHolder(
        private val binding: ViewFireproofWebsiteSettingsSelectionBinding,
        private val viewModel: FireproofWebsitesViewModel,
    ) :
        FireproofWebSiteViewHolder(binding.root) {
        fun bind(automaticFireproofSetting: AutomaticFireproofSetting) {
            binding.fireproofWebsiteSettingListItem.setSecondaryText(itemView.context.getString(automaticFireproofSetting.stringRes))
            binding.fireproofWebsiteSettingListItem.setOnClickListener {
                viewModel.command.value = ShowAutomaticFireproofSettingSelectionDialog(automaticFireproofSetting)
            }
        }
    }

    class FireproofWebsiteSimpleViewViewHolder(binding: ViewBinding) : FireproofWebSiteViewHolder(binding.root)

    class FireproofWebsiteItemViewHolder(
        private val layoutInflater: LayoutInflater,
        private val binding: RowOneLineListItemBinding,
        private val viewModel: FireproofWebsitesViewModel,
        private val lifecycleOwner: LifecycleOwner,
        private val faviconManager: FaviconManager,
    ) : FireproofWebSiteViewHolder(binding.root) {

        private val context: Context = binding.root.context
        private lateinit var entity: FireproofWebsiteEntity

        fun bind(entity: FireproofWebsiteEntity) {
            val listItem = binding.root
            this.entity = entity

            listItem.contentDescription = context.getString(
                R.string.fireproofWebsiteOverflowContentDescription,
                entity.website(),
            )

            loadFavicon(entity.domain, listItem.leadingIcon())
            listItem.setPrimaryText(entity.website())
            listItem.showTrailingIcon()
            listItem.setTrailingIconClickListener { anchor ->
                showOverFlowMenu(anchor, entity)
            }
        }

        private fun loadFavicon(
            url: String,
            image: ImageView,
        ) {
            lifecycleOwner.lifecycleScope.launch {
                faviconManager.loadToViewFromLocalWithPlaceholder(url = url, view = image)
            }
        }

        private fun showOverFlowMenu(
            anchor: View,
            entity: FireproofWebsiteEntity,
        ) {
            val popupMenu = PopupMenu(layoutInflater, R.layout.popup_window_remove_menu)
            val view = popupMenu.contentView
            popupMenu.onMenuItemClicked(view.findViewById(R.id.remove)) { deleteEntity(entity) }
            popupMenu.show(binding.root, anchor)
        }

        private fun deleteEntity(entity: FireproofWebsiteEntity) {
            logcat(INFO) { "Deleting website with domain: ${entity.domain}" }
            viewModel.onDeleteRequested(entity)
        }
    }
}
