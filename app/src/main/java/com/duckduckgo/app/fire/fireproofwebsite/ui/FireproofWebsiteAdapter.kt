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

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.ImageView
import android.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteEntity
import com.duckduckgo.app.fire.fireproofwebsite.data.website
import com.duckduckgo.app.global.view.quietlySetIsChecked
import kotlinx.android.synthetic.main.view_fireproof_title.view.*
import kotlinx.android.synthetic.main.view_fireproof_website_entry.view.*
import kotlinx.android.synthetic.main.view_fireproof_website_toggle.view.*
import timber.log.Timber

class FireproofWebsiteAdapter(
    private val viewModel: FireproofWebsitesViewModel
) : RecyclerView.Adapter<FireproofWebSiteViewHolder>() {

    companion object {
        const val FIREPROOF_WEBSITE_TYPE = 0
        const val DESCRIPTION_TYPE = 1
        const val EMPTY_STATE_TYPE = 2
        const val TOGGLE_TYPE = 3
        const val DIVIDER_TYPE = 4
        const val SECTION_TITLE_TYPE = 5

        const val EMPTY_HINT_ITEM_SIZE = 1
    }

    private val sortedHeaderElements = listOf(DESCRIPTION_TYPE, TOGGLE_TYPE, DIVIDER_TYPE, SECTION_TITLE_TYPE)

    var fireproofWebsites: List<FireproofWebsiteEntity> = emptyList()
        set(value) {
            if (field != value) {
                field = value
                notifyDataSetChanged()
            }
        }

    var loginDetectionEnabled: Boolean = false

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FireproofWebSiteViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            DESCRIPTION_TYPE -> {
                val view = inflater.inflate(R.layout.view_fireproof_website_description, parent, false)
                FireproofWebSiteViewHolder.FireproofWebsiteSimpleViewViewHolder(view)
            }
            TOGGLE_TYPE -> {
                val view = inflater.inflate(R.layout.view_fireproof_website_toggle, parent, false)
                FireproofWebSiteViewHolder.FireproofWebsiteToggleViewHolder(view,
                    CompoundButton.OnCheckedChangeListener { _, isChecked -> viewModel.onUserToggleLoginDetection(isChecked) })
            }
            DIVIDER_TYPE -> {
                val view = inflater.inflate(R.layout.view_fireproof_divider, parent, false)
                FireproofWebSiteViewHolder.FireproofWebsiteSimpleViewViewHolder(view)
            }
            SECTION_TITLE_TYPE -> {
                val view = inflater.inflate(R.layout.view_fireproof_title, parent, false)
                view.fireproofWebsiteSectionTitle.setText(R.string.fireproofWebsiteItemsSectionTitle)
                FireproofWebSiteViewHolder.FireproofWebsiteSimpleViewViewHolder(view)
            }
            FIREPROOF_WEBSITE_TYPE -> {
                val view = inflater.inflate(R.layout.view_fireproof_website_entry, parent, false)
                FireproofWebSiteViewHolder.FireproofWebsiteItemViewHolder(view, viewModel)
            }
            EMPTY_STATE_TYPE -> {
                val view = inflater.inflate(R.layout.view_fireproof_website_empty_hint, parent, false)
                FireproofWebSiteViewHolder.FireproofWebsiteSimpleViewViewHolder(view)
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

    override fun onBindViewHolder(holder: FireproofWebSiteViewHolder, position: Int) {
        when (holder) {
            is FireproofWebSiteViewHolder.FireproofWebsiteToggleViewHolder -> {
                holder.bind(loginDetectionEnabled)
            }
            is FireproofWebSiteViewHolder.FireproofWebsiteItemViewHolder -> holder.bind(fireproofWebsites[getWebsiteItemPosition(position)])
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

    class FireproofWebsiteToggleViewHolder(itemView: View, private val listener: CompoundButton.OnCheckedChangeListener) :
        FireproofWebSiteViewHolder(itemView) {
        fun bind(loginDetectionEnabled: Boolean) {
            itemView.fireproofWebsiteToggle.quietlySetIsChecked(loginDetectionEnabled, listener)
        }
    }

    class FireproofWebsiteSimpleViewViewHolder(itemView: View) : FireproofWebSiteViewHolder(itemView)

    class FireproofWebsiteItemViewHolder(itemView: View, private val viewModel: FireproofWebsitesViewModel) : FireproofWebSiteViewHolder(itemView) {

        lateinit var entity: FireproofWebsiteEntity

        fun bind(entity: FireproofWebsiteEntity) {
            this.entity = entity

            itemView.overflowMenu.contentDescription = itemView.context.getString(
                R.string.fireproofWebsiteOverflowContentDescription,
                entity.website()
            )

            itemView.fireproofWebsiteEntryDomain.text = entity.website()
            loadFavicon(entity.domain)

            itemView.overflowMenu.setOnClickListener {
                showOverFlowMenu(itemView.overflowMenu, entity)
            }
        }

        private fun loadFavicon(url: String) {
            viewModel.loadFavicon(url, itemView.fireproofWebsiteEntryFavicon)
        }

        private fun showOverFlowMenu(overflowMenu: ImageView, entity: FireproofWebsiteEntity) {
            val popup = PopupMenu(overflowMenu.context, overflowMenu)
            popup.inflate(R.menu.fireproof_website_individual_overflow_menu)
            popup.setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.delete -> {
                        deleteEntity(entity); true
                    }
                    else -> false
                }
            }
            popup.show()
        }

        private fun deleteEntity(entity: FireproofWebsiteEntity) {
            Timber.i("Deleting website with domain: ${entity.domain}")
            viewModel.onDeleteRequested(entity)
        }
    }
}
