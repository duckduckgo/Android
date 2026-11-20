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
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.duckduckgo.agesignals.impl.AgeSignalsOutcome.Failed
import com.duckduckgo.agesignals.impl.AgeSignalsOutcome.NotApplicable
import com.duckduckgo.agesignals.impl.AgeSignalsOutcome.NotShared
import com.duckduckgo.agesignals.impl.AgeSignalsOutcome.Shared
import com.duckduckgo.agesignals.impl.AgeSignalsOutcome.UnknownStatus
import com.duckduckgo.agesignals.impl.AgeSignalsOutcome.VerificationRequired
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Qualifier

internal data class LastOutcome(val outcome: AgeSignalsOutcome, val outcomeAt: Long)

internal data class LastFailure(val step: Step, val error: AgeSignalsError, val failureAt: Long)

internal interface AgeSignalsStore {
    /** A [Failed] outcome goes to [lastFailure] and leaves [lastOutcome] untouched. */
    suspend fun record(outcome: AgeSignalsOutcome)
    suspend fun recordPromptShown()
    suspend fun lastOutcome(): LastOutcome?
    suspend fun lastFailure(): LastFailure?
    suspend fun lastPromptShownAt(): Long?
}

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
internal class RealAgeSignalsStore @Inject constructor(
    @AgeSignals private val store: DataStore<Preferences>,
    private val dispatchers: DispatcherProvider,
    private val timeProvider: CurrentTimeProvider,
) : AgeSignalsStore {

    override suspend fun record(outcome: AgeSignalsOutcome) {
        val now = timeProvider.currentTimeMillis()
        withContext(dispatchers.io()) {
            store.edit { prefs ->
                when (outcome) {
                    is Failed -> {
                        prefs[FAILURE_STEP] = outcome.step.name
                        prefs[FAILURE_ERROR] = outcome.error.name
                        prefs[FAILURE_AT] = now
                    }
                    NotApplicable -> prefs.setOutcome(NOT_APPLICABLE, now)
                    NotShared -> prefs.setOutcome(NOT_SHARED, now)
                    VerificationRequired -> prefs.setOutcome(VERIFICATION_REQUIRED, now)
                    Shared -> prefs.setOutcome(SHARED, now)
                    UnknownStatus -> prefs.setOutcome(UNKNOWN_STATUS, now)
                }
            }
        }
    }

    override suspend fun recordPromptShown() {
        val now = timeProvider.currentTimeMillis()
        withContext(dispatchers.io()) {
            store.edit { it[PROMPT_SHOWN_AT] = now }
        }
    }

    override suspend fun lastOutcome(): LastOutcome? {
        val prefs = read()
        val name = prefs[OUTCOME] ?: return null
        val at = prefs[OUTCOME_AT] ?: return null
        return LastOutcome(outcomeFromStoredName(name), at)
    }

    override suspend fun lastFailure(): LastFailure? {
        val prefs = read()
        val step = prefs[FAILURE_STEP]?.let { name -> Step.entries.firstOrNull { it.name == name } } ?: return null
        val error = prefs[FAILURE_ERROR] ?: return null
        val at = prefs[FAILURE_AT] ?: return null
        return LastFailure(step, AgeSignalsError.entries.firstOrNull { it.name == error } ?: AgeSignalsError.UNKNOWN, at)
    }

    override suspend fun lastPromptShownAt(): Long? = read()[PROMPT_SHOWN_AT]

    private suspend fun read(): Preferences = withContext(dispatchers.io()) { store.data.firstOrNull() ?: emptyPreferences() }

    private fun MutablePreferences.setOutcome(name: String, at: Long) {
        this[OUTCOME] = name
        this[OUTCOME_AT] = at
    }

    private fun outcomeFromStoredName(name: String): AgeSignalsOutcome = when (name) {
        NOT_APPLICABLE -> NotApplicable
        NOT_SHARED -> NotShared
        VERIFICATION_REQUIRED -> VerificationRequired
        SHARED -> Shared
        else -> UnknownStatus
    }

    private companion object {
        const val NOT_APPLICABLE = "NOT_APPLICABLE"
        const val NOT_SHARED = "NOT_SHARED"
        const val VERIFICATION_REQUIRED = "VERIFICATION_REQUIRED"
        const val SHARED = "SHARED"
        const val UNKNOWN_STATUS = "UNKNOWN_STATUS"

        val OUTCOME = stringPreferencesKey("outcome")
        val OUTCOME_AT = longPreferencesKey("outcomeAt")
        val FAILURE_STEP = stringPreferencesKey("failureStep")
        val FAILURE_ERROR = stringPreferencesKey("failureError")
        val FAILURE_AT = longPreferencesKey("failureAt")
        val PROMPT_SHOWN_AT = longPreferencesKey("promptShownAt")
    }
}

@ContributesTo(AppScope::class)
@BindingContainer
internal object AgeSignalsStoreModule {
    private val Context.ageSignalsDataStore: DataStore<Preferences> by preferencesDataStore(name = "age_signals")

    @Provides
    @AgeSignals
    fun provideAgeSignalsDataStore(context: Context): DataStore<Preferences> = context.ageSignalsDataStore
}

@Qualifier
internal annotation class AgeSignals
