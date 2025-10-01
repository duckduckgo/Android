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

package com.duckduckgo.sync.impl.promotion

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.impl.di.SyncPromotion
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import javax.inject.Inject

interface SyncPromotionDataStore {
    suspend fun hasBookmarksPromoBeenDismissed(): Boolean
    suspend fun recordBookmarksPromoDismissed()

    suspend fun hasPasswordsPromoBeenDismissed(): Boolean
    suspend fun recordPasswordsPromoDismissed()
}

@ContributesBinding(AppScope::class)
class SyncPromotionDataStoreImpl @Inject constructor(
    @SyncPromotion private val dataStore: DataStore<Preferences>,
) : SyncPromotionDataStore {

    override suspend fun hasBookmarksPromoBeenDismissed(): Boolean {
        return dataStore.data.map { it[bookmarksPromoDismissedKey] }.firstOrNull() != null
    }

    override suspend fun recordBookmarksPromoDismissed() {
        dataStore.edit { it[bookmarksPromoDismissedKey] = System.currentTimeMillis() }
    }

    override suspend fun hasPasswordsPromoBeenDismissed(): Boolean {
        return dataStore.data.map { it[passwordsPromoDismissedKey] }.firstOrNull() != null
    }

    override suspend fun recordPasswordsPromoDismissed() {
        dataStore.edit { it[passwordsPromoDismissedKey] = System.currentTimeMillis() }
    }

    companion object {
        private val bookmarksPromoDismissedKey = longPreferencesKey("bookmarks_promo_dismissed")
        private val passwordsPromoDismissedKey = longPreferencesKey("passwords_promo_dismissed")
    }
}
