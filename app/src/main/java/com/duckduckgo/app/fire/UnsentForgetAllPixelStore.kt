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

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import androidx.core.content.edit
import javax.inject.Inject

interface UnsentForgetAllPixelStore {
    val pendingPixelCountClearData: Int
    val lastClearTimestamp: Long

    fun incrementCount()
    fun resetCount()
}

/**
 * Stores information about unsent clear data Pixels.
 *
 * When writing values here to SharedPreferences, it is crucial to use `commit = true`. As otherwise
 * the change can be lost in the process restart.
 */
class UnsentForgetAllPixelStoreSharedPreferences @Inject constructor(private val context: Context) :
    UnsentForgetAllPixelStore {

    override val pendingPixelCountClearData: Int
        get() = preferences.getInt(KEY_UNSENT_CLEAR_PIXELS, 0)

    override val lastClearTimestamp: Long
        get() = preferences.getLong(KEY_TIMESTAMP_LAST_CLEARED, 0L)

    override fun incrementCount() {
        val updated = pendingPixelCountClearData + 1

        preferences.edit(commit = true) {
            putInt(KEY_UNSENT_CLEAR_PIXELS, updated)
            putLong(KEY_TIMESTAMP_LAST_CLEARED, System.currentTimeMillis())
        }
    }

    override fun resetCount() {
        preferences.edit(commit = true) { putInt(KEY_UNSENT_CLEAR_PIXELS, 0) }
    }

    private val preferences: SharedPreferences
        get() = context.getSharedPreferences(FILENAME, Context.MODE_PRIVATE)

    companion object {

        @VisibleForTesting const val FILENAME = "com.duckduckgo.app.fire.unsentpixels.settings"
        const val KEY_UNSENT_CLEAR_PIXELS = "KEY_UNSENT_CLEAR_PIXELS"
        const val KEY_TIMESTAMP_LAST_CLEARED = "KEY_TIMESTAMP_LAST_CLEARED"
    }
}
