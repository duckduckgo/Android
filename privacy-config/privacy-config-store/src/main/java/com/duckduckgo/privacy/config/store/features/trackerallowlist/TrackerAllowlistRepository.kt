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

package com.duckduckgo.privacy.config.store.features.trackerallowlist

import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.privacy.config.store.TrackerAllowlistEntity
import com.duckduckgo.privacy.config.store.PrivacyConfigDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

interface TrackerAllowlistRepository {
    fun updateAll(exceptions: List<TrackerAllowlistEntity>)
    val exceptions: ArrayList<TrackerAllowlistEntity>
}

class RealTrackerAllowlistRepository(database: PrivacyConfigDatabase, coroutineScope: CoroutineScope, dispatcherProvider: DispatcherProvider) :
    TrackerAllowlistRepository {

    private val trackerAllowlistDao: TrackerAllowlistDao = database.trackerAllowlistDao()
    override val exceptions = ArrayList<TrackerAllowlistEntity>()

    init {
        coroutineScope.launch(dispatcherProvider.io()) {
            loadToMemory()
        }
    }

    override fun updateAll(exceptions: List<TrackerAllowlistEntity>) {
        trackerAllowlistDao.updateAll(exceptions)
        loadToMemory()
    }

    private fun loadToMemory() {
        exceptions.clear()
        trackerAllowlistDao.getAll().map {
            exceptions.add(it)
        }
    }
}
