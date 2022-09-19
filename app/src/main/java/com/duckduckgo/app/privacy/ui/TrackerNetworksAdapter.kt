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

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.core.net.toUri
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ItemTrackerNetworkElementBinding
import com.duckduckgo.app.browser.databinding.ItemTrackerNetworkHeaderBinding
import com.duckduckgo.app.browser.databinding.ItemTrackerNetworkSectionTitleBinding
import com.duckduckgo.app.global.baseHost
import com.duckduckgo.app.privacy.renderer.TrackersRenderer
import com.duckduckgo.app.privacy.ui.TrackerNetworksAdapter.ViewData.SectionTitle
import com.duckduckgo.app.privacy.ui.TrackerNetworksAdapter.ViewData.Header
import com.duckduckgo.app.privacy.ui.TrackerNetworksAdapter.ViewData.Row
import com.duckduckgo.app.trackerdetection.model.Entity
import com.duckduckgo.app.trackerdetection.model.TrackingEvent
import com.duckduckgo.mobile.android.ui.DuckDuckGoTheme
import com.duckduckgo.mobile.android.ui.Theming
import com.duckduckgo.mobile.android.ui.view.addClickableLink
import com.duckduckgo.mobile.android.ui.view.gone
import com.duckduckgo.mobile.android.ui.view.show
import java.util.*

class TrackerNetworksAdapter(
    private val listener: TrackerNetworksListener
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val HEADER = 0
        const val ROW = 1
        const val SECTION_TITLE = 2
        private const val ANALYTICS = "Analytics"
        private const val ADVERTISING = "Advertising"
        private const val SOCIAL_NETWORK = "Social Network"
        private const val CONTENT_DELIVERY = "Content Delivery"
        private const val EMBEDDED_CONTENT = "Embedded Content"
        private val DISPLAY_CATEGORIES = listOf(ANALYTICS, ADVERTISING, SOCIAL_NETWORK, CONTENT_DELIVERY, EMBEDDED_CONTENT)
    }

    sealed class ViewData {
        data class Header(val networkName: String, val networkDisplayName: String) : ViewData()
        data class Row(val tracker: TrackingEvent) : ViewData()
        data class SectionTitle(
            @StringRes val descriptionRes: Int? = null,
            @StringRes val linkTextRes: Int? = null,
            @StringRes val linkUrlRes: Int? = null,
            val domain: String? = null,
        ) : ViewData()
    }

    class HeaderViewHolder(
        val binding: ItemTrackerNetworkHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root)

    class RowViewHolder(
        val binding: ItemTrackerNetworkElementBinding
    ) : RecyclerView.ViewHolder(binding.root)

    class SectionTitleViewHolder(
        val binding: ItemTrackerNetworkSectionTitleBinding
    ) : RecyclerView.ViewHolder(binding.root)

    private var viewData: List<ViewData> = ArrayList()
    private var networkRenderer: TrackersRenderer = TrackersRenderer()

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            HEADER -> HeaderViewHolder(binding = ItemTrackerNetworkHeaderBinding.inflate(inflater, parent, false))
            SECTION_TITLE -> SectionTitleViewHolder(binding = ItemTrackerNetworkSectionTitleBinding.inflate(inflater, parent, false))
            ROW -> RowViewHolder(binding = ItemTrackerNetworkElementBinding.inflate(inflater, parent, false))
            else -> throw IllegalArgumentException()
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int
    ) {
        val viewElement = viewData[position]
        if (holder is HeaderViewHolder && viewElement is Header) {
            onBindHeader(holder, viewElement)
        } else if (holder is RowViewHolder && viewElement is Row) {
            onBindRow(holder, viewElement)
        } else if (holder is SectionTitleViewHolder && viewElement is SectionTitle) {
            onBindSectionTitle(holder, viewElement)
        }
    }

    private fun onBindSectionTitle(
        holder: SectionTitleViewHolder,
        viewElement: SectionTitle
    ) {
        val context = holder.binding.root.context

        if (viewElement.descriptionRes != null) {
            if (viewElement.domain.isNullOrEmpty()) {
                holder.binding.trackersSectionDescription.text = context.resources.getString(viewElement.descriptionRes)
            } else {
                holder.binding.trackersSectionDescription.text = context.resources.getString(viewElement.descriptionRes, viewElement.domain)
            }
        }

        if (viewElement.linkTextRes != null && viewElement.linkUrlRes != null) {
            holder.binding.trackersSectionLink.addClickableLink(
                annotation = "learn_more_link",
                textSequence = context.getText(R.string.adLoadedSectionLinkText)
            ) {
                listener.onClicked(context.getString(R.string.adLoadedSectionUrl))
            }
            holder.binding.trackersSectionLink.show()
        } else {
            holder.binding.trackersSectionLink.gone()
        }
    }

    private fun onBindRow(
        holder: RowViewHolder,
        viewElement: Row
    ) {
        holder.binding.host.text = viewElement.tracker.trackerUrl.toUri().baseHost
        viewElement.tracker.categories?.let { categories ->
            val selectedCategory = DISPLAY_CATEGORIES.firstOrNull { categories.contains(it) }
            if (selectedCategory.isNullOrEmpty()) {
                holder.binding.category.gone()
            } else {
                holder.binding.category.text = selectedCategory
                holder.binding.category.show()
            }
        }
    }

    private fun onBindHeader(
        holder: HeaderViewHolder,
        viewElement: Header
    ) {
        holder.apply {
            val context = binding.root.context
            val iconResource = networkRenderer.networkLogoIcon(context, viewElement.networkName)
            if (iconResource != null) {
                val drawable = Theming.getThemedDrawable(
                    context,
                    iconResource,
                    DuckDuckGoTheme.LIGHT
                )
                binding.icon.setImageDrawable(drawable)
                binding.icon.show()
                binding.unknownIcon.gone()
            } else {
                val drawable = Theming.getThemedDrawable(
                    context,
                    R.drawable.other_tracker_privacy_dashboard_bg,
                    DuckDuckGoTheme.LIGHT
                )
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
        return when (viewData[position]) {
            is Header -> HEADER
            is Row -> ROW
            is SectionTitle -> SECTION_TITLE
        }
    }

    fun updateData(data: SortedMap<TrackerNetworksSection, SortedMap<Entity, List<TrackingEvent>>>) {
        val oldViewData = viewData
        val newViewData = generateViewData(data)
        val diffCallback = DiffCallback(oldViewData, newViewData)
        val diffResult = DiffUtil.calculateDiff(diffCallback)

        viewData = newViewData
        diffResult.dispatchUpdatesTo(this)
    }

    @VisibleForTesting
    internal fun generateViewData(data: SortedMap<TrackerNetworksSection, SortedMap<Entity, List<TrackingEvent>>>): List<ViewData> {
        val viewData = ArrayList<ViewData>().toMutableList()

        data.forEach { entry ->
            val section = entry.key
            if (!isEmptySection(section)) {
                viewData.add(
                    SectionTitle(
                        descriptionRes = getSectionDescription(data.size, section),
                        linkTextRes = section.linkTextRes,
                        linkUrlRes = section.linkUrlRes,
                        domain = section.domain
                    )
                )
            }
            entry.value.forEach { trackingMap ->
                viewData.add(Header(trackingMap.key.name, trackingMap.key.displayName))
                trackingMap.value?.mapTo(viewData) { Row(it) }
            }
        }
        return viewData
    }

    private fun getSectionDescription(dataSize: Int, section: TrackerNetworksSection): Int? {
        if (dataSize == 1 && section.descriptionRes == R.string.domainsLoadedSectionDescription) {
            return R.string.trackersBlockedNoSectionDescription
        }
        return section.descriptionRes
    }

    private fun isEmptySection(section: TrackerNetworksSection): Boolean {
        if (section.descriptionRes == null && section.linkTextRes == null && section.linkUrlRes == null) {
            return true
        }
        return false
    }

    class DiffCallback(
        private val old: List<ViewData>,
        private val new: List<ViewData>
    ) : DiffUtil.Callback() {

        override fun areItemsTheSame(
            oldItemPosition: Int,
            newItemPosition: Int
        ): Boolean {
            return old[oldItemPosition] == new[newItemPosition]
        }

        override fun getOldListSize(): Int {
            return old.size
        }

        override fun getNewListSize(): Int {
            return new.size
        }

        override fun areContentsTheSame(
            oldItemPosition: Int,
            newItemPosition: Int
        ): Boolean {
            return old[oldItemPosition] == new[newItemPosition]
        }
    }
}
