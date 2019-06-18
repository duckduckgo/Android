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
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.RecyclerView.Adapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.image.GlideApp
import com.duckduckgo.app.tabs.model.TabEntity
import kotlinx.android.synthetic.main.item_tab.view.*

class TabSwitcherAdapter(private val context: Context, private val itemClickListener: TabSwitchedListener) : Adapter<TabSwitcherAdapter.TabViewHolder>() {

    private var data: List<TabEntity> = ArrayList()
    private var selectedTab: TabEntity? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val root = inflater.inflate(R.layout.item_tab, parent, false)
        //return TabViewHolder(root, root.favicon, root.tabPreview, root.title, root.close, root.tabUnread)
        return TabViewHolder(root, root.favicon, root.tabPreview, root.title, root.close)
        //return TabViewHolder(root)
    }

    override fun getItemCount(): Int {
        return data.size
    }

    override fun onBindViewHolder(holder: TabViewHolder, position: Int) {

        val tab = data[position]
        holder.title.text = tab.displayTitle(context)
        //holder.tabUnread.visibility = if (tab.viewed) View.INVISIBLE else View.VISIBLE
        //holder.root.setBackgroundResource(if (tab.tabId == selectedTab?.tabId) SELECTED_BACKGROUND else DEFAULT_BACKGROUND)

        GlideApp.with(holder.root)
            .load(tab.favicon())
            .placeholder(R.drawable.ic_globe_gray_16dp)
            .error(R.drawable.ic_globe_gray_16dp)
            .into(holder.favicon)

        holder.tabPreview.setImageResource(R.drawable.ic_globe_gray_16dp)

//        GlideApp.with(holder.root)
//            .load(R.drawable.ic_globe_gray_16dp)
//            .into(holder.tabPreview)

        attachClickListeners(holder, tab)

        val randomColorList = listOf(
            R.color.cornflowerBlue,
            R.color.brickOrange,
            R.color.midGreen,
            R.color.coolGray
        )

        val color = position % randomColorList.size
        val tint = ContextCompat.getColor(context, randomColorList[color])
        ImageViewCompat.setImageTintList(holder.tabPreview, ColorStateList.valueOf(tint))
    }

    private fun attachClickListeners(holder: TabViewHolder, tab: TabEntity) {
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

    fun getTab(position: Int): TabEntity = data[position]

    interface TabSwitchedListener {
        fun onNewTabRequested()
        fun onTabSelected(tab: TabEntity)
        fun onTabDeleted(tab: TabEntity)
    }

    data class TabViewHolder(
        val root: View,
        val favicon: ImageView,
        val tabPreview: ImageView,
        val title: TextView,
        val close: ImageView/*,
        val tabUnread: View*/
    ) : ViewHolder(root)

    companion object {

        @DrawableRes
        private const val SELECTED_BACKGROUND = R.drawable.tab_background_selected
        @DrawableRes
        private const val DEFAULT_BACKGROUND = R.drawable.tab_background
    }


}
