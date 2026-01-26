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

package com.duckduckgo.pir.impl.store

import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.data.store.api.SharedPreferencesProvider

interface PirDataStore {
    var mainConfigEtag: String?
    var customStatsPixelsLastSentMs: Long
    var dauLastSentMs: Long
    var wauLastSentMs: Long
    var mauLastSentMs: Long
    var weeklyStatLastSentMs: Long
    var hasBrokerConfigBeenManuallyUpdated: Boolean
    var latestBackgroundScanRunInMs: Long

    fun reset()
    fun resetUserData()
}

internal class RealPirDataStore(
    private val sharedPreferencesProvider: SharedPreferencesProvider,
) : PirDataStore {
    private val preferences: SharedPreferences by lazy {
        sharedPreferencesProvider.getSharedPreferences(
            FILENAME,
            multiprocess = true,
            migrate = false,
        )
    }
    override var mainConfigEtag: String?
        get() = preferences.getString(KEY_MAIN_ETAG, null)
        set(value) {
            preferences.edit {
                putString(KEY_MAIN_ETAG, value)
            }
        }

    override var customStatsPixelsLastSentMs: Long
        get() = preferences.getLong(KEY_CUSTOM_STATS_PIXEL_LAST_SENT_MS, 0L)
        set(value) {
            preferences.edit {
                putLong(KEY_CUSTOM_STATS_PIXEL_LAST_SENT_MS, value)
            }
        }

    override var dauLastSentMs: Long
        get() = preferences.getLong(KEY_ENGAGEMENT_DAU_LAST_MS, 0L)
        set(value) {
            preferences.edit {
                putLong(KEY_ENGAGEMENT_DAU_LAST_MS, value)
            }
        }

    override var wauLastSentMs: Long
        get() = preferences.getLong(KEY_ENGAGEMENT_WAU_LAST_MS, 0L)
        set(value) {
            preferences.edit {
                putLong(KEY_ENGAGEMENT_WAU_LAST_MS, value)
            }
        }

    override var mauLastSentMs: Long
        get() = preferences.getLong(KEY_ENGAGEMENT_MAU_LAST_MS, 0L)
        set(value) {
            preferences.edit {
                putLong(KEY_ENGAGEMENT_MAU_LAST_MS, value)
            }
        }

    override var weeklyStatLastSentMs: Long
        get() = preferences.getLong(KEY_WEEKLY_STATS_LAST_SENT_MS, 0L)
        set(value) {
            preferences.edit {
                putLong(KEY_WEEKLY_STATS_LAST_SENT_MS, value)
            }
        }

    override var hasBrokerConfigBeenManuallyUpdated: Boolean
        get() = preferences.getBoolean(KEY_BROKER_CONFIG_MANUALLY_UPDATED, false)
        set(value) {
            preferences.edit {
                putBoolean(KEY_BROKER_CONFIG_MANUALLY_UPDATED, value)
            }
        }

    override var latestBackgroundScanRunInMs: Long
        get() = preferences.getLong(KEY_LAST_BG_SCAN_RUN, 0L)
        set(value) {
            preferences.edit {
                putLong(KEY_LAST_BG_SCAN_RUN, value)
            }
        }

    override fun reset() {
        mainConfigEtag = null
        hasBrokerConfigBeenManuallyUpdated = false
        resetUserData()
    }

    override fun resetUserData() {
        customStatsPixelsLastSentMs = 0L
        dauLastSentMs = 0L
        wauLastSentMs = 0L
        mauLastSentMs = 0L
        weeklyStatLastSentMs = 0L
        latestBackgroundScanRunInMs = 0L
    }

    companion object {
        private const val FILENAME = "com.duckduckgo.pir.v1"
        private const val KEY_MAIN_ETAG = "KEY_MAIN_ETAG"
        private const val KEY_CUSTOM_STATS_PIXEL_LAST_SENT_MS = "KEY_CUSTOM_STATS_PIXEL_LAST_SENT_MS"
        private const val KEY_ENGAGEMENT_DAU_LAST_MS = "KEY_ENGAGEMENT_DAU_LAST_MS"
        private const val KEY_ENGAGEMENT_WAU_LAST_MS = "KEY_ENGAGEMENT_WAU_LAST_MS"
        private const val KEY_ENGAGEMENT_MAU_LAST_MS = "KEY_ENGAGEMENT_MAU_LAST_MS"
        private const val KEY_WEEKLY_STATS_LAST_SENT_MS = "KEY_WEEKLY_STATS_LAST_SENT_MS"
        private const val KEY_BROKER_CONFIG_MANUALLY_UPDATED = "KEY_BROKER_CONFIG_MANUALLY_UPDATED"
        private const val KEY_LAST_BG_SCAN_RUN = "KEY_LAST_BG_SCAN_RUN_MS"
    }
}
