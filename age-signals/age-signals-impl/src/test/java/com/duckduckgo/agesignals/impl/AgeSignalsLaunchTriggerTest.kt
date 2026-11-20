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

import android.annotation.SuppressLint
import android.app.Activity
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.duckduckgo.agesignals.impl.AgeSignalsOutcome.Failed
import com.duckduckgo.agesignals.impl.AgeSignalsOutcome.Shared
import com.duckduckgo.agesignals.impl.AgeSignalsPixelName.CHECK_COUNT
import com.duckduckgo.agesignals.impl.AgeSignalsPixelName.CHECK_DAILY
import com.duckduckgo.agesignals.impl.AgeSignalsPixelName.PROMPT_SHOWN_COUNT
import com.duckduckgo.agesignals.impl.AgeSignalsPixelName.PROMPT_SHOWN_DAILY
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter.PETAL
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Count
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Daily
import com.duckduckgo.app.statistics.pixels.Pixel.PixelValues.PETAL_RANDOMIZE
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import com.google.android.play.agesignals.AgeSharingConsentWrapperActivity
import com.google.android.play.agesignals.AgeSignalsManager
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import java.time.LocalDateTime

@SuppressLint("DenyListedApi")
class AgeSignalsLaunchTriggerTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val feature = FakeFeatureToggleFactory.create(AgeSignalsFeature::class.java)
    private val checker = FakeChecker()
    private val managerProvider = FakeManagerProvider()
    private val pixel: Pixel = mock()
    private lateinit var store: AgeSignalsStore
    private lateinit var trigger: AgeSignalsLaunchTrigger

    @Before
    fun setUp() {
        feature.self().setRawStoredState(Toggle.State(enable = true))
        val dataStore = PreferenceDataStoreFactory.create(
            scope = coroutineRule.testScope,
            produceFile = { tempFolder.newFile("age_signals.preferences_pb") },
        )
        store = RealAgeSignalsStore(dataStore, coroutineRule.testDispatcherProvider, FixedTimeProvider())
        trigger = trigger(checker)
    }

    private fun trigger(checker: AgeSignalsChecker) = AgeSignalsLaunchTrigger(
        feature,
        checker,
        store,
        managerProvider,
        AgeSignalsPixels(pixel),
        coroutineRule.testScope,
        coroutineRule.testDispatcherProvider,
    )

    @Test
    fun whenTwoActivitiesResumeThenTheCheckRunsOnce() {
        trigger.onActivityResumed(mock<Activity>())
        trigger.onActivityResumed(mock<Activity>())

        assertEquals(1, checker.runs)
    }

    @Test
    fun whenTheToggleIsOffThenTheCheckDoesNotRun() {
        feature.self().setRawStoredState(Toggle.State(enable = false))

        trigger.onActivityResumed(mock<Activity>())

        assertEquals(0, checker.runs)
    }

    @Test
    fun whenTheActivityIsRecreatedThenTheCheckDoesNotRunAgain() {
        val first = mock<Activity>()
        trigger.onActivityResumed(first)
        trigger.onActivityPaused(first)
        trigger.onActivityDestroyed(first)

        trigger.onActivityResumed(mock<Activity>())

        assertEquals(1, checker.runs)
    }

    @Test
    fun whenTheCheckAsksForTheActivityThenItGetsTheOneResumedNow() {
        val first = mock<Activity>()
        val second = mock<Activity>()
        trigger.onActivityResumed(first)

        trigger.onActivityPaused(first)
        trigger.onActivityResumed(second)

        assertSame(second, checker.currentActivity!!())
    }

    @Test
    fun whenNoActivityIsResumedThenTheCheckGetsNoActivity() {
        val activity = mock<Activity>()
        trigger.onActivityResumed(activity)

        trigger.onActivityPaused(activity)

        assertNull(checker.currentActivity!!())
    }

    @Test
    fun whenAnotherActivityPausesThenTheResumedOneIsStillSupplied() {
        val resumed = mock<Activity>()
        trigger.onActivityResumed(resumed)

        trigger.onActivityPaused(mock<Activity>())

        assertSame(resumed, checker.currentActivity!!())
    }

    @Test
    fun whenTheConsentActivityResumesThenThePromptIsRecorded() = runTest {
        trigger.onActivityResumed(mock<Activity>())

        trigger.onActivityResumed(mock<AgeSharingConsentWrapperActivity>())

        assertEquals(NOW, store.lastPromptShownAt())
    }

    @Test
    fun whenTheConsentActivityResumesWithTheToggleOffThenThePromptIsStillRecorded() = runTest {
        feature.self().setRawStoredState(Toggle.State(enable = false))

        trigger.onActivityResumed(mock<AgeSharingConsentWrapperActivity>())

        assertEquals(NOW, store.lastPromptShownAt())
    }

    @Test
    fun whenOtherActivitiesResumeThenNoPromptIsRecorded() = runTest {
        trigger.onActivityResumed(mock<Activity>())
        trigger.onActivityResumed(mock<Activity>())

        assertNull(store.lastPromptShownAt())
    }

    @Test
    fun whenTheLaunchRunRecordsASuccessThenTheCheckPixelReportsSuccess() {
        checker.outcome = Shared

        trigger.onActivityResumed(mock<Activity>())

        verifyCheckPixel(mapOf("result" to "success", PETAL to PETAL_RANDOMIZE), "m_age_signals_check_daily:success")
    }

    @Test
    fun whenTheLaunchRunRecordsAFailureThenTheCheckPixelReportsTheErrorCode() {
        checker.outcome = Failed(Step.REQUEST_ACCESS, AgeSignalsError.TIMEOUT)

        trigger.onActivityResumed(mock<Activity>())

        verifyCheckPixel(
            mapOf("result" to "failure", "error_code" to "timeout", PETAL to PETAL_RANDOMIZE),
            "m_age_signals_check_daily:failure:timeout",
        )
    }

    @Test
    fun whenTheLaunchRunRecordsNothingThenNoPixelFires() {
        checker.outcome = null

        trigger.onActivityResumed(mock<Activity>())

        assertEquals(1, checker.runs)
        verifyNoInteractions(pixel)
    }

    @Test
    fun whenTheToggleIsOffThenNoPixelFires() {
        feature.self().setRawStoredState(Toggle.State(enable = false))

        trigger.onActivityResumed(mock<Activity>())

        verifyNoInteractions(pixel)
    }

    @Test
    fun whenAFakeScenarioIsActiveThenTheLaunchRunFiresNoPixel() {
        managerProvider.fake = true

        trigger.onActivityResumed(mock<Activity>())

        assertEquals(1, checker.runs)
        verifyNoInteractions(pixel)
    }

    @Test
    fun whenTheCheckerRunsOutsideTheLaunchTriggerThenNoPixelFires() = runTest {
        val realChecker = RealAgeSignalsChecker(managerProvider, store)

        val outcome = realChecker.check { mock<Activity>() }

        assertEquals(Shared, outcome)
        verifyNoInteractions(pixel)

        trigger(realChecker).onActivityResumed(mock<Activity>())

        verifyCheckPixel(mapOf("result" to "success", PETAL to PETAL_RANDOMIZE), "m_age_signals_check_daily:success")
    }

    @Test
    fun whenTheConsentActivityResumesThenThePromptPixelFires() {
        feature.self().setRawStoredState(Toggle.State(enable = false))

        trigger.onActivityResumed(mock<AgeSharingConsentWrapperActivity>())

        verify(pixel).fire(eq(PROMPT_SHOWN_COUNT), eq(mapOf(PETAL to PETAL_RANDOMIZE)), any(), eq(Count))
        verify(pixel).fire(eq(PROMPT_SHOWN_DAILY), eq(mapOf(PETAL to PETAL_RANDOMIZE)), any(), eq(Daily()))
    }

    @Test
    fun whenTheConsentActivityResumesWithAFakeScenarioThenNoPromptPixelFires() = runTest {
        feature.self().setRawStoredState(Toggle.State(enable = false))
        managerProvider.fake = true

        trigger.onActivityResumed(mock<AgeSharingConsentWrapperActivity>())

        assertEquals(NOW, store.lastPromptShownAt())
        verifyNoInteractions(pixel)
    }

    private fun verifyCheckPixel(parameters: Map<String, String>, dailyTag: String) {
        verify(pixel).fire(eq(CHECK_COUNT), eq(parameters), any(), eq(Count))
        verify(pixel).fire(eq(CHECK_DAILY), eq(parameters), any(), eq(Daily(dailyTag)))
    }

    private class FakeChecker : AgeSignalsChecker {
        var runs = 0
        var currentActivity: (() -> Activity?)? = null
        var outcome: AgeSignalsOutcome? = Shared

        override suspend fun check(currentActivity: () -> Activity?): AgeSignalsOutcome? {
            runs++
            this.currentActivity = currentActivity
            return outcome
        }
    }

    private class FakeManagerProvider : AgeSignalsManagerProvider {
        var fake = false

        override suspend fun get(): AgeSignalsManager = AgeSignalsScenario.SHARED.fake!!.toFakeManager()

        override suspend fun isFakeScenarioActive(): Boolean = fake
    }

    private class FixedTimeProvider : CurrentTimeProvider {
        override fun currentTimeMillis(): Long = NOW
        override fun elapsedRealtime(): Long = NOW
        override fun localDateTimeNow(): LocalDateTime = LocalDateTime.MIN
    }

    private companion object {
        const val NOW = 5_000L
    }
}
