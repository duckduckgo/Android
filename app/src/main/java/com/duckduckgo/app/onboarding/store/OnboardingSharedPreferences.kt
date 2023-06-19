/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.onboarding.store

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

class OnboardingSharedPreferences @Inject constructor(private val context: Context) : OnboardingStore {
    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = FILENAME)
    private val PREF_ONBOARDING by lazy { stringPreferencesKey(ONBOARDING_JOURNEY) }

    override suspend fun setOnboardingDialogJourney(dialogJourney: String) {
        set(PREF_ONBOARDING, value = dialogJourney)
    }

    override suspend fun getOnBoardingDialogJourney(): Flow<String?> =
        get(PREF_ONBOARDING)

    companion object {
        const val FILENAME = "com.duckduckgo.app.onboarding.settings"
        const val ONBOARDING_JOURNEY = "onboardingJourney"
    }

    private suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        context.dataStore.edit { settings ->
            settings[key] = value
        }
    }

    private fun <T> get(key: Preferences.Key<T>): Flow<T?> {
        return context.dataStore.data.map { settings ->
            settings[key]
        }
    }
}
