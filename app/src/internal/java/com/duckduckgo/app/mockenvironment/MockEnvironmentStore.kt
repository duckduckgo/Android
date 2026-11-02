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

package com.duckduckgo.app.mockenvironment

import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import javax.inject.Inject

/**
 * Persisted so the flag survives force-stop and is readable in a cold-started process that never ran the test seeder.
 * `pm clear` resets it, and it expires on its own so a crashed run cannot leave a normal launch proxied to a dead port.
 */
class MockEnvironmentStore @Inject constructor(
    sharedPreferencesProvider: SharedPreferencesProvider,
    private val currentTimeProvider: CurrentTimeProvider,
) {

    private val preferences: SharedPreferences by lazy { sharedPreferencesProvider.getSharedPreferences(FILENAME) }

    var isEnabled: Boolean
        get() {
            if (!preferences.getBoolean(KEY_ENABLED, false)) return false
            val age = currentTimeProvider.currentTimeMillis() - preferences.getLong(KEY_ENABLED_AT, 0L)
            if (age in 0..TTL_MS) return true
            isEnabled = false
            fixturePort = 0
            return false
        }

        // commit() so the value is on disk before the harness force-stops the process
        set(value) = preferences.edit(commit = true) {
            putBoolean(KEY_ENABLED, value)
            putLong(KEY_ENABLED_AT, if (value) currentTimeProvider.currentTimeMillis() else 0L)
        }

    /**
     * Loopback port of the fixture server WebView traffic is proxied to.
     */
    var fixturePort: Int
        get() = preferences.getInt(KEY_FIXTURE_PORT, 0)
        set(value) = preferences.edit(commit = true) { putInt(KEY_FIXTURE_PORT, value) }

    companion object {
        /**
         * Well beyond one full run (90 min CI job, 45 min test timeout).
         */
        const val TTL_MS = 3 * 60 * 60 * 1000L

        private const val FILENAME = "com.duckduckgo.app.mockenvironment.v1"
        private const val KEY_ENABLED = "KEY_MOCK_ENVIRONMENT"
        private const val KEY_ENABLED_AT = "KEY_MOCK_ENVIRONMENT_AT"
        private const val KEY_FIXTURE_PORT = "KEY_FIXTURE_PORT"
    }
}
