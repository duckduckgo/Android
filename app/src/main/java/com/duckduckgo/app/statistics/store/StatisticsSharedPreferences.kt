/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.statistics.store

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.app.statistics.model.Atb
import javax.inject.Inject

class StatisticsSharedPreferences @Inject constructor(private val context: Context) :
    StatisticsDataStore {

    override var variant: String?
        get() = preferences.getString(KEY_VARIANT, null)
        set(value) = preferences.edit { putString(KEY_VARIANT, value) }

    override val hasInstallationStatistics: Boolean
        get() = preferences.contains(KEY_ATB) && preferences.contains(KEY_RETENTION_ATB)

    override var atb: Atb?
        get() {
            val atbString = preferences.getString(KEY_ATB, null) ?: return null
            return Atb(atbString)
        }
        set(atb) = preferences.edit { putString(KEY_ATB, atb?.version) }

    override var retentionAtb: String?
        get() = preferences.getString(KEY_RETENTION_ATB, null)
        set(value) = preferences.edit { putString(KEY_RETENTION_ATB, value) }

    override var httpsUpgradesTotal: Int
        get() = preferences.getInt(KEY_HTTPS_UPGRADES_TOTAL, 0)
        set(value) = preferences.edit { putInt(KEY_HTTPS_UPGRADES_TOTAL, value) }

    override var httpsUpgradesFailures: Int
        get() = preferences.getInt(KEY_HTTPS_UPGRADES_FAILURES, 0)
        set(value) = preferences.edit { putInt(KEY_HTTPS_UPGRADES_FAILURES, value) }

    override fun saveAtb(atb: Atb) {
        preferences.edit {
            putString(KEY_ATB, atb.version)
            putString(KEY_RETENTION_ATB, atb.version)
        }
    }

    override fun clearAtb() {
        preferences.edit {
            putString(KEY_ATB, null)
            putString(KEY_RETENTION_ATB, null)
        }
    }

    private val preferences: SharedPreferences
        get() = context.getSharedPreferences(FILENAME, Context.MODE_PRIVATE)

    companion object {
        private const val FILENAME = "com.duckduckgo.app.statistics"
        private const val KEY_ATB = "com.duckduckgo.app.statistics.atb"
        private const val KEY_RETENTION_ATB = "com.duckduckgo.app.statistics.retentionatb"
        private const val KEY_VARIANT = "com.duckduckgo.app.statistics.variant"
        private const val KEY_HTTPS_UPGRADES_TOTAL = "com.duckduckgo.app.statistics.httpsupgradestotal"
        private const val KEY_HTTPS_UPGRADES_FAILURES = "com.duckduckgo.app.statistics.httpsupgradesfailures"
    }
}
