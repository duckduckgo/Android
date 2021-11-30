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
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.text.HtmlCompat
import androidx.core.text.HtmlCompat.FROM_HTML_MODE_COMPACT
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.duckduckgo.app.global.extensions.safeGetApplicationIcon
import com.duckduckgo.mobile.android.ui.TextDrawable
import com.duckduckgo.mobile.android.ui.recyclerviewext.StickyHeaders
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.time.TimeDiffFormatter
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.model.TrackerFeedItem
import com.facebook.shimmer.ShimmerFrameLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.threeten.bp.LocalDateTime
import javax.inject.Inject

class TrackerFeedAdapter @Inject constructor(
    private val timeDiffFormatter: TimeDiffFormatter,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>(), StickyHeaders {

    private val trackerFeedItems = mutableListOf<TrackerFeedItem>()
    private lateinit var onAppClick: (TrackerFeedItem.TrackerFeedData) -> Unit

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is TrackerFeedViewHolder -> holder.bind(trackerFeedItems[position] as TrackerFeedItem.TrackerFeedData, onAppClick)
            is TrackerSkeletonViewHolder -> holder.bind()
            is TrackerFeedHeaderViewHolder -> holder.bind(trackerFeedItems[position] as TrackerFeedItem.TrackerFeedItemHeader)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            LOADING_STATE_TYPE -> TrackerSkeletonViewHolder.create(parent)
            EMPTY_STATE_TYPE -> TrackerEmptyFeedViewHolder.create(parent)
            DATA_STATE_TYPE -> TrackerFeedViewHolder.create(parent)
            else -> TrackerFeedHeaderViewHolder.create(parent, timeDiffFormatter)
        }
    }

    override fun getItemCount(): Int = trackerFeedItems.size

    override fun getItemViewType(position: Int): Int {
        return when (trackerFeedItems[position]) {
            is TrackerFeedItem.TrackerLoadingSkeleton -> LOADING_STATE_TYPE
            is TrackerFeedItem.TrackerEmptyFeed -> EMPTY_STATE_TYPE
            is TrackerFeedItem.TrackerFeedData -> DATA_STATE_TYPE
            is TrackerFeedItem.TrackerFeedItemHeader -> HEADER_TYPE
        }
    }

    override fun isStickyHeader(position: Int): Boolean {
        return trackerFeedItems[position] is TrackerFeedItem.TrackerFeedItemHeader
    }

    suspend fun updateData(data: List<TrackerFeedItem>, onAppClickListener: (TrackerFeedItem.TrackerFeedData) -> Unit) {
        onAppClick = onAppClickListener
        val newData = data
        val oldData = trackerFeedItems
        val diffResult = withContext(Dispatchers.IO) {
            DiffCallback(oldData, newData).run { DiffUtil.calculateDiff(this) }
        }

        trackerFeedItems.clear().also { trackerFeedItems.addAll(newData) }

        diffResult.dispatchUpdatesTo(this@TrackerFeedAdapter)
    }

    private class TrackerEmptyFeedViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        companion object {
            fun create(parent: ViewGroup): TrackerEmptyFeedViewHolder {
                val inflater = LayoutInflater.from(parent.context)
                val view = inflater.inflate(R.layout.view_device_shield_activity_empty, parent, false)
                return TrackerEmptyFeedViewHolder(view)
            }
        }
    }

    private class TrackerSkeletonViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
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

    private class TrackerFeedHeaderViewHolder(val view: TextView, private val timeDiffFormatter: TimeDiffFormatter) :
        RecyclerView.ViewHolder(view) {
        companion object {
            fun create(parent: ViewGroup, timeDiffFormatter: TimeDiffFormatter): TrackerFeedHeaderViewHolder {
                val inflater = LayoutInflater.from(parent.context)
                val view = inflater.inflate(R.layout.view_device_shield_activity_entry_header, parent, false)
                return TrackerFeedHeaderViewHolder(view as TextView, timeDiffFormatter)
            }
        }

        fun bind(item: TrackerFeedItem.TrackerFeedItemHeader) {
            val title =
                timeDiffFormatter.formatTimePassedInDays(LocalDateTime.now(), LocalDateTime.parse(item.timestamp))
            view.text = title
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

        fun bind(tracker: TrackerFeedItem.TrackerFeedData?, onAppClick: (TrackerFeedItem.TrackerFeedData) -> Unit) {
            tracker?.let { item ->
                with(activityMessage) {
                    val companies = resources.getQuantityString(R.plurals.atp_ActivityTrackersBlockedCompanyCount, tracker.trackers.size, tracker.trackers.size)
                    val styledText = HtmlCompat
                        .fromHtml(
                            context.getString(
                                R.string.atp_ActivityTrackersBlocked,
                                item.trackersTotalCount,
                                companies,
                                item.trackingApp.appDisplayName
                            ),
                            FROM_HTML_MODE_COMPACT
                        )
                    text = styledText
                }

                timeSinceTrackerBlocked.text = "${item.displayTimestamp}"

                Glide.with(trackingAppIcon.context.applicationContext)
                    .load(packageManager.safeGetApplicationIcon(item.trackingApp.packageId))
                    .error(item.trackingApp.appDisplayName.asIconDrawable())
                    .into(trackingAppIcon)

                with(trackerBadgesView) {
                    // click through recyvlerview
                    suppressLayout(false)
                    (adapter as TrackerBadgeAdapter).updateData(tracker.trackers)
                    suppressLayout(true)
                }
                itemView.setOnClickListener {
                    onAppClick(item)
                }
            }
        }

        private fun String.asIconDrawable(): TextDrawable {
            return TextDrawable.builder().buildRound(this.take(1), Color.DKGRAY)
        }
    }

    private class DiffCallback(private val old: List<TrackerFeedItem>, private val new: List<TrackerFeedItem>) :
        DiffUtil.Callback() {
        override fun getOldListSize() = old.size

        override fun getNewListSize() = new.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return old[oldItemPosition].id == new[newItemPosition].id
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return old == new
        }
    }

    companion object {
        private const val LOADING_STATE_TYPE = 0
        private const val EMPTY_STATE_TYPE = 1
        private const val DATA_STATE_TYPE = 2
        private const val HEADER_TYPE = 3
    }
}
