/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.service.notification

import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface VpnNotificationStore {
    var persistentNotifDimissedTimestamp: Long
}

@ContributesBinding(AppScope::class)
class RealVpnNotificationStore @Inject constructor(
    private val sharedPreferencesProvider: SharedPreferencesProvider,
) : VpnNotificationStore {
    private val prefs: SharedPreferences by lazy {
        sharedPreferencesProvider.getSharedPreferences(FILENAME, multiprocess = true, migrate = false)
    }

    override var persistentNotifDimissedTimestamp: Long
        get() = prefs.getLong(KEY_PERSISTENT_NOTIF_DISMISSED_TIMESTAMP, 0L)
        set(value) {
            prefs.edit { putLong(KEY_PERSISTENT_NOTIF_DISMISSED_TIMESTAMP, value) }
        }

    companion object {
        private const val FILENAME = "com.duckduckgo.mobile.android.vpn.service.notification.v1"
        private const val KEY_PERSISTENT_NOTIF_DISMISSED_TIMESTAMP = "key_persistent_notif_dismissed_timestamp"
    }
}
