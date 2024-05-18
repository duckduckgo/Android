/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.networkprotection.store

import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.data.store.api.SharedPreferencesProvider

interface NetpDataStore {
    var authToken: String?
    var didAcceptedTerms: Boolean

    fun clear()
}

class NetpDataStoreSharedPreferences constructor(
    private val sharedPreferencesProvider: SharedPreferencesProvider,
) : NetpDataStore {

    private val preferences: SharedPreferences by lazy {
        sharedPreferencesProvider.getSharedPreferences(
            FILENAME,
            multiprocess = true,
            migrate = false,
        )
    }

    override var authToken: String?
        get() = preferences.getString(KEY_AUTH_TOKEN, null)
        set(value) {
            preferences.edit(commit = true) {
                if (value == null) {
                    remove(KEY_AUTH_TOKEN)
                } else {
                    putString(KEY_AUTH_TOKEN, value)
                }
            }
        }
    override var didAcceptedTerms: Boolean
        get() = preferences.getBoolean(KEY_WAITLIST_ACCEPTED_TERMS, false)
        set(value) {
            preferences.edit(commit = true) {
                putBoolean(KEY_WAITLIST_ACCEPTED_TERMS, value)
            }
        }

    override fun clear() {
        preferences.edit { clear() }
    }

    companion object {
        const val FILENAME = "com.duckduckgo.netp.store.waitlist"
        const val KEY_AUTH_TOKEN = "KEY_AUTH_TOKEN"
        const val KEY_WAITLIST_ACCEPTED_TERMS = "KEY_WAITLIST_ACCEPTED_TERMS"
    }
}
