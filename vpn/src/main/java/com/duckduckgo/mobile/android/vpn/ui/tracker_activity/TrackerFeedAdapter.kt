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
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.duckduckgo.app.global.extensions.safeGetApplicationIcon
import com.duckduckgo.mobile.android.ui.TextDrawable
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.model.TimePassed
import com.duckduckgo.mobile.android.vpn.time.TimeDiffFormatter
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.model.TrackerFeedItem
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.model.TrackerInfo
import org.threeten.bp.LocalDateTime
import org.threeten.bp.OffsetDateTime
import org.threeten.bp.temporal.ChronoUnit
import javax.inject.Inject

class TrackerFeedAdapter @Inject constructor(
    private val timeDiffFormatter: TimeDiffFormatter
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val trackerFeedItems = mutableListOf<TrackerFeedItem>()

    private var showHeadings: Boolean = true

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is TrackerFeedViewHolder -> holder.bind(trackerFeedItems[position] as TrackerFeedItem.TrackerFeedData, showHeadings)
            is TrackerEmptyFeedViewHolder -> holder.bind("No Activity Yet")
        }
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            EMPTY_STATE_TYPE -> TrackerEmptyFeedViewHolder.create(parent)
            else -> TrackerFeedViewHolder.create(parent, timeDiffFormatter)
        }
    }

    override fun getItemCount(): Int = trackerFeedItems.size

    override fun getItemViewType(position: Int): Int {
        return when (trackerFeedItems[position]) {
            is TrackerFeedItem.TrackerEmptyFeed -> EMPTY_STATE_TYPE
            is TrackerFeedItem.TrackerFeedData -> DATA_STATE_TYPE
        }
    }

    fun updateData(data: List<TrackerFeedItem>) {
        val oldData = trackerFeedItems
        val newData = data
        val diffResult = DiffCallback(oldData, newData).run { DiffUtil.calculateDiff(this) }

        trackerFeedItems.clear().also { trackerFeedItems.addAll(newData) }
        diffResult.dispatchUpdatesTo(this)
    }

    fun showTimeWindowHeadings(showHeadings: Boolean) {
        this.showHeadings = showHeadings
    }

    private class TrackerEmptyFeedViewHolder(val view: TextView) : RecyclerView.ViewHolder(view) {
        companion object {
            fun create(parent: ViewGroup): TrackerEmptyFeedViewHolder {
                val inflater = LayoutInflater.from(parent.context)
                val view: TextView = inflater.inflate(R.layout.view_device_shield_activity_empty, parent, false) as TextView
                return TrackerEmptyFeedViewHolder(view)
            }
        }

        fun bind(text: String) {
            view.text = text
        }
    }

    private class TrackerFeedViewHolder(view: View, private val timeDiffFormatter: TimeDiffFormatter) : RecyclerView.ViewHolder(view) {
        companion object {
            fun create(parent: ViewGroup, timeDiffFormatter: TimeDiffFormatter): TrackerFeedViewHolder {
                val inflater = LayoutInflater.from(parent.context)
                val view = inflater.inflate(R.layout.view_device_shield_activity_entry, parent, false)
                return TrackerFeedViewHolder(view, timeDiffFormatter)
            }
        }

        val context: Context = view.context
        var activityMessage: TextView = view.findViewById(R.id.activity_message)
        var timeSinceTrackerBlocked: TextView = view.findViewById(R.id.activity_time_since)
        var trackingAppIcon: ImageView = view.findViewById(R.id.tracking_app_icon)
        var trackerBadgesView: RecyclerView = view.findViewById<RecyclerView>(R.id.tracker_badges).apply {
            adapter = TrackerBadgeAdapter()
        }
        var dateHeading: TextView = view.findViewById(R.id.date_heading)

        var packageManager: PackageManager = view.context.packageManager

        fun bind(tracker: TrackerFeedItem.TrackerFeedData?, showHeadings: Boolean = true) {
            tracker?.let { item ->
                with(activityMessage) {
                    val styledText = HtmlCompat
                        .fromHtml(
                            context.getString(
                                R.string.deviceShieldActivityTrackersBlocked, item.trackersTotalCount, item.trackingApp.appDisplayName
                            ),
                            FROM_HTML_MODE_COMPACT
                        )
                    text = styledText
                }

                val timestamp = LocalDateTime.parse(item.timestamp)
                val timeDifference = timestamp.until(OffsetDateTime.now(), ChronoUnit.MILLIS)

                val timeRunning = TimePassed.fromMilliseconds(timeDifference)
                val trackerInfoMessage = "${item.trackers.asInfoMessage()} · ${timeRunning.shortFormat()}"
                timeSinceTrackerBlocked.text = trackerInfoMessage

                Glide.with(trackingAppIcon.context.applicationContext)
                    .load(packageManager.safeGetApplicationIcon(item.trackingApp.packageId))
                    .error(item.trackingApp.appDisplayName.asIconDrawable())
                    .into(trackingAppIcon)

                (trackerBadgesView.adapter as TrackerBadgeAdapter).updateData(tracker.trackers)

                dateHeading.isVisible = showHeadings && tracker.firstInBucket
                if (dateHeading.isVisible) {
                    dateHeading.text = timeDiffFormatter.formatTimePassedInDays(LocalDateTime.now(), timestamp)
                }
            }
        }

        private fun String.asIconDrawable(): TextDrawable {
            return TextDrawable.builder().buildRound(this.take(1), Color.DKGRAY)
        }

        private fun List<TrackerInfo>.asInfoMessage(): String {
            return context.resources.getQuantityString(R.plurals.deviceShieldActivityTrackersCount, size, first().company, size - 1)
        }
    }

    private class DiffCallback(private val old: List<TrackerFeedItem>, private val new: List<TrackerFeedItem>) : DiffUtil.Callback() {
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
        private const val EMPTY_STATE_TYPE = 0
        private const val DATA_STATE_TYPE = 1
    }
}
