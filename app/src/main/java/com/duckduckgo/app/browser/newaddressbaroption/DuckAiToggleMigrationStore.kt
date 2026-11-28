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
package com.duckduckgo.app.browser.newaddressbaroption

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.duckduckgo.app.di.DuckAiToggleMigration
import com.duckduckgo.app.di.LegacyAddressBarOption
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Where the toggle migration stands for this install. [NotEvaluated] and [Skipped] are evaluated on each cold start. */
sealed interface MigrationState {
    data object NotEvaluated : MigrationState

    /** Evaluated and left alone: the user was not in the cohort at the last cold start. */
    data object Skipped : MigrationState

    /** The migration turned Search & Duck.ai on at [atMillis]. */
    data class Migrated(val atMillis: Long) : MigrationState

    /** The migration turned it on and the user later undid it: turned it off, or turned Duck.ai off. */
    data object Reverted : MigrationState

    /** A state written by a newer app version. Never acted on, since we can't tell what it means. */
    data object Unrecognized : MigrationState
}

interface DuckAiToggleMigrationStore {
    suspend fun state(): MigrationState
    suspend fun setState(state: MigrationState)
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealDuckAiToggleMigrationStore @Inject constructor(
    @DuckAiToggleMigration private val dataStore: DataStore<Preferences>,
    private val dispatchers: DispatcherProvider,
) : DuckAiToggleMigrationStore {

    override suspend fun state(): MigrationState = withContext(dispatchers.io()) {
        val prefs = dataStore.data.firstOrNull()
        when (prefs?.get(STATE_KEY)) {
            null -> MigrationState.NotEvaluated
            STATE_MIGRATED -> MigrationState.Migrated(atMillis = prefs[MIGRATED_AT_KEY] ?: 0L)
            STATE_REVERTED -> MigrationState.Reverted
            STATE_SKIPPED -> MigrationState.Skipped
            else -> MigrationState.Unrecognized
        }
    }

    override suspend fun setState(state: MigrationState) {
        withContext(dispatchers.io()) {
            dataStore.edit { prefs ->
                when (state) {
                    MigrationState.NotEvaluated -> prefs.remove(STATE_KEY)
                    MigrationState.Skipped -> prefs[STATE_KEY] = STATE_SKIPPED
                    is MigrationState.Migrated -> {
                        prefs[STATE_KEY] = STATE_MIGRATED
                        prefs[MIGRATED_AT_KEY] = state.atMillis
                    }
                    MigrationState.Reverted -> prefs[STATE_KEY] = STATE_REVERTED
                    MigrationState.Unrecognized -> Unit
                }
            }
        }
    }

    private companion object {
        val STATE_KEY = stringPreferencesKey("STATE")
        val MIGRATED_AT_KEY = longPreferencesKey("MIGRATED_AT")
        const val STATE_SKIPPED = "skipped"
        const val STATE_MIGRATED = "migrated"
        const val STATE_REVERTED = "reverted"
    }
}

/** Reads what the V1 address bar picker left on disk. */
interface LegacyAddressBarOptionStore {
    suspend fun wasShown(): Boolean
}

@ContributesBinding(AppScope::class)
class RealLegacyAddressBarOptionStore @Inject constructor(
    @LegacyAddressBarOption private val dataStore: DataStore<Preferences>,
    private val dispatchers: DispatcherProvider,
) : LegacyAddressBarOptionStore {

    override suspend fun wasShown(): Boolean = withContext(dispatchers.io()) {
        dataStore.data.firstOrNull()?.get(WAS_SHOWN_KEY) == true
    }

    private companion object {
        val WAS_SHOWN_KEY = booleanPreferencesKey("NEW_ADDRESS_BAR_OPTION_WAS_SHOWN")
    }
}
