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

package com.duckduckgo.mobile.android.vpn.ui.tracker_activity

import android.content.Context
import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat.startActivity
import androidx.core.text.HtmlCompat
import androidx.core.text.HtmlCompat.FROM_HTML_MODE_COMPACT
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.duckduckgo.common.ui.recyclerviewext.StickyHeaders
import com.duckduckgo.common.ui.view.divider.HorizontalDivider
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.listitem.SectionHeaderListItem
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.extensions.safeGetApplicationIcon
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.model.TrackerCompanyBadge
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.model.TrackerFeedItem
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.view.AppsProtectionStateView
import com.duckduckgo.mobile.android.vpn.ui.util.TextDrawable
import com.facebook.shimmer.ShimmerFrameLayout
import kotlinx.coroutines.withContext
import javax.inject.Inject

class TrackerFeedAdapter @Inject constructor(
    private val dispatchers: DispatcherProvider,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>(), StickyHeaders {

    private val trackerFeedItems = mutableListOf<TrackerFeedItem>()
    private lateinit var onAppClick: (TrackerFeedItem) -> Unit

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
    ) {
        when (holder) {
            is TrackerFeedViewHolder ->
                holder.bind(trackerFeedItems[position] as TrackerFeedItem.TrackerFeedData, onAppClick)
            is TrackerSkeletonViewHolder -> holder.bind()
            is TrackerFeedHeaderViewHolder -> holder.bind(trackerFeedItems[position] as TrackerFeedItem.TrackerFeedItemHeader)
            is TrackerAppsProtectionStateViewHolder ->
                holder.bind(trackerFeedItems[position] as TrackerFeedItem.TrackerTrackerAppsProtection, onAppClick)
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): RecyclerView.ViewHolder {
        return when (viewType) {
            LOADING_STATE_TYPE -> TrackerSkeletonViewHolder.create(parent)
            DATA_STATE_TYPE -> TrackerFeedViewHolder.create(parent)
            DESCRIPTION_TYPE -> TrackerDescriptionViewHolder.create(parent)
            APPS_PROTECTION_STATE_TYPE -> TrackerAppsProtectionStateViewHolder.create(parent)
            else -> TrackerFeedHeaderViewHolder.create(parent)
        }
    }

    override fun getItemCount(): Int = trackerFeedItems.size

    override fun getItemViewType(position: Int): Int {
        return when (trackerFeedItems[position]) {
            is TrackerFeedItem.TrackerLoadingSkeleton -> LOADING_STATE_TYPE
            is TrackerFeedItem.TrackerFeedData -> DATA_STATE_TYPE
            is TrackerFeedItem.TrackerFeedItemHeader -> HEADER_TYPE
            is TrackerFeedItem.TrackerDescriptionFeed -> DESCRIPTION_TYPE
            is TrackerFeedItem.TrackerTrackerAppsProtection -> APPS_PROTECTION_STATE_TYPE
        }
    }

    override fun isStickyHeader(position: Int): Boolean {
        return trackerFeedItems[position] is TrackerFeedItem.TrackerFeedItemHeader
    }

    suspend fun updateData(
        data: List<TrackerFeedItem>,
        onAppClickListener: (TrackerFeedItem) -> Unit,
    ) {
        onAppClick = onAppClickListener
        val newData = data
        val oldData = trackerFeedItems
        val diffResult = withContext(dispatchers.io()) {
            DiffCallback(oldData, newData).run { DiffUtil.calculateDiff(this) }
        }

        trackerFeedItems.clear().also { trackerFeedItems.addAll(newData) }

        diffResult.dispatchUpdatesTo(this@TrackerFeedAdapter)
    }

    private class TrackerSkeletonViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        companion object {
            fun create(parent: ViewGroup): TrackerSkeletonViewHolder {
                val inflater = LayoutInflater.from(parent.context)
                val view = inflater.inflate(R.layout.view_device_shield_activity_skeleton_entry, parent, false)
                return TrackerSkeletonViewHolder(view)
            }
        }

        var shimmerLayout: ShimmerFrameLayout = view.findViewById(R.id.shimmerFrameLayout)

        fun bind() {
            shimmerLayout.startShimmer()
        }
    }

    private class TrackerFeedHeaderViewHolder(
        val view: SectionHeaderListItem,
    ) :
        RecyclerView.ViewHolder(view) {
        companion object {
            fun create(
                parent: ViewGroup,
            ): TrackerFeedHeaderViewHolder {
                val inflater = LayoutInflater.from(parent.context)
                val view = inflater.inflate(R.layout.view_device_shield_activity_entry_header, parent, false)
                return TrackerFeedHeaderViewHolder(view as SectionHeaderListItem)
            }
        }

        val context: Context = view.context
        val today = context.getString(com.duckduckgo.common.utils.R.string.common_Today)
        val yesterday = context.getString(com.duckduckgo.common.utils.R.string.common_Yesterday)

        fun bind(item: TrackerFeedItem.TrackerFeedItemHeader) {
            if (item.timestamp == today || item.timestamp == yesterday) {
                view.primaryText = context.getString(R.string.atp_ActivityBlockedByHeaderText, item.timestamp)
            } else {
                view.primaryText = context.getString(R.string.atp_ActivityBlockedByOnDateHeaderText, item.timestamp)
            }
        }
    }

    private class TrackerFeedViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        companion object {
            fun create(parent: ViewGroup): TrackerFeedViewHolder {
                val inflater = LayoutInflater.from(parent.context)
                val view = inflater.inflate(R.layout.view_device_shield_activity_entry, parent, false)
                return TrackerFeedViewHolder(view)
            }
        }

        val context: Context = view.context
        var activityMessage: TextView = view.findViewById(R.id.activity_message)
        var timeSinceTrackerBlocked: TextView = view.findViewById(R.id.activity_time_since)
        var trackingAppIcon: ImageView = view.findViewById(R.id.tracking_app_icon)
        var trackerBadgesView: RecyclerView = view.findViewById<RecyclerView>(R.id.tracker_badges).apply {
            adapter = TrackerBadgeAdapter()
        }

        var packageManager: PackageManager = view.context.packageManager

        fun bind(
            tracker: TrackerFeedItem.TrackerFeedData?,
            onAppClick: (TrackerFeedItem.TrackerFeedData) -> Unit,
        ) {
            tracker?.let { item ->
                with(activityMessage) {
                    val trackersCount = tracker.trackersTotalCount
                    val trackingAppName = item.trackingApp.appDisplayName

                    var trackingCompanies = tracker.trackingCompanyBadges.size
                    if (tracker.trackingCompanyBadges.last() is TrackerCompanyBadge.Extra) {
                        // Subtracting 1 since badge sizes contains the Extra icon with amount
                        trackingCompanies += (tracker.trackingCompanyBadges.last() as TrackerCompanyBadge.Extra).amount - 1
                    }

                    val textToStyle = if (trackersCount == 1) {
                        if (trackingCompanies == 1) {
                            resources.getString(
                                R.string.atp_ActivityTrackersCompanyBlockedOnetimeOneCompany,
                                trackingAppName,
                            )
                        } else {
                            resources.getString(
                                R.string.atp_ActivityTrackersCompanyBlockedOnetimeOtherCompanies,
                                trackingCompanies,
                                trackingAppName,
                            )
                        }
                    } else {
                        if (trackingCompanies == 1) {
                            resources.getString(
                                R.string.atp_ActivityTrackersCompanyBlockedOtherTimesOneCompany,
                                trackersCount,
                                trackingAppName,
                            )
                        } else {
                            resources.getString(
                                R.string.atp_ActivityTrackersCompanyBlockedOtherTimesOtherCompanies,
                                trackersCount,
                                trackingCompanies,
                                trackingAppName,
                            )
                        }
                    }
                    val styledText = HtmlCompat.fromHtml(textToStyle, FROM_HTML_MODE_COMPACT)
                    text = styledText
                }

                timeSinceTrackerBlocked.text = item.displayTimestamp

                Glide.with(trackingAppIcon.context.applicationContext)
                    .load(packageManager.safeGetApplicationIcon(item.trackingApp.packageId))
                    .error(item.trackingApp.appDisplayName.asIconDrawable())
                    .into(trackingAppIcon)

                with(trackerBadgesView) {
                    // click through recyclerview
                    suppressLayout(false)
                    (adapter as TrackerBadgeAdapter).updateData(tracker.trackingCompanyBadges)
                    suppressLayout(true)
                }
                itemView.setOnClickListener {
                    startActivity(
                        context,
                        AppTPCompanyTrackersActivity.intent(
                            context,
                            item.trackingApp.packageId,
                            item.trackingApp.appDisplayName,
                            item.bucket,
                        ),
                        null,
                    )
                }
                itemView.setOnClickListener {
                    onAppClick(item)
                }
            }
        }

        private fun String.asIconDrawable(): TextDrawable {
            return TextDrawable.asIconDrawable(this)
        }
    }

    private class TrackerDescriptionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        companion object {
            fun create(parent: ViewGroup): TrackerDescriptionViewHolder {
                val inflater = LayoutInflater.from(parent.context)
                val view = inflater.inflate(R.layout.view_device_shield_activity_description, parent, false)
                return TrackerDescriptionViewHolder(view)
            }
        }
    }

    private class TrackerAppsProtectionStateViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        companion object {
            fun create(parent: ViewGroup): TrackerAppsProtectionStateViewHolder {
                val inflater = LayoutInflater.from(parent.context)
                val view = inflater.inflate(R.layout.view_device_shield_activity_apps_protection, parent, false)
                return TrackerAppsProtectionStateViewHolder(view)
            }
        }

        var protectedAppsState: AppsProtectionStateView = view.findViewById(R.id.protectedAppsState)
        var protectedAppsDivider: HorizontalDivider = view.findViewById(R.id.protectedAppsBottomDivider)
        var unProtectedAppsState: AppsProtectionStateView = view.findViewById(R.id.unProtectedAppsState)
        var unProtectedAppsDivider: HorizontalDivider = view.findViewById(R.id.unProtectedAppsBottomDivider)

        fun bind(
            tracker: TrackerFeedItem.TrackerTrackerAppsProtection,
            onAppClick: (TrackerFeedItem.TrackerTrackerAppsProtection) -> Unit,
        ) {
            if (tracker.appsData.protectedAppsData.appsCount > 0) {
                protectedAppsState.bind(tracker.appsData.protectedAppsData) { appsFilter ->
                    onAppClick(tracker.copy(selectedFilter = appsFilter))
                }
                protectedAppsState.show()
                protectedAppsDivider.show()
            } else {
                protectedAppsState.gone()
                protectedAppsDivider.gone()
            }
            if (tracker.appsData.unprotectedAppsData.appsCount > 0) {
                unProtectedAppsState.bind(tracker.appsData.unprotectedAppsData) { appsFilter ->
                    onAppClick(tracker.copy(selectedFilter = appsFilter))
                }
                unProtectedAppsState.show()
                unProtectedAppsDivider.show()
            } else {
                unProtectedAppsState.gone()
                unProtectedAppsDivider.gone()
            }
        }
    }

    private class DiffCallback(
        private val old: List<TrackerFeedItem>,
        private val new: List<TrackerFeedItem>,
    ) :
        DiffUtil.Callback() {
        override fun getOldListSize() = old.size

        override fun getNewListSize() = new.size

        override fun areItemsTheSame(
            oldItemPosition: Int,
            newItemPosition: Int,
        ): Boolean {
            return old[oldItemPosition].id == new[newItemPosition].id
        }

        override fun areContentsTheSame(
            oldItemPosition: Int,
            newItemPosition: Int,
        ): Boolean {
            return old[oldItemPosition] == new[newItemPosition]
        }
    }

    companion object {
        private const val LOADING_STATE_TYPE = 0
        private const val DATA_STATE_TYPE = 1
        private const val HEADER_TYPE = 2
        private const val DESCRIPTION_TYPE = 3
        private const val APPS_PROTECTION_STATE_TYPE = 4
    }
}
