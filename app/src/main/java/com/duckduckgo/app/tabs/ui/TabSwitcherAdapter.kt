/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.tabs.ui

import android.content.Context
import android.net.Uri
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.RecyclerView.Adapter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.AppUrl
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.ui.TabSwitcherAdapter.TabViewHolder
import kotlinx.android.synthetic.main.item_tab.view.*

class TabSwitcherAdapter(private val context: Context, private val itemClickListener: TabSwitchedListener) : Adapter<TabViewHolder>() {

    private var data: List<TabEntity> = ArrayList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val root = inflater.inflate(R.layout.item_tab, parent, false)
        return TabViewHolder(root, root.favicon, root.title, root.url, root.close)
    }

    override fun getItemCount(): Int {
        return data.size
    }

    override fun onBindViewHolder(holder: TabViewHolder, position: Int) {
        val tab = data[position]
        if (tab.title == null && tab.url == null) {
            holder.title.text = context.getText(R.string.homeTab)
            holder.url.text = AppUrl.Url.HOME
        } else {
            holder.url.text = tab.url ?: ""
            holder.title.text = tab.title ?: Uri.parse(tab.url).host ?: ""
        }
        holder.root.setOnClickListener {
            itemClickListener.onTabSelected(tab)
        }
        holder.close.setOnClickListener {
            itemClickListener.onTabDeleted(tab)
        }
    }

    fun updateData(data: List<TabEntity>) {
        this.data = data
        notifyDataSetChanged()
    }

    interface TabSwitchedListener {
        fun onNewTabRequested()
        fun onTabSelected(tab: TabEntity)
        fun onTabDeleted(tab: TabEntity)
    }

    data class TabViewHolder(
        val root: View,
        val favicon: ImageView,
        val title: TextView,
        val url: TextView,
        val close: ImageView
    ) : RecyclerView.ViewHolder(root)
}
