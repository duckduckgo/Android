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

package com.duckduckgo.networkprotection.internal.network

import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.mobile.android.vpn.prefs.VpnSharedPreferencesProvider
import javax.inject.Inject

class NetPInternalEnvDataStore @Inject constructor(vpnSharedPreferencesProvider: VpnSharedPreferencesProvider) {

    private val preferences: SharedPreferences by lazy {
        vpnSharedPreferencesProvider.getSharedPreferences(FILENAME, multiprocess = true, migrate = false)
    }

    var netpCustomEnvironmentUrl: String?
        get() = preferences.getString(KEY_NETP_ENVIRONMENT_URL, null)
        set(value) = preferences.edit { putString(KEY_NETP_ENVIRONMENT_URL, value) }
    var useNetpCustomEnvironmentUrl: Boolean
        get() = preferences.getBoolean(KEY_NETP_USE_ENVIRONMENT_URL, false)
        set(enabled) = preferences.edit { putBoolean(KEY_NETP_USE_ENVIRONMENT_URL, enabled) }
    var customDns: String?
        get() = preferences.getString(KEY_NETP_CUSTOM_DNS, null)
        set(value) = preferences.edit { putString(KEY_NETP_CUSTOM_DNS, value) }

    companion object {
        private const val FILENAME = "com.duckduckgo.netp.internal.env.store.v1"
        private const val KEY_NETP_ENVIRONMENT_URL = "KEY_NETP_ENVIRONMENT_URL"
        private const val KEY_NETP_USE_ENVIRONMENT_URL = "KEY_NETP_USE_ENVIRONMENT_URL"
        private const val KEY_NETP_CUSTOM_DNS = "KEY_NETP_CUSTOM_DNS"
    }
}
