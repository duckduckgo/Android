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
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.fire.fireproofwebsite.ui.FireproofWebSiteViewHolder
import com.duckduckgo.app.privacy.model.UserWhitelistedDomain
import kotlinx.android.synthetic.main.view_fireproof_title.view.*

class WebsitesAdapter(viewModel: WhitelistViewModel, private val faviconManager: FaviconManager) : RecyclerView.Adapter<WebsiteViewHolder>() {

    companion object {
        const val SITE_ENTRY = 0
        const val DESCRIPTION_TYPE = 1
        const val EMPTY_STATE_TYPE = 2
        const val DIVIDER_TYPE = 3
        const val SECTION_TITLE_TYPE = 5

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
                WebsiteViewHolder.DividerViewViewHolder(view)
            }
            SECTION_TITLE_TYPE -> {
                val view = inflater.inflate(R.layout.view_list_item_section_title, parent, false)
                view.fireproofWebsiteSectionTitle.setText(R.string.fireproofWebsiteItemsSectionTitle)
                FireproofWebSiteViewHolder.FireproofWebsiteSimpleViewViewHolder(view)
            }
            SITE_ENTRY -> {
                val view = inflater.inflate(R.layout.view_fireproof_website_entry, parent, false)
                FireproofWebSiteViewHolder.FireproofWebsiteItemViewHolder(
                    inflater,
                    view,
                    viewModel,
                    lifecycleOwner,
                    faviconManager
                )
            }
        }
    }

    override fun onBindViewHolder(holder: WebsiteViewHolder, position: Int) {
        TODO("Not yet implemented")
    }

    override fun getItemCount(): Int {
        return entries.size + itemsOnTopOfList()
    }
}

sealed class WebsiteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    class DescriptionViewViewHolder(itemView: View) : WebsiteViewHolder(itemView)
    class DividerViewViewHolder(itemView: View) : WebsiteViewHolder(itemView)

}