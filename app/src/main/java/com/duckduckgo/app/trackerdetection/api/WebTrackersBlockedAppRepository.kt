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
import com.duckduckgo.app.trackerdetection.db.WebTrackerBlocked
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@ContributesBinding(AppScope::class)
class WebTrackersBlockedAppRepository @Inject constructor(appDatabase: AppDatabase) : WebTrackersBlockedRepository {

    private val dao = appDatabase.webTrackersBlockedDao()

    override fun get(
        startTime: () -> String,
        endTime: String
    ): Flow<List<WebTrackerBlocked>> {
        return dao.getTrackersBetween(startTime(), endTime)
            .distinctUntilChanged()
            .map { it.filter { tracker -> tracker.timestamp >= startTime() } }
    }
}
