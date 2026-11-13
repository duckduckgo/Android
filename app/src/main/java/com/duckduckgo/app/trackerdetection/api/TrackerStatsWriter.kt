/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.app.trackerdetection.api

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.trackerdetection.db.WebTrackerBlocked
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

/**
 * Persists per-tracker statistics without issuing one database transaction per tracker request.
 */
interface TrackerStatsWriter {
    /**
     * Queues a blocked-tracker history row, stamped with the time of this call.
     */
    fun recordBlockedHistory(trackerUrl: String, trackerCompany: String)

    /**
     * Queues an increment of the lifetime blocked-tracker count.
     */
    fun incrementBlockedCount()

    /**
     * Queues an increment of [networkName] in the tracker network leaderboard.
     */
    fun incrementNetworkCount(networkName: String)

    /**
     * Drops history rows that were queued but not yet written.
     */
    fun discardPendingHistory()
}

/**
 * Accumulates tracker events in memory and flushes them in one transaction per [FLUSH_WINDOW_MS] burst.
 *
 * `app.db` has a single connection, so independent writes per tracker queue up on the connection pool.
 * Events queued in the window are lost if the process dies before the flush.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealTrackerStatsWriter @Inject constructor(
    private val appDatabase: AppDatabase,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
) : TrackerStatsWriter {

    private class Pending {
        val history = mutableListOf<WebTrackerBlocked>()
        var blockedCount = 0
        val networkCounts = linkedMapOf<String, Int>()
    }

    private val lock = Any()
    private var pending = Pending()
    private var flushScheduled = false

    // Keeps flushes strictly sequential even if a slow one overlaps the next window.
    private val writeMutex = Mutex()

    override fun recordBlockedHistory(trackerUrl: String, trackerCompany: String) {
        // Built here so the persisted timestamp is the request time, not the flush time.
        val row = WebTrackerBlocked(trackerUrl = trackerUrl, trackerCompany = trackerCompany)
        enqueue { history.add(row) }
    }

    override fun incrementBlockedCount() {
        enqueue { blockedCount++ }
    }

    override fun incrementNetworkCount(networkName: String) {
        enqueue { networkCounts.merge(networkName, 1, Int::plus) }
    }

    override fun discardPendingHistory() {
        synchronized(lock) { pending.history.clear() }
    }

    private fun enqueue(update: Pending.() -> Unit) {
        val shouldSchedule = synchronized(lock) {
            pending.update()
            val schedule = !flushScheduled
            flushScheduled = true
            schedule
        }
        if (shouldSchedule) {
            appCoroutineScope.launch(dispatchers.io()) {
                delay(FLUSH_WINDOW_MS)
                flush()
            }
        }
    }

    private suspend fun flush() {
        val batch = synchronized(lock) {
            flushScheduled = false
            pending.also { pending = Pending() }
        }
        writeMutex.withLock {
            appDatabase.runInTransaction {
                if (batch.history.isNotEmpty()) appDatabase.webTrackersBlockedDao().insertAll(batch.history)
                if (batch.blockedCount > 0) appDatabase.privacyProtectionCountsDao().incrementBlockedTrackerCountBy(batch.blockedCount)
                batch.networkCounts.forEach { (network, delta) -> appDatabase.networkLeaderboardDao().incrementNetworkCountBy(network, delta) }
            }
        }
    }

    companion object {
        /**
         * Long enough to merge a page's tracker burst into one or two transactions, short enough that
         * the stats are still current by the time the user opens a screen that shows them.
         */
        const val FLUSH_WINDOW_MS = 500L
    }
}
