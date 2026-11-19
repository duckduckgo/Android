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

package com.duckduckgo.duckchat.impl.responding

import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.SingleIn
import javax.inject.Inject

/**
 * Persists whether the responding service is running, so a cold start can tell the previous
 * process ended even though the service was keeping it alive.
 */
@SingleIn(AppScope::class)
class DuckAiRespondingServiceRunStore @Inject constructor(
    private val sharedPreferencesProvider: SharedPreferencesProvider,
) {

    private val preferences: SharedPreferences by lazy {
        sharedPreferencesProvider.getSharedPreferences(FILENAME, multiprocess = false, migrate = false)
    }

    fun markRunning(restricted: Boolean) {
        preferences.edit {
            putBoolean(KEY_RUNNING, true)
            putBoolean(KEY_RESTRICTED, restricted)
        }
    }

    fun clear() {
        preferences.edit { clear() }
    }

    /**
     * Returns the interrupted run, or null if there was none. Clears it, so each interrupted run is returned once.
     */
    fun takeInterruptedRun(): InterruptedRun? {
        if (!preferences.getBoolean(KEY_RUNNING, false)) return null
        val run = InterruptedRun(restricted = preferences.getBoolean(KEY_RESTRICTED, false))
        clear()
        return run
    }

    private companion object {
        const val FILENAME = "com.duckduckgo.duckchat.responding.service"
        const val KEY_RUNNING = "running"
        const val KEY_RESTRICTED = "restricted"
    }
}

/**
 * The app process was killed while the responding service was running. [restricted] is whether battery usage was Restricted when it started.
 */
data class InterruptedRun(val restricted: Boolean)
