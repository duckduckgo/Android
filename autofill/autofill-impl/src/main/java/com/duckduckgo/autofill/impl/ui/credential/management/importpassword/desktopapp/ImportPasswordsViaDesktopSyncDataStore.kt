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

package com.duckduckgo.autofill.impl.ui.credential.management.importpassword.desktopapp

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject

interface ImportPasswordsViaDesktopSyncDataStore {

    suspend fun startUserJourney()
    suspend fun getUserJourneyStartTime(): Long?
    suspend fun clearUserJourneyStartTime()
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class ImportPasswordsViaDesktopSyncDataStoreImpl @Inject constructor(
    @ImportPasswordDesktopSync private val store: DataStore<Preferences>,
) : ImportPasswordsViaDesktopSyncDataStore {

    private val userJourneyStartTime = longPreferencesKey("user_journey_started_timestamp")

    override suspend fun startUserJourney() {
        store.edit {
            it[userJourneyStartTime] = System.currentTimeMillis()
        }
    }

    override suspend fun getUserJourneyStartTime(): Long? {
        return store.data.firstOrNull()?.get(userJourneyStartTime)
    }

    override suspend fun clearUserJourneyStartTime() {
        store.edit {
            it.remove(userJourneyStartTime)
        }
    }
}
