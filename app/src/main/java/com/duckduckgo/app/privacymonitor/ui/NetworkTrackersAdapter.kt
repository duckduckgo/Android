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

package com.duckduckgo.app.privacymonitor.ui

import android.net.Uri
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView

import android.widget.TextView
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.baseHost
import com.duckduckgo.app.trackerdetection.model.TrackingEvent
import kotlinx.android.synthetic.main.item_tracker_network_element.view.*
import kotlinx.android.synthetic.main.item_tracker_network_header.view.*


class NetworkTrackersAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var data: List<ViewData> = ArrayList()

    companion object {
        val HEADER = 0
        val ROW = 1
    }

    interface ViewData
    data class Header(val networkName: String) : ViewData
    data class Row(val tracker: TrackingEvent) : ViewData

    override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            HEADER -> {
                val root = LayoutInflater.from(parent!!.context).inflate(R.layout.item_tracker_network_header, parent, false)
                HeaderViewHolder(root, root.network, root.icon)
            }
            else -> {
                val root = LayoutInflater.from(parent!!.context).inflate(R.layout.item_tracker_network_element, parent, false)
                RowViewHolder(root, root.host, root.category)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val element = data[position]
        if (holder is HeaderViewHolder && element is Header) {
            holder.network.text = element.networkName
        } else if (holder is RowViewHolder && element is Row) {
            holder.host.text = Uri.parse(element.tracker.trackerUrl).baseHost
            holder.category.text = element.tracker.trackerNetwork?.category
        }
    }

    override fun getItemCount(): Int {
        return data.size
    }

    override fun getItemViewType(position: Int): Int {
        return if (data[position] is Header) HEADER else ROW
    }

    fun updateData(trackers: List<TrackingEvent>) {
        var organised = HashMap<String, MutableList<TrackingEvent>>().toMutableMap()
        for (tracker: TrackingEvent in trackers) {
            val network = tracker.trackerNetwork?.name ?: Uri.parse(tracker.trackerUrl).baseHost ?: tracker.trackerUrl
            var entries = organised.get(network) ?: ArrayList()
            entries.add(tracker)
            organised[network] = entries
        }

        var flat = ArrayList<ViewData>().toMutableList()
        for (key: String in organised.keys) {
            flat.add(Header(key))
            for (tracker: TrackingEvent in organised[key]!!) {
                flat.add(Row(tracker))
            }
        }

        data = flat
        notifyDataSetChanged()
    }

    class HeaderViewHolder(val root: View,
                           val network: TextView,
                           val icon: ImageView) : RecyclerView.ViewHolder(root)

    class RowViewHolder(val root: View,
                        val host: TextView,
                        val category: TextView) : RecyclerView.ViewHolder(root)

}
