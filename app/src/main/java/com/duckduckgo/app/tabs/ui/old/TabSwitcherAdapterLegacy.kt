/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.app.tabs.ui.old

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.Adapter
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.image.GlideApp
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.ui.TabSwitcherListener
import com.duckduckgo.app.tabs.ui.displayTitle
import com.duckduckgo.app.tabs.ui.displayUrl
import com.duckduckgo.app.tabs.ui.favicon

class TabSwitcherAdapterLegacy(private val context: Context, private val itemClickListener: TabSwitcherListener) :
    Adapter<TabSwitcherAdapterLegacy.TabViewHolderLegacy>() {

    private var data: List<TabEntity> = ArrayList()
    private var selectedTab: TabEntity? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabViewHolderLegacy {
        val inflater = LayoutInflater.from(parent.context)
        val root = inflater.inflate(R.layout.item_tab_legacy, parent, false)

        val favicon = root.findViewById<ImageView>(R.id.favicon)
        val title = root.findViewById<TextView>(R.id.title)
        val url = root.findViewById<TextView>(R.id.url)
        val closeButton = root.findViewById<ImageView>(R.id.close)
        val tabUnread = root.findViewById<ImageView>(R.id.tabUnread)

        return TabViewHolderLegacy(root, favicon, title, url, closeButton, tabUnread)
    }

    override fun getItemCount(): Int {
        return data.size
    }

    override fun onBindViewHolder(holder: TabViewHolderLegacy, position: Int) {

        val tab = data[position]
        holder.title.text = tab.displayTitle(context)
        holder.url.text = tab.displayUrl()
        holder.tabUnread.visibility = if (tab.viewed) View.INVISIBLE else View.VISIBLE
        holder.root.setBackgroundResource(if (tab.tabId == selectedTab?.tabId) SELECTED_BACKGROUND else DEFAULT_BACKGROUND)

        GlideApp.with(holder.root)
            .load(tab.favicon())
            .placeholder(R.drawable.ic_globe_gray_16dp)
            .error(R.drawable.ic_globe_gray_16dp)
            .into(holder.favicon)

        attachClickListeners(holder, tab)
    }

    private fun attachClickListeners(holder: TabViewHolderLegacy, tab: TabEntity) {
        holder.root.setOnClickListener {
            itemClickListener.onTabSelected(tab)
        }
        holder.close.setOnClickListener {
            itemClickListener.onTabDeleted(tab)
        }
    }

    fun updateData(data: List<TabEntity>?, selectedTab: TabEntity?) {

        data ?: return

        this.data = data
        this.selectedTab = selectedTab
        notifyDataSetChanged()
    }

    data class TabViewHolderLegacy(
        val root: View,
        val favicon: ImageView,
        val title: TextView,
        val url: TextView,
        val close: ImageView,
        val tabUnread: View
    ) : RecyclerView.ViewHolder(root)

    companion object {

        @DrawableRes
        private const val SELECTED_BACKGROUND = R.drawable.tab_background_selected
        @DrawableRes
        private const val DEFAULT_BACKGROUND = R.drawable.tab_background
    }

}
