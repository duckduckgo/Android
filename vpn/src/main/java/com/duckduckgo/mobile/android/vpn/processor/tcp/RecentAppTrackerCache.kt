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

package com.duckduckgo.mobile.android.vpn.processor.tcp

import androidx.annotation.VisibleForTesting
import timber.log.Timber
import xyz.hexene.localvpn.TCB
import javax.inject.Inject

class RecentAppTrackerCache @Inject constructor(private val tcbCloser: TCBCloser) {

    @VisibleForTesting
    val recentTrackingAttempts = mutableMapOf<String, MutableMap<String, MutableList<RecentTrackerEvent>>>()

    fun addTrackerForApp(appPackage: String, trackerDomain: String?, tcbId: String, timestamp: Long = System.currentTimeMillis()) {
        if (trackerDomain == null) return

        synchronized(recentTrackingAttempts) {
            val trackersForApp = getTrackersForApp(appPackage)
            addTracker(trackersForApp, appPackage, trackerDomain, tcbId, timestamp)
        }
    }

    fun getRecentTrackingAttempt(appPackage: String, hostName: String): RecentTrackerEvent? {
        synchronized(recentTrackingAttempts) {
            val trackers = recentTrackingAttempts[appPackage]
            if (trackers.isNullOrEmpty()) return null
            val trackingEvents = trackers[hostName]
            if (trackingEvents.isNullOrEmpty()) return null
            return trackingEvents.last()
        }
    }

    private fun addTracker(
        trackers: MutableMap<String, MutableList<RecentTrackerEvent>>,
        appPackage: String,
        trackerDomain: String,
        tcbId: String,
        timestamp: Long
    ) {
        val trackingEvents = getTrackerListForDomain(trackers, trackerDomain)
        trackingEvents.add(RecentTrackerEvent(appPackage, trackerDomain, tcbId, timestamp))
    }

    private fun getTrackerListForDomain(trackers: MutableMap<String, MutableList<RecentTrackerEvent>>, trackerDomain: String): MutableList<RecentTrackerEvent> {
        val existingList = trackers[trackerDomain]
        if (existingList != null) return existingList

        return mutableListOf<RecentTrackerEvent>().also {
            trackers[trackerDomain] = it
        }
    }

    private fun getTrackersForApp(appPackage: String): MutableMap<String, MutableList<RecentTrackerEvent>> {
        val existingMap = recentTrackingAttempts[appPackage]
        if (existingMap != null) return existingMap

        return mutableMapOf<String, MutableList<RecentTrackerEvent>>().also {
            recentTrackingAttempts[appPackage] = it
        }
    }

    fun cleanupStaleConnections() {
        synchronized(recentTrackingAttempts) {
            val oldestTimestampToKeep = System.currentTimeMillis() - OLDEST_CONNECTION_TO_KEEP_MS
            val connectionsToRemove = mutableListOf<RecentTrackerEvent>()

            recentTrackingAttempts.values.forEach { cachedEntries ->
                cachedEntries.values.forEach { recentTrackersForDomain ->
                    recentTrackersForDomain.forEach { trackerEvent ->
                        if (trackerEvent.timestamp < oldestTimestampToKeep) {
                            connectionsToRemove.add(trackerEvent)
                        }
                    }
                }
            }

            if (connectionsToRemove.isNotEmpty()) {
                Timber.v("Cleaning up stale connections from recent trackers. There are %d to remove", connectionsToRemove.size)
            }

            connectionsToRemove.forEach { connection ->
                Timber.d("Killing connection: %s", connection)
                TCB.tcbCache[connection.tcbCacheKey]?.let { tcbCloser.closeConnection(it) }
                val trackersForApp = recentTrackingAttempts[connection.appPackage]
                val trackingEvents = trackersForApp?.get(connection.trackerDomain)
                trackingEvents?.remove(connection)
                pruneEmptyMapKeys(trackingEvents, trackersForApp, connection)
            }
        }
    }

    private fun pruneEmptyMapKeys(
        trackingEvents: MutableList<RecentTrackerEvent>?,
        trackersForApp: MutableMap<String, MutableList<RecentTrackerEvent>>?,
        connection: RecentTrackerEvent
    ) {
        if (trackingEvents.isNullOrEmpty()) trackersForApp?.remove(connection.trackerDomain)
        if (trackersForApp.isNullOrEmpty()) recentTrackingAttempts.remove(connection.appPackage)
    }

    companion object {
        private const val OLDEST_CONNECTION_TO_KEEP_MS = 30_000
    }

    data class RecentTrackerEvent(
        val appPackage: String,
        val trackerDomain: String,
        val tcbCacheKey: String,
        val timestamp: Long = System.currentTimeMillis()
    )
}
