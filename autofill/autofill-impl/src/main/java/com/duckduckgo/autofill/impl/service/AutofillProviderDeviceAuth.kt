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

package com.duckduckgo.autofill.impl.service

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.duckduckgo.autofill.impl.deviceauth.AutofillDeviceAuthStore
import com.duckduckgo.autofill.impl.time.TimeProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.flow.firstOrNull
import logcat.LogPriority.VERBOSE
import logcat.logcat
import javax.inject.Inject

interface AutofillProviderDeviceAuth {
    suspend fun isAuthRequired(): Boolean
    suspend fun recordSuccessfulAuthorization()
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealAutofillProviderDeviceAuth @Inject constructor(
    private val timeProvider: TimeProvider,
    @AutofillDeviceAuthStore private val store: DataStore<Preferences>,
) : AutofillProviderDeviceAuth {

    private val lastAuthTimeKey = longPreferencesKey("last_auth_time_key")

    override suspend fun isAuthRequired(): Boolean {
        val lastAuthTime = store.data.firstOrNull()?.get(lastAuthTimeKey) ?: 0L
        if (lastAuthTime == 0L) return true

        val timeSinceLastAuth = timeProvider.currentTimeMillis() - lastAuthTime
        if (timeSinceLastAuth <= AUTH_GRACE_EXTENDED_PERIOD_MS) {
            logcat(VERBOSE) { "Within grace period; auth not required" }
            return false
        }
        return true
    }

    override suspend fun recordSuccessfulAuthorization() {
        store.edit {
            it[lastAuthTimeKey] = timeProvider.currentTimeMillis()
        }
    }

    companion object {
        private const val AUTH_GRACE_EXTENDED_PERIOD_MS = 180_000
    }
}
