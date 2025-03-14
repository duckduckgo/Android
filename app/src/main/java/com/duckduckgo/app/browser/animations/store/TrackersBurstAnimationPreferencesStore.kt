/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.app.browser.animations.store

import androidx.core.content.edit
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlinx.coroutines.withContext

interface TrackersBurstAnimationPreferencesStore {
    suspend fun fetchCount(): Int
    suspend fun incrementCount()
}

@ContributesBinding(AppScope::class)
class RealTrackersBurstAnimationPreferencesStore @Inject constructor(
    private val sharedPreferencesProvider: SharedPreferencesProvider,
    private val dispatcher: DispatcherProvider,
) : TrackersBurstAnimationPreferencesStore {

    private val preferences by lazy {
        sharedPreferencesProvider.getSharedPreferences(FILENAME)
    }

    override suspend fun fetchCount(): Int =
        withContext(dispatcher.io()) {
            preferences.getInt(TRACKERS_BURST_COUNT, 0)
        }

    override suspend fun incrementCount() {
        withContext(dispatcher.io()) {
            val count = fetchCount() + 1
            preferences.edit {
                putInt(TRACKERS_BURST_COUNT, count)
            }
        }
    }

    companion object {
        const val FILENAME = "com.duckduckgo.app.trackersburstanimation"
        const val TRACKERS_BURST_COUNT = "TRACKERS_BURST_COUNT"
    }
}
