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

package com.duckduckgo.app.icon.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.app.browser.R
import kotlinx.android.synthetic.main.item_tracker_network_header.view.*

class AppIconsAdapter(private val onClick: (ChangeIconViewModel.IconViewData) -> Unit) : RecyclerView.Adapter<AppIconsAdapter.IconViewHolder>() {

    private var iconViewData: MutableList<ChangeIconViewModel.IconViewData> = mutableListOf()

    class IconViewHolder(
        val root: View,
        val icon: ImageView
    ) : RecyclerView.ViewHolder(root)

    override fun getItemCount() = iconViewData.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IconViewHolder {
        val root = LayoutInflater.from(parent.context).inflate(R.layout.item_app_icon, parent, false)
        return IconViewHolder(root, root.icon)
    }

    override fun onBindViewHolder(holder: IconViewHolder, position: Int) {
        val viewElement = iconViewData[position]
        holder.itemView.setOnClickListener { onClick.invoke(viewElement) }
        holder.icon.setBackgroundResource(viewElement.appIcon.icon)
        holder.itemView.isSelected = viewElement.selected
    }

    fun notifyChanges(newList: List<ChangeIconViewModel.IconViewData>) {
        iconViewData = newList.toMutableList()
        notifyDataSetChanged()
    }
}
