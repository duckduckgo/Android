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

package com.duckduckgo.agesignals.impl

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Qualifier

interface AgeSignalsScenarioStore {
    suspend fun scenario(): AgeSignalsScenario
    suspend fun setScenario(scenario: AgeSignalsScenario)
}

@ContributesBinding(AppScope::class)
class RealAgeSignalsScenarioStore @Inject constructor(
    @AgeSignalsDev private val store: DataStore<Preferences>,
    private val dispatchers: DispatcherProvider,
) : AgeSignalsScenarioStore {

    override suspend fun scenario(): AgeSignalsScenario = withContext(dispatchers.io()) {
        val name = store.data.firstOrNull()?.get(SCENARIO)
        AgeSignalsScenario.entries.firstOrNull { it.name == name } ?: AgeSignalsScenario.REAL_PLAY
    }

    override suspend fun setScenario(scenario: AgeSignalsScenario) {
        withContext(dispatchers.io()) {
            store.edit { it[SCENARIO] = scenario.name }
        }
    }

    private companion object {
        val SCENARIO = stringPreferencesKey("scenario")
    }
}

@ContributesTo(AppScope::class)
@BindingContainer
object AgeSignalsScenarioStoreModule {
    private val Context.ageSignalsDevDataStore: DataStore<Preferences> by preferencesDataStore(
        name = "age_signals_dev",
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
    )

    @Provides
    @AgeSignalsDev
    fun provideAgeSignalsDevDataStore(context: Context): DataStore<Preferences> = context.ageSignalsDevDataStore
}

@Qualifier
annotation class AgeSignalsDev
