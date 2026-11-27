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
import com.duckduckgo.app.trackerdetection.db.WebTrackerBlocked
import com.duckduckgo.app.trackerdetection.db.WebTrackersBlockedDao
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

/**
 * Accumulates blocked-tracker history rows in memory and inserts them in one `tracker_detection.db` transaction per
 * [FLUSH_WINDOW_MS] burst, instead of one transaction per tracker request on the database's single connection.
 *
 * Rows queued in the window are lost if the process dies before the flush.
 */
@SingleIn(AppScope::class)
class WebTrackersBlockedWriter @Inject constructor(
    private val webTrackersBlockedDao: WebTrackersBlockedDao,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
) {

    private val lock = Any()
    private var pending = mutableListOf<WebTrackerBlocked>()
    private var flushScheduled = false

    // Keeps flushes strictly sequential even if a slow one overlaps the next window.
    private val writeMutex = Mutex()

    /**
     * Queues a history row, stamped with the time of this call.
     */
    fun record(trackerUrl: String, trackerCompany: String) {
        // Built here so the persisted timestamp is the request time, not the flush time.
        val row = WebTrackerBlocked(trackerUrl = trackerUrl, trackerCompany = trackerCompany)
        val shouldSchedule = synchronized(lock) {
            pending.add(row)
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

    /**
     * Drops queued rows and deletes the persisted ones. A flush already in progress either completes
     * before the delete or has its rows discarded, so no row recorded before this call survives it.
     */
    suspend fun deleteAll() {
        writeMutex.withLock {
            synchronized(lock) { pending.clear() }
            webTrackersBlockedDao.deleteAll()
        }
    }

    private suspend fun flush() {
        writeMutex.withLock {
            // Swapped inside the mutex so a concurrent deleteAll can't be followed by a stale batch insert.
            val batch = synchronized(lock) {
                flushScheduled = false
                pending.also { pending = mutableListOf() }
            }
            if (batch.isNotEmpty()) webTrackersBlockedDao.insertAll(batch)
        }
    }

    companion object {
        /**
         * Long enough to merge a page's tracker burst into one or two transactions, short enough that
         * the count is still current by the time the user opens the tab switcher.
         */
        const val FLUSH_WINDOW_MS = 500L
    }
}
