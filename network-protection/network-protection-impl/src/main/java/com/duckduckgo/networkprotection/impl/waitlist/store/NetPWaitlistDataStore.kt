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

package com.duckduckgo.networkprotection.impl.waitlist.store

import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.prefs.VpnSharedPreferencesProvider
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface NetPWaitlistDataStore {
    var settingUnlocked: Boolean
    var authToken: String?
    var waitlistToken: String?
    var waitlistTimestamp: Int
    var didAcceptedTerms: Boolean

    fun clear()
}

@ContributesBinding(AppScope::class)
class NetPWaitlistDataStoreSharedPreferences @Inject constructor(
    private val vpnSharedPreferencesProvider: VpnSharedPreferencesProvider,
) : NetPWaitlistDataStore {

    private val preferences: SharedPreferences by lazy {
        vpnSharedPreferencesProvider.getSharedPreferences(
            FILENAME,
            multiprocess = true,
            migrate = false,
        )
    }

    override var settingUnlocked: Boolean
        get() = preferences.getBoolean(KEY_SETTING_UNLOCKED, false)
        set(value) {
            preferences.edit(commit = true) {
                putBoolean(KEY_SETTING_UNLOCKED, value)
            }
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
    override var waitlistToken: String?
        get() = preferences.getString(KEY_WAITLIST_TOKEN, null)
        set(value) {
            preferences.edit(commit = true) {
                if (value == null) {
                    remove(KEY_WAITLIST_TOKEN)
                } else {
                    putString(KEY_WAITLIST_TOKEN, value)
                }
            }
        }

    override var waitlistTimestamp: Int
        get() = preferences.getInt(KEY_WAITLIST_TIMESTAMP, -1)
        set(value) {
            preferences.edit(commit = true) {
                putInt(KEY_WAITLIST_TIMESTAMP, value)
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
        const val KEY_SETTING_UNLOCKED = "KEY_SETTING_UNLOCKED"
        const val KEY_AUTH_TOKEN = "KEY_AUTH_TOKEN"
        const val KEY_WAITLIST_TOKEN = "KEY_WAITLIST_TOKEN"
        const val KEY_WAITLIST_TIMESTAMP = "KEY_WAITLIST_TIMESTAMP"
        const val KEY_WAITLIST_ACCEPTED_TERMS = "KEY_WAITLIST_ACCEPTED_TERMS"
    }
}
