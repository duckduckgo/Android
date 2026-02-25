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

package com.duckduckgo.app.startup_metrics.impl.store

import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

/**
 * Storage for startup metrics data.
 */
interface StartupMetricsDataStore {
    /**
     * Gets the launch timestamp of the last collected startup metrics.
     * Returns 0 if no metrics have been collected yet.
     *
     * @return Launch timestamp in milliseconds
     */
    fun getLastCollectedLaunchTime(): Long

    /**
     * Stores the launch timestamp when startup metrics were collected.
     *
     * @param launchTimeMs Launch timestamp in milliseconds
     */
    fun setLastCollectedLaunchTime(launchTimeMs: Long)
}

@ContributesBinding(AppScope::class)
class RealStartupMetricsDataStore @Inject constructor(
    private val sharedPreferencesProvider: SharedPreferencesProvider,
) : StartupMetricsDataStore {

    private val preferences: SharedPreferences by lazy {
        sharedPreferencesProvider.getSharedPreferences(PREFS_FILENAME, multiprocess = false, migrate = false)
    }

    override fun getLastCollectedLaunchTime(): Long {
        return preferences.getLong(KEY_LAST_COLLECTED_LAUNCH_TIME, 0L)
    }

    override fun setLastCollectedLaunchTime(launchTimeMs: Long) {
        preferences.edit {
            putLong(KEY_LAST_COLLECTED_LAUNCH_TIME, launchTimeMs)
        }
    }

    companion object {
        private const val PREFS_FILENAME = "com.duckduckgo.app.startup_metrics.prefs"
        private const val KEY_LAST_COLLECTED_LAUNCH_TIME = "last_collected_launch_time_ms"
    }
}
