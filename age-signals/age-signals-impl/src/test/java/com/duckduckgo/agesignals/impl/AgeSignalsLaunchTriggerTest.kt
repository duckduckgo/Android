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
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import com.google.android.play.agesignals.AgeSharingConsentWrapperActivity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.mock
import java.time.LocalDateTime

@SuppressLint("DenyListedApi")
class AgeSignalsLaunchTriggerTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val feature = FakeFeatureToggleFactory.create(AgeSignalsFeature::class.java)
    private val checker = FakeChecker()
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
        trigger = AgeSignalsLaunchTrigger(feature, checker, store, coroutineRule.testScope, coroutineRule.testDispatcherProvider)
    }

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

    private class FakeChecker : AgeSignalsChecker {
        var runs = 0
        var currentActivity: (() -> Activity?)? = null

        override suspend fun check(currentActivity: () -> Activity?) {
            runs++
            this.currentActivity = currentActivity
        }
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
