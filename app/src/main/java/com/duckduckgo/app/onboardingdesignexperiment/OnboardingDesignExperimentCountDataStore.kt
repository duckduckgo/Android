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

package com.duckduckgo.app.onboardingdesignexperiment

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.duckduckgo.app.onboardingdesignexperiment.di.OnboardingVisitCount
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

interface OnboardingDesignExperimentCountDataStore {
    suspend fun increaseSiteVisitCount(): Int
    suspend fun getSiteVisitCount(): Int
    suspend fun increaseSerpVisitCount(): Int
    suspend fun getSerpVisitCount(): Int
}

private const val KEY_SITE_VISIT_COUNT = "site_visit_count"
private const val KEY_SERP_VISIT_COUNT = "serp_visit_count"

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class SharedPreferencesOnboardingDesignExperimentCountDataStore @Inject constructor(
        private val dispatcherProvider: DispatcherProvider,
        @OnboardingVisitCount private val store: DataStore<Preferences>,
) : OnboardingDesignExperimentCountDataStore {

    override suspend fun increaseSiteVisitCount(): Int {
        return withContext(dispatcherProvider.io()) {
            val currentCount = getSiteVisitCount()
            store.edit { preferences ->
                preferences[intPreferencesKey(KEY_SITE_VISIT_COUNT)] = currentCount + 1
            }
            currentCount + 1
        }
    }

    override suspend fun getSiteVisitCount(): Int {
        return withContext(dispatcherProvider.io()) {
            store.data.map { preferences ->
                preferences[intPreferencesKey(KEY_SITE_VISIT_COUNT)] ?: 0
            }.firstOrNull() ?: 0
        }
    }

    override suspend fun increaseSerpVisitCount(): Int {
        return withContext(dispatcherProvider.io()) {
            val currentCount = getSerpVisitCount()
            store.edit { preferences ->
                preferences[intPreferencesKey(KEY_SERP_VISIT_COUNT)] = currentCount + 1
            }
            currentCount + 1
        }
    }

    override suspend fun getSerpVisitCount(): Int {
        return withContext(dispatcherProvider.io()) {
            store.data.map { preferences ->
                preferences[intPreferencesKey(KEY_SERP_VISIT_COUNT)] ?: 0
            }.firstOrNull() ?: 0
        }
    }
}
