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

package com.duckduckgo.app.privacy.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.privacy.model.UserWhitelistedDomain
import com.duckduckgo.mobile.android.ui.menu.PopupMenu
import com.duckduckgo.mobile.android.ui.view.SingleLineListItem
import kotlinx.coroutines.launch

class WebsitesAdapter(
    private val viewModel: WhitelistViewModel,
    private val lifecycleOwner: LifecycleOwner,
    private val faviconManager: FaviconManager
) : RecyclerView.Adapter<WebsiteViewHolder>() {

    companion object {
        const val SITE_ENTRY = 0
        const val DESCRIPTION_TYPE = 1
        const val EMPTY_STATE_TYPE = 2
        const val DIVIDER_TYPE = 3
        const val SECTION_TITLE_TYPE = 4

        const val EMPTY_HINT_ITEM_SIZE = 1
    }

    private val sortedHeaderElements = listOf(
        DESCRIPTION_TYPE,
        DIVIDER_TYPE,
        SECTION_TITLE_TYPE,
        SITE_ENTRY
    )

    private fun itemsOnTopOfList() = sortedHeaderElements.size

    private fun getWebsiteItemPosition(position: Int) = position - itemsOnTopOfList()

    var entries: List<UserWhitelistedDomain> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WebsiteViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            DESCRIPTION_TYPE -> {
                val view = inflater.inflate(R.layout.view_list_item_description, parent, false)
                val description = view.findViewById<TextView>(R.id.websiteDescription)
                description.setText(R.string.whitelistExplanation)
                WebsiteViewHolder.DescriptionViewViewHolder(view)
            }
            DIVIDER_TYPE -> {
                val view = inflater.inflate(R.layout.view_list_item_divider, parent, false)
                WebsiteViewHolder.DividerViewHolder(view)
            }
            SECTION_TITLE_TYPE -> {
                val view = inflater.inflate(R.layout.view_list_item_section_title, parent, false)
                view.findViewById<TextView>(R.id.listItemSectionTitle).setText(R.string.fireproofWebsiteItemsSectionTitle)
                WebsiteViewHolder.SectionTitleViewHolder(view)
            }
            SITE_ENTRY -> {
                val view = inflater.inflate(R.layout.view_fireproof_website_entry, parent, false)
                WebsiteViewHolder.WebsiteItemViewHolder(
                    inflater,
                    view,
                    viewModel,
                    lifecycleOwner,
                    faviconManager
                )
            }
            else -> throw IllegalArgumentException("viewType not found")
        }
    }

    override fun onBindViewHolder(holder: WebsiteViewHolder, position: Int) {
        when (holder) {
            is WebsiteViewHolder.WebsiteItemViewHolder -> {
                holder.bind(entries[getWebsiteItemPosition(position)])
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (position < sortedHeaderElements.size) {
            sortedHeaderElements[position]
        } else {
            getListItemType()
        }
    }

    private fun getListItemType(): Int {
        return if (entries.isEmpty()) {
            EMPTY_STATE_TYPE
        } else {
            SITE_ENTRY
        }
    }

    override fun getItemCount(): Int {
        return getItemsSize() + itemsOnTopOfList()
    }

    private fun getItemsSize() = if (entries.isEmpty()) {
        EMPTY_HINT_ITEM_SIZE
    } else {
        entries.size
    }
}

sealed class WebsiteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    class DescriptionViewViewHolder(itemView: View) : WebsiteViewHolder(itemView)
    class DividerViewHolder(itemView: View) : WebsiteViewHolder(itemView)
    class SectionTitleViewHolder(itemView: View) : WebsiteViewHolder(itemView)
    class WebsiteItemViewHolder(
        private val layoutInflater: LayoutInflater,
        itemView: View,
        private val viewModel: WhitelistViewModel,
        private val lifecycleOwner: LifecycleOwner,
        private val faviconManager: FaviconManager
    ) : WebsiteViewHolder(itemView) {

        lateinit var entity: UserWhitelistedDomain

        fun bind(entity: UserWhitelistedDomain) {
            val listItem = itemView as SingleLineListItem
            this.entity = entity

            listItem.setContentDescription(
                itemView.context.getString(
                    R.string.fireproofWebsiteOverflowContentDescription,
                    entity.domain
                )
            )

            listItem.setTitle(entity.domain)
            loadFavicon(entity.domain)
            listItem.setOverflowClickListener { anchor ->
                showOverFlowMenu(anchor, entity)
            }
        }

        private fun loadFavicon(url: String) {
            lifecycleOwner.lifecycleScope.launch {
                faviconManager.loadToViewFromLocalOrFallback(url = url, view = itemView.findViewById(R.id.image))
            }
        }

        private fun showOverFlowMenu(anchor: View, entity: UserWhitelistedDomain) {
            val popupMenu = PopupMenu(layoutInflater, R.layout.popup_window_delete_menu)
            val view = popupMenu.contentView
            popupMenu.apply {
                onMenuItemClicked(view.findViewById(R.id.edit)) { viewModel.onEditRequested(entity) }
                onMenuItemClicked(view.findViewById(R.id.delete)) { viewModel.onDeleteRequested(entity) }
            }
            popupMenu.show(itemView, anchor)
        }
    }
}
