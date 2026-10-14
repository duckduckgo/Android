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

package com.duckduckgo.app.trackerdetection.api

import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.trackerdetection.WebTrackersBlockedHistory
import com.duckduckgo.app.trackerdetection.db.WebTrackerBlocked
import com.duckduckgo.common.utils.formatters.time.DatabaseDateFormatter
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import java.time.LocalDateTime
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class WebTrackersBlockedAppRepository @Inject constructor(appDatabase: AppDatabase) : WebTrackersBlockedHistory {

    private val dao = appDatabase.webTrackersBlockedDao()

    override suspend fun onTrackerBlocked(trackerUrl: String, trackerCompany: String) {
        dao.insert(WebTrackerBlocked(trackerUrl = trackerUrl, trackerCompany = trackerCompany))
    }

    override suspend fun trackerCountForLast7Days(): Int = dao.getTrackersCountBetween(
        startTime = DatabaseDateFormatter.timestamp(retentionCutoff()),
        endTime = DatabaseDateFormatter.timestamp(LocalDateTime.now()),
    )

    override suspend fun deleteAll() {
        dao.deleteAll()
    }

    override suspend fun deleteExpiredEntries() {
        dao.deleteOldDataUntil(DatabaseDateFormatter.timestamp(retentionCutoff()))
    }
}

/**
 * The count the tab switcher shows and the rows the cleaner keeps have to agree on where the
 * retention window starts, so both read it from here.
 */
private const val RETENTION_DAYS = 7L

private fun retentionCutoff(): LocalDateTime = LocalDateTime.now().minusDays(RETENTION_DAYS)
