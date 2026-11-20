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

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.duckduckgo.agesignals.impl.AgeSignalsOutcome.Failed
import com.duckduckgo.agesignals.impl.AgeSignalsOutcome.NotApplicable
import com.duckduckgo.agesignals.impl.AgeSignalsOutcome.Shared
import com.duckduckgo.agesignals.impl.AgeSignalsOutcome.UnknownStatus
import com.duckduckgo.agesignals.impl.AgeSignalsOutcome.VerificationRequired
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.CurrentTimeProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDateTime

class AgeSignalsStoreTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val clock = FakeTimeProvider()
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var store: RealAgeSignalsStore

    @Before
    fun setUp() {
        dataStore = PreferenceDataStoreFactory.create(
            scope = coroutineRule.testScope,
            produceFile = { tempFolder.newFile("age_signals.preferences_pb") },
        )
        store = RealAgeSignalsStore(dataStore, coroutineRule.testDispatcherProvider, clock)
    }

    @Test
    fun whenNothingRecordedThenAllSlotsAreEmpty() = runTest {
        assertNull(store.lastOutcome())
        assertNull(store.lastFailure())
        assertNull(store.lastPromptShownAt())
    }

    @Test
    fun whenFailureRecordedAfterOutcomeThenLastOutcomeIsUnchanged() = runTest {
        clock.now = 1_000
        store.record(VerificationRequired)

        clock.now = 2_000
        store.record(Failed(Step.REQUEST_ACCESS, AgeSignalsError.NETWORK_ERROR))

        assertEquals(LastOutcome(VerificationRequired, 1_000), store.lastOutcome())
        assertEquals(LastFailure(Step.REQUEST_ACCESS, AgeSignalsError.NETWORK_ERROR, 2_000), store.lastFailure())
    }

    @Test
    fun whenSecondOutcomeRecordedThenItReplacesTheFirst() = runTest {
        clock.now = 1_000
        store.record(NotApplicable)

        clock.now = 2_000
        store.record(Shared)

        assertEquals(LastOutcome(Shared, 2_000), store.lastOutcome())
        assertNull(store.lastFailure())
    }

    @Test
    fun whenPromptShownThenTimestampIsRecordedWithoutTouchingOtherSlots() = runTest {
        clock.now = 1_000
        store.record(NotApplicable)

        clock.now = 3_000
        store.recordPromptShown()

        assertEquals(3_000L, store.lastPromptShownAt())
        assertEquals(LastOutcome(NotApplicable, 1_000), store.lastOutcome())
    }

    @Test
    fun whenStoredOutcomeNameIsUnknownThenItReadsAsUnknownStatus() = runTest {
        store.record(Shared)
        dataStore.edit { it[stringPreferencesKey("outcome")] = "SOMETHING_NEW" }

        assertEquals(UnknownStatus, store.lastOutcome()?.outcome)
    }

    @Test
    fun whenStoredErrorNameIsUnknownThenItReadsAsUnknownError() = runTest {
        store.record(Failed(Step.CHECK_SIGNALS, AgeSignalsError.APP_NOT_OWNED))
        dataStore.edit { it[stringPreferencesKey("failureError")] = "SOMETHING_NEW" }

        assertEquals(LastFailure(Step.CHECK_SIGNALS, AgeSignalsError.UNKNOWN, clock.now), store.lastFailure())
    }

    @Test
    fun whenStoredFailureStepNameIsUnknownThenThereIsNoLastFailure() = runTest {
        store.record(Failed(Step.CHECK_SIGNALS, AgeSignalsError.APP_NOT_OWNED))
        dataStore.edit { it[stringPreferencesKey("failureStep")] = "SOMETHING_NEW" }

        assertNull(store.lastFailure())
    }

    @Test
    fun whenEveryOutcomeIsRecordedThenItReadsBackUnchanged() = runTest {
        listOf(NotApplicable, AgeSignalsOutcome.NotShared, VerificationRequired, Shared, UnknownStatus).forEach { outcome ->
            store.record(outcome)
            assertEquals(outcome, store.lastOutcome()?.outcome)
        }
    }

    @Test
    fun whenEveryErrorIsRecordedThenItReadsBackUnchanged() = runTest {
        AgeSignalsError.entries.forEach { error ->
            store.record(Failed(Step.REQUEST_ACCESS, error))
            assertEquals(error, store.lastFailure()?.error)
        }
    }

    private class FakeTimeProvider : CurrentTimeProvider {
        var now: Long = 0
        override fun currentTimeMillis(): Long = now
        override fun elapsedRealtime(): Long = now
        override fun localDateTimeNow(): LocalDateTime = LocalDateTime.MIN
    }
}
