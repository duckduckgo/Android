/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.prefs

import android.content.Context
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import androidx.annotation.VisibleForTesting
import androidx.core.content.edit
import javax.inject.Inject

class VpnPreferences @Inject constructor(
    private val applicationContext: Context,
) {

    var isPrivateDnsEnabled: Boolean
        get() = preferences.getBoolean(PRIVATE_DNS_ENABLED, false)
        set(value) = preferences.edit { putBoolean(PRIVATE_DNS_ENABLED, value) }

    fun isPrivateDnsKey(key: String) = key == PRIVATE_DNS_ENABLED

    fun registerOnSharedPreferenceChangeListener(listener: OnSharedPreferenceChangeListener) {
        preferences.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterOnSharedPreferenceChangeListener(listener: OnSharedPreferenceChangeListener) {
        preferences.unregisterOnSharedPreferenceChangeListener(listener)
    }

    private val preferences: SharedPreferences
        get() = applicationContext.getSharedPreferences(PREFS_FILENAME, Context.MODE_PRIVATE)

    companion object {

        @VisibleForTesting
        const val PREFS_FILENAME = "com.duckduckgo.mobile.android.vpn.prefs"

        private const val PRIVATE_DNS_ENABLED = "private_dns_enabled"
        const val PREFS_KEY_REMINDER_NOTIFICATION_SHOWN = "PREFS_KEY_REMINDER_NOTIFICATION_SHOWN"
    }
}
