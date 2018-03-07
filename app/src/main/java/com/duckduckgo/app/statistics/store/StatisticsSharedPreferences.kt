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
import javax.inject.Inject

class StatisticsSharedPreferences @Inject constructor(private val context: Context) :
    StatisticsDataStore {

    override val hasInstallationStatistics: Boolean
        get() = preferences.contains(KEY_ATB) && preferences.contains(KEY_RETENTION_ATB)

    override var atb: String?
        get() = preferences.getString(KEY_ATB, null)
        set(atb) = preferences.edit().putString(KEY_ATB, atb).apply()

    override var retentionAtb: String?
        get() = preferences.getString(KEY_RETENTION_ATB, null)
        set(retentionAtb) = preferences.edit().putString(KEY_RETENTION_ATB, retentionAtb).apply()

    private val preferences: SharedPreferences
        get() = context.getSharedPreferences(FILENAME, Context.MODE_PRIVATE)

    companion object {
        const val FILENAME = "com.duckduckgo.app.statistics"
        const val KEY_ATB = "com.duckduckgo.app.statistics.atb"
        const val KEY_RETENTION_ATB = "com.duckduckgo.app.statistics.retentionatb"
    }
}
