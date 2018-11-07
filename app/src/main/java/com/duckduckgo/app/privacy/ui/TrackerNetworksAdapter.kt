/*
 * Copyright (c) 2017 DuckDuckGo
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

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.baseHost
import com.duckduckgo.app.privacy.renderer.TrackersRenderer
import com.duckduckgo.app.trackerdetection.model.TrackingEvent
import kotlinx.android.synthetic.main.item_tracker_network_element.view.*
import kotlinx.android.synthetic.main.item_tracker_network_header.view.*


class TrackerNetworksAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val HEADER = 0
        const val ROW = 1
    }

    interface ViewData
    data class Header(val networkName: String) : ViewData
    data class Row(val tracker: TrackingEvent) : ViewData

    class HeaderViewHolder(
        val root: View,
        val network: TextView,
        val icon: ImageView
    ) : RecyclerView.ViewHolder(root)

    class RowViewHolder(
        val root: View,
        val host: TextView,
        val category: TextView
    ) : RecyclerView.ViewHolder(root)

    private var viewData: List<ViewData> = ArrayList()
    private var networkRenderer: TrackersRenderer = TrackersRenderer()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            HEADER -> {
                val root = LayoutInflater.from(parent.context).inflate(R.layout.item_tracker_network_header, parent, false)
                HeaderViewHolder(root, root.network, root.icon)
            }
            else -> {
                val root = LayoutInflater.from(parent.context).inflate(R.layout.item_tracker_network_element, parent, false)
                RowViewHolder(root, root.host, root.category)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val viewElement = viewData[position]
        if (holder is HeaderViewHolder && viewElement is Header) {
            onBindHeader(holder, viewElement)
        } else if (holder is RowViewHolder && viewElement is Row) {
            onBindRow(holder, viewElement)
        }
    }

    private fun onBindRow(holder: RowViewHolder, viewElement: Row) {
        holder.host.text = Uri.parse(viewElement.tracker.trackerUrl).baseHost
        holder.category.text = viewElement.tracker.trackerNetwork?.category
    }

    private fun onBindHeader(holder: HeaderViewHolder, viewElement: Header) {
        val iconResource = networkRenderer.networkLogoIcon(holder.icon.context, viewElement.networkName)
        if (iconResource != null) {
            holder.icon.setImageResource(iconResource)
        }
        holder.network.text = viewElement.networkName
    }

    override fun getItemCount(): Int {
        return viewData.size
    }

    override fun getItemViewType(position: Int): Int {
        return if (viewData[position] is Header) HEADER else ROW
    }

    fun updateData(data: Map<String, List<TrackingEvent>>) {
        val majorNetworkKeys = data.map { if (it.value.find { it.trackerNetwork?.isMajor == true } != null) it.key else null }.filterNotNull()
        val otherKeys = data.keys.filter { !majorNetworkKeys.contains(it) }.sorted()

        val oldViewData = viewData
        val newViewData = generateViewData(majorNetworkKeys, data) + generateViewData(otherKeys, data)
        val diffCallback = DiffCallback(oldViewData, newViewData)
        val diffResult = DiffUtil.calculateDiff(diffCallback)

        viewData = newViewData
        diffResult.dispatchUpdatesTo(this)
    }

    private fun generateViewData(keys: List<String>, data: Map<String, List<TrackingEvent>>): List<ViewData> {
        val viewData = ArrayList<ViewData>().toMutableList()
        for (key: String in keys) {
            viewData.add(Header(key))
            data[key]!!.mapTo(viewData) { Row(it) }
        }
        return viewData
    }

    class DiffCallback(private val old: List<ViewData>, private val new: List<ViewData>) : DiffUtil.Callback() {

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return old[oldItemPosition] == new[newItemPosition]
        }

        override fun getOldListSize(): Int {
            return old.size
        }

        override fun getNewListSize(): Int {
            return new.size
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return old[oldItemPosition] == new[newItemPosition]
        }
    }
}

