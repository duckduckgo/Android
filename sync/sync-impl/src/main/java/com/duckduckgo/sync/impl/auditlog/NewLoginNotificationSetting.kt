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

package com.duckduckgo.sync.impl.auditlog

import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface NewLoginNotificationSetting {
    /**
     * The user's choice in Sync settings. On until the user turns it off. It says nothing about whether the app can post.
     */
    suspend fun isEnabled(): Boolean

    suspend fun setEnabled(enabled: Boolean)

    suspend fun isCardDismissed(): Boolean

    suspend fun dismissCard()
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealNewLoginNotificationSetting @Inject constructor(
    private val sharedPreferencesProvider: SharedPreferencesProvider,
    private val dispatchers: DispatcherProvider,
) : NewLoginNotificationSetting {

    private val preferences: SharedPreferences by lazy { sharedPreferencesProvider.getSharedPreferences(FILENAME) }

    override suspend fun isEnabled(): Boolean = withContext(dispatchers.io()) {
        preferences.getBoolean(KEY_ENABLED, true)
    }

    override suspend fun setEnabled(enabled: Boolean) = withContext(dispatchers.io()) {
        preferences.edit { putBoolean(KEY_ENABLED, enabled) }
    }

    override suspend fun isCardDismissed(): Boolean = withContext(dispatchers.io()) {
        preferences.getBoolean(KEY_CARD_DISMISSED, false)
    }

    override suspend fun dismissCard() = withContext(dispatchers.io()) {
        preferences.edit { putBoolean(KEY_CARD_DISMISSED, true) }
    }

    private companion object {
        const val FILENAME = "com.duckduckgo.sync.newLoginNotification.v1"
        const val KEY_ENABLED = "enabled"
        const val KEY_CARD_DISMISSED = "card_dismissed"
    }
}
