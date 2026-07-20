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

package com.duckduckgo.app.fire

import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import androidx.core.content.edit
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import javax.inject.Inject

interface UnsentForgetAllPixelStore {
    val pendingPixelCountsClearData: Map<BrowserMode, Int>
    val pendingPixelCountClearData: Int
        get() = pendingPixelCountsClearData.values.sum()
    val lastClearTimestamp: Long

    fun incrementCount(mode: BrowserMode)
    fun resetCount(mode: BrowserMode)
}

/**
 * Stores information about unsent clear data Pixels.
 *
 * When writing values here to SharedPreferences, it is crucial to use `commit = true`. As otherwise the change can be lost in the process restart.
 */
class UnsentForgetAllPixelStoreSharedPreferences @Inject constructor(
    private val sharedPreferencesProvider: SharedPreferencesProvider,
) : UnsentForgetAllPixelStore {

    override val pendingPixelCountsClearData: Map<BrowserMode, Int>
        get() = BrowserMode.entries
            .associateWith(::pendingPixelCount)
            .filterValues { it > 0 }

    override val lastClearTimestamp: Long
        get() = preferences.getLong(KEY_TIMESTAMP_LAST_CLEARED, 0L)

    override fun incrementCount(mode: BrowserMode) {
        val updated = pendingPixelCount(mode) + 1

        preferences.edit(commit = true) {
            putInt(keyFor(mode), updated)
            putLong(KEY_TIMESTAMP_LAST_CLEARED, System.currentTimeMillis())
        }
    }

    override fun resetCount(mode: BrowserMode) {
        preferences.edit(commit = true) {
            putInt(keyFor(mode), 0)
        }
    }

    private fun pendingPixelCount(mode: BrowserMode): Int {
        val key = keyFor(mode)
        return when {
            preferences.contains(key) -> preferences.getInt(key, 0)
            mode == BrowserMode.REGULAR -> preferences.getInt(KEY_UNSENT_CLEAR_PIXELS, 0)
            else -> 0
        }
    }

    private fun keyFor(mode: BrowserMode): String = "${KEY_UNSENT_CLEAR_PIXELS}_${mode.name}"

    private val preferences: SharedPreferences by lazy { sharedPreferencesProvider.getSharedPreferences(FILENAME) }

    companion object {

        @VisibleForTesting
        const val FILENAME = "com.duckduckgo.app.fire.unsentpixels.settings"
        const val KEY_UNSENT_CLEAR_PIXELS = "KEY_UNSENT_CLEAR_PIXELS"
        const val KEY_TIMESTAMP_LAST_CLEARED = "KEY_TIMESTAMP_LAST_CLEARED"
    }
}
