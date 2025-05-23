/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.autofill.impl.service.store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.flow.firstOrNull
import logcat.LogPriority.INFO
import logcat.logcat

interface AutofillServiceStore {
    suspend fun isDefaultAutofillProvider(): Boolean
    suspend fun updateDefaultAutofillProvider(isDefault: Boolean)
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealAutofillServiceStore @Inject constructor(
    @AutofillServiceDataStore private val store: DataStore<Preferences>,
) : AutofillServiceStore {

    override suspend fun isDefaultAutofillProvider(): Boolean {
        return store.data.firstOrNull()?.get(enabledByUserKey) ?: false
    }

    override suspend fun updateDefaultAutofillProvider(isDefault: Boolean) {
        logcat(INFO) { "DDGAutofillService updating default autofill provider to $isDefault" }
        store.edit {
            it[enabledByUserKey] = isDefault
        }
    }

    companion object {
        private val enabledByUserKey = booleanPreferencesKey("enabled_by_user_key")
    }
}
