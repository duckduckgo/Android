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

import android.app.Activity
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.duckduckgo.agesignals.impl.AgeSignalsError.APP_NOT_OWNED
import com.duckduckgo.agesignals.impl.AgeSignalsError.INTERNAL_ERROR
import com.duckduckgo.agesignals.impl.AgeSignalsError.NETWORK_ERROR
import com.duckduckgo.agesignals.impl.AgeSignalsOutcome.Failed
import com.duckduckgo.agesignals.impl.AgeSignalsOutcome.NotApplicable
import com.duckduckgo.agesignals.impl.AgeSignalsOutcome.NotShared
import com.duckduckgo.agesignals.impl.AgeSignalsOutcome.Shared
import com.duckduckgo.agesignals.impl.AgeSignalsOutcome.UnknownStatus
import com.duckduckgo.agesignals.impl.AgeSignalsOutcome.VerificationRequired
import com.duckduckgo.agesignals.impl.AgeSignalsScenario.APP_NOT_OWNED_AT_ACCESS
import com.duckduckgo.agesignals.impl.AgeSignalsScenario.APP_NOT_OWNED_AT_CHECK
import com.duckduckgo.agesignals.impl.AgeSignalsScenario.APP_NOT_OWNED_AT_FEATURES
import com.duckduckgo.agesignals.impl.AgeSignalsScenario.INTERNAL_ERROR_AT_ACCESS
import com.duckduckgo.agesignals.impl.AgeSignalsScenario.INTERNAL_ERROR_AT_CHECK
import com.duckduckgo.agesignals.impl.AgeSignalsScenario.INTERNAL_ERROR_AT_FEATURES
import com.duckduckgo.agesignals.impl.AgeSignalsScenario.NETWORK_ERROR_AT_ACCESS
import com.duckduckgo.agesignals.impl.AgeSignalsScenario.NETWORK_ERROR_AT_CHECK
import com.duckduckgo.agesignals.impl.AgeSignalsScenario.NETWORK_ERROR_AT_FEATURES
import com.duckduckgo.agesignals.impl.AgeSignalsScenario.NOT_APPLICABLE
import com.duckduckgo.agesignals.impl.AgeSignalsScenario.NOT_SHARED
import com.duckduckgo.agesignals.impl.AgeSignalsScenario.SHARED
import com.duckduckgo.agesignals.impl.AgeSignalsScenario.UNKNOWN_STATUS
import com.duckduckgo.agesignals.impl.AgeSignalsScenario.VERIFICATION_REQUIRED
import com.duckduckgo.agesignals.impl.Step.APPLICABLE_FEATURES
import com.duckduckgo.agesignals.impl.Step.CHECK_SIGNALS
import com.duckduckgo.agesignals.impl.Step.REQUEST_ACCESS
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.android.play.agesignals.AgeSignalsAccessRequest
import com.google.android.play.agesignals.AgeSignalsAccessResult
import com.google.android.play.agesignals.AgeSignalsManager
import com.google.android.play.agesignals.AgeSignalsRequest
import com.google.android.play.agesignals.AgeSignalsResult
import com.google.android.play.agesignals.ApplicableAgeFeaturesRequest
import com.google.android.play.agesignals.ApplicableAgeFeaturesResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.mock
import java.io.File
import java.time.LocalDateTime

class AgeSignalsCheckerTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val activity: Activity = mock()
    private val provider = FakeManagerProvider()
    private lateinit var store: AgeSignalsStore
    private lateinit var checker: RealAgeSignalsChecker

    @Before
    fun setUp() {
        resetStore()
    }

    private fun resetStore() {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = coroutineRule.testScope,
            produceFile = { File(tempFolder.newFolder(), "age_signals.preferences_pb") },
        )
        store = RealAgeSignalsStore(dataStore, coroutineRule.testDispatcherProvider, FakeTimeProvider())
        checker = RealAgeSignalsChecker(provider, store)
    }

    @Test
    fun whenEachScenarioRunsThenItRecordsTheExpectedOutcomeAfterTheExpectedCalls() = runTest {
        val expectations = mapOf(
            NOT_APPLICABLE to Expected(NotApplicable, FEATURES_ONLY),
            NOT_SHARED to Expected(NotShared, UP_TO_ACCESS),
            VERIFICATION_REQUIRED to Expected(VerificationRequired, UP_TO_ACCESS),
            SHARED to Expected(Shared, ALL_CALLS),
            UNKNOWN_STATUS to Expected(UnknownStatus, UP_TO_ACCESS),
            APP_NOT_OWNED_AT_FEATURES to Expected(Failed(APPLICABLE_FEATURES, APP_NOT_OWNED), FEATURES_ONLY),
            NETWORK_ERROR_AT_FEATURES to Expected(Failed(APPLICABLE_FEATURES, NETWORK_ERROR), FEATURES_ONLY),
            INTERNAL_ERROR_AT_FEATURES to Expected(Failed(APPLICABLE_FEATURES, INTERNAL_ERROR), FEATURES_ONLY),
            APP_NOT_OWNED_AT_ACCESS to Expected(Failed(REQUEST_ACCESS, APP_NOT_OWNED), UP_TO_ACCESS),
            NETWORK_ERROR_AT_ACCESS to Expected(Failed(REQUEST_ACCESS, NETWORK_ERROR), UP_TO_ACCESS),
            INTERNAL_ERROR_AT_ACCESS to Expected(Failed(REQUEST_ACCESS, INTERNAL_ERROR), UP_TO_ACCESS),
            APP_NOT_OWNED_AT_CHECK to Expected(Failed(CHECK_SIGNALS, APP_NOT_OWNED), ALL_CALLS),
            NETWORK_ERROR_AT_CHECK to Expected(Failed(CHECK_SIGNALS, NETWORK_ERROR), ALL_CALLS),
            INTERNAL_ERROR_AT_CHECK to Expected(Failed(CHECK_SIGNALS, INTERNAL_ERROR), ALL_CALLS),
        )
        assertEquals(AgeSignalsScenario.entries - AgeSignalsScenario.REAL_PLAY, expectations.keys.toList())

        expectations.forEach { (scenario, expected) ->
            resetStore()
            val manager = provider.use(RecordingManager(scenario.fake!!.toFakeManager()))

            checker.check(activity)

            assertEquals("$scenario calls", expected.calls, manager.calls)
            when (val outcome = expected.outcome) {
                is Failed -> {
                    assertEquals("$scenario", outcome.step to outcome.error, store.lastFailure()?.let { it.step to it.error })
                    assertNull("$scenario", store.lastOutcome())
                }
                else -> {
                    assertEquals("$scenario", outcome, store.lastOutcome()?.outcome)
                    assertNull("$scenario", store.lastFailure())
                }
            }
        }
    }

    @Test
    fun whenSharingIsNotMandatoryThenAccessIsNeverRequested() = runTest {
        val manager = provider.use(RecordingManager(NOT_APPLICABLE.fake!!.toFakeManager()))

        checker.check(activity)

        assertEquals(FEATURES_ONLY, manager.calls)
        assertEquals(NotApplicable, store.lastOutcome()?.outcome)
    }

    @Test
    fun whenAccessIsNotSharedThenSignalsAreNeverChecked() = runTest {
        listOf(NOT_SHARED, VERIFICATION_REQUIRED, UNKNOWN_STATUS).forEach { scenario ->
            val manager = provider.use(RecordingManager(scenario.fake!!.toFakeManager()))

            checker.check(activity)

            assertEquals("$scenario", UP_TO_ACCESS, manager.calls)
        }
    }

    @Test
    fun whenAccessIsRequestedThenTheRequestCarriesTheActivity() = runTest {
        val manager = provider.use(RecordingManager(SHARED.fake!!.toFakeManager()))

        checker.check(activity)

        assertSame(activity, manager.accessRequestActivity)
    }

    @Test(expected = IllegalStateException::class)
    fun whenTheManagerFailsWithAnotherExceptionThenItPropagatesAndNothingIsRecorded() = runTest {
        provider.use(
            object : AgeSignalsManager by SHARED.fake!!.toFakeManager() {
                override fun getApplicableAgeFeatures(request: ApplicableAgeFeaturesRequest): Task<ApplicableAgeFeaturesResult> =
                    Tasks.forException(IllegalStateException("boom"))
            },
        )

        try {
            checker.check(activity)
        } finally {
            assertNull(store.lastOutcome())
            assertNull(store.lastFailure())
        }
    }

    private data class Expected(val outcome: AgeSignalsOutcome, val calls: List<Call>)

    private enum class Call { GET_APPLICABLE_FEATURES, REQUEST_ACCESS, CHECK_SIGNALS }

    private class RecordingManager(private val delegate: AgeSignalsManager) : AgeSignalsManager {
        val calls = mutableListOf<Call>()
        var accessRequestActivity: Activity? = null

        override fun getApplicableAgeFeatures(request: ApplicableAgeFeaturesRequest): Task<ApplicableAgeFeaturesResult> {
            calls += Call.GET_APPLICABLE_FEATURES
            return delegate.getApplicableAgeFeatures(request)
        }

        override fun requestAgeSignalsAccess(request: AgeSignalsAccessRequest): Task<AgeSignalsAccessResult> {
            calls += Call.REQUEST_ACCESS
            accessRequestActivity = request.activity()
            return delegate.requestAgeSignalsAccess(request)
        }

        override fun checkAgeSignals(request: AgeSignalsRequest): Task<AgeSignalsResult> {
            calls += Call.CHECK_SIGNALS
            return delegate.checkAgeSignals(request)
        }
    }

    private class FakeManagerProvider : AgeSignalsManagerProvider {
        private var manager: AgeSignalsManager? = null

        fun <T : AgeSignalsManager> use(manager: T): T = manager.also { this.manager = it }

        override suspend fun get(): AgeSignalsManager = manager!!
    }

    private class FakeTimeProvider : CurrentTimeProvider {
        override fun currentTimeMillis(): Long = 1_000
        override fun elapsedRealtime(): Long = 1_000
        override fun localDateTimeNow(): LocalDateTime = LocalDateTime.MIN
    }

    private companion object {
        val FEATURES_ONLY = listOf(Call.GET_APPLICABLE_FEATURES)
        val UP_TO_ACCESS = listOf(Call.GET_APPLICABLE_FEATURES, Call.REQUEST_ACCESS)
        val ALL_CALLS = listOf(Call.GET_APPLICABLE_FEATURES, Call.REQUEST_ACCESS, Call.CHECK_SIGNALS)
    }
}
