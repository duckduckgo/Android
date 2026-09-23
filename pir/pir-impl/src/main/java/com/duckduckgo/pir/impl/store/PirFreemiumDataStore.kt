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

package com.duckduckgo.pir.impl.store

import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.data.store.api.SharedPreferencesProvider

interface PirFreemiumDataStore {
    /**
     * Whether the user has activated freemium PIR by saving a profile through the dashboard.
     */
    val didActivate: Boolean

    /** First-write-wins. Anchors the bounded window during which a free user still gets background scans. */
    val firstProfileSavedTimestamp: Long

    /** No-op once [didActivate] is already true, so the timestamp it is paired with can never be re-anchored. */
    fun activate(timestampMillis: Long)

    fun reset()
}

internal class RealPirFreemiumDataStore(
    private val sharedPreferencesProvider: SharedPreferencesProvider,
) : PirFreemiumDataStore {
    private val preferences: SharedPreferences by lazy {
        sharedPreferencesProvider.getSharedPreferences(
            FILENAME,
            multiprocess = true,
            migrate = false,
        )
    }

    override val didActivate: Boolean
        get() = preferences.getBoolean(KEY_DID_ACTIVATE, false)

    override val firstProfileSavedTimestamp: Long
        get() = preferences.getLong(KEY_FIRST_PROFILE_SAVED_TIMESTAMP, 0L)

    override fun activate(timestampMillis: Long) {
        if (didActivate) return

        // both keys committed in one write as both dashboard (:main) and scan (:pir) read them one after another
        preferences.edit(commit = true) {
            putBoolean(KEY_DID_ACTIVATE, true)
            putLong(KEY_FIRST_PROFILE_SAVED_TIMESTAMP, timestampMillis)
        }
    }

    override fun reset() {
        preferences.edit(commit = true) {
            putBoolean(KEY_DID_ACTIVATE, false)
            putLong(KEY_FIRST_PROFILE_SAVED_TIMESTAMP, 0L)
        }
    }

    companion object {
        private const val FILENAME = "com.duckduckgo.pir.freemium.v1"
        private const val KEY_DID_ACTIVATE = "KEY_DID_ACTIVATE"
        private const val KEY_FIRST_PROFILE_SAVED_TIMESTAMP = "KEY_FIRST_PROFILE_SAVED_TIMESTAMP"
    }
}
