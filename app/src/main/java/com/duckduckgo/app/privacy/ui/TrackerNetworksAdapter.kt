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
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ItemTrackerNetworkElementBinding
import com.duckduckgo.app.browser.databinding.ItemTrackerNetworkHeaderBinding
import com.duckduckgo.app.global.baseHost
import com.duckduckgo.app.privacy.renderer.TrackersRenderer
import com.duckduckgo.app.trackerdetection.model.Entity
import com.duckduckgo.app.trackerdetection.model.TrackingEvent
import com.duckduckgo.mobile.android.ui.DuckDuckGoTheme
import com.duckduckgo.mobile.android.ui.Theming
import com.duckduckgo.mobile.android.ui.view.gone
import com.duckduckgo.mobile.android.ui.view.show
import java.util.*
import kotlin.collections.ArrayList

class TrackerNetworksAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val HEADER = 0
        const val ROW = 1
        val DISPLAY_CATEGORIES = listOf("Analytics", "Advertising", "Social Network")
    }

    interface ViewData
    data class Header(val networkName: String, val networkDisplayName: String) : ViewData
    data class Row(val tracker: TrackingEvent) : ViewData

    class HeaderViewHolder(val binding: ItemTrackerNetworkHeaderBinding) :
        RecyclerView.ViewHolder(binding.root)

    class RowViewHolder(val binding: ItemTrackerNetworkElementBinding) :
        RecyclerView.ViewHolder(binding.root)

    private var viewData: List<ViewData> = ArrayList()
    private var networkRenderer: TrackersRenderer = TrackersRenderer()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            HEADER -> {
                val binding = ItemTrackerNetworkHeaderBinding.inflate(inflater, parent, false)
                HeaderViewHolder(binding)
            }
            else -> {
                val binding = ItemTrackerNetworkElementBinding.inflate(inflater, parent, false)
                RowViewHolder(binding)
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
        holder.binding.host.text = Uri.parse(viewElement.tracker.trackerUrl).baseHost
        viewElement.tracker.categories?.let { categories ->
            holder.binding.category.text =
                DISPLAY_CATEGORIES.firstOrNull { categories.contains(it) }
        }
    }

    private fun onBindHeader(holder: HeaderViewHolder, viewElement: Header) {
        holder.apply {
            val context = binding.root.context
            val iconResource = networkRenderer.networkLogoIcon(context, viewElement.networkName)
            if (iconResource != null) {
                val drawable =
                    Theming.getThemedDrawable(context, iconResource, DuckDuckGoTheme.LIGHT)
                binding.icon.setImageDrawable(drawable)
                binding.icon.show()
                binding.unknownIcon.gone()
            } else {
                val drawable =
                    Theming.getThemedDrawable(
                        context,
                        R.drawable.other_tracker_privacy_dashboard_bg,
                        DuckDuckGoTheme.LIGHT)
                binding.unknownIcon.text = viewElement.networkDisplayName.take(1)
                binding.unknownIcon.background = drawable
                binding.unknownIcon.show()
                binding.icon.gone()
            }
            binding.network.text = viewElement.networkDisplayName
        }
    }

    override fun getItemCount(): Int {
        return viewData.size
    }

    override fun getItemViewType(position: Int): Int {
        return if (viewData[position] is Header) HEADER else ROW
    }

    fun updateData(data: SortedMap<Entity, List<TrackingEvent>>) {
        val oldViewData = viewData
        val newViewData = generateViewData(data)
        val diffCallback = DiffCallback(oldViewData, newViewData)
        val diffResult = DiffUtil.calculateDiff(diffCallback)

        viewData = newViewData
        diffResult.dispatchUpdatesTo(this)
    }

    private fun generateViewData(data: SortedMap<Entity, List<TrackingEvent>>): List<ViewData> {
        val viewData = ArrayList<ViewData>().toMutableList()
        for ((entity: Entity, trackingEvents: List<TrackingEvent>) in data) {
            viewData.add(Header(entity.name, entity.displayName))
            trackingEvents?.mapTo(viewData) { Row(it) }
        }
        return viewData
    }

    class DiffCallback(private val old: List<ViewData>, private val new: List<ViewData>) :
        DiffUtil.Callback() {

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
