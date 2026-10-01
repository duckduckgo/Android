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

package com.duckduckgo.promptscoordinator.impl.exposure

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.browser.api.install.AppInstall
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

@RunWith(AndroidJUnit4::class)
class PromptExposureWeekTrackerTest {

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private val appInstall: AppInstall = mock()

    private val storeFiles = mutableListOf<File>()
    private lateinit var testDataStore: DataStore<Preferences>
    private lateinit var testee: PromptExposureWeekTracker

    private var installAge: Duration? = 0.days

    @Before
    fun setUp() = runTest {
        whenever(appInstall.getInstallAge()).thenAnswer { installAge }

        testDataStore = createStore()
        testee = PromptExposureWeekTracker(testDataStore, appInstall)
    }

    @After
    fun tearDown() {
        storeFiles.forEach { it.delete() }
    }

    private fun createStore(): DataStore<Preferences> {
        val file = File.createTempFile("prompt_exposure_week_test", ".preferences_pb").also { storeFiles += it }
        return PreferenceDataStoreFactory.create(
            scope = coroutinesTestRule.testScope,
            produceFile = { file },
        )
    }

    @Test
    fun whenFirstEverRunThenStateIsInitialisedAtTheCurrentWeek() = runTest {
        installAge = 10.days

        assertEquals(PromptExposureWeekTracker.Week(weekIndex = 1, daysSinceInstall = 10), testee.currentWeek())
        assertEquals(1, testee.recordPromptShown()?.nthInWeek)
    }

    @Test
    fun whenSameWeekThenRolloverIsANoOp() = runTest {
        repeat(2) { testee.recordPromptShown() }
        installAge = 6.days

        assertEquals(PromptExposureWeekTracker.Week(weekIndex = 0, daysSinceInstall = 6), testee.currentWeek())
        assertEquals(3, testee.recordPromptShown()?.nthInWeek)
    }

    @Test
    fun whenNewWeekThenOrdinalsAndReportedCardsReset() = runTest {
        testee.recordPromptShown()
        testee.recordNtpCardShown("message")
        installAge = 7.days

        assertEquals(PromptExposureWeekTracker.Week(weekIndex = 1, daysSinceInstall = 7), testee.currentWeek())
        assertEquals(1, testee.recordNtpCardShown("message")?.nthInWeek)
    }

    @Test
    fun whenWeeksAreSkippedThenStateResetsAtTheCurrentWeek() = runTest {
        repeat(3) { testee.recordPromptShown() }
        installAge = 22.days

        assertEquals(3L, testee.currentWeek()?.weekIndex)
        assertEquals(1, testee.recordPromptShown()?.nthInWeek)
    }

    @Test
    fun whenPromptsShownThenOrdinalsCountUpAndRestartAfterRollover() = runTest {
        assertEquals(listOf(1, 2, 3), List(3) { testee.recordPromptShown()?.nthInWeek })

        installAge = 7.days

        assertEquals(1, testee.recordPromptShown()?.nthInWeek)
    }

    @Test
    fun whenReporterRunsBeforeObserverInANewWeekThenStateMatchesTheOtherOrder() = runTest {
        val observerFirstStore = createStore()
        val observerFirst = PromptExposureWeekTracker(observerFirstStore, appInstall)
        repeat(2) {
            testee.recordPromptShown()
            observerFirst.recordPromptShown()
        }
        installAge = 7.days

        testee.recordPromptShown()
        testee.currentWeek()
        observerFirst.currentWeek()
        observerFirst.recordPromptShown()

        assertEquals(observerFirstStore.data.first().asMap(), testDataStore.data.first().asMap())
    }

    @Test
    fun whenNtpCardShownTwiceInAWeekThenItCountsOnce() = runTest {
        assertNotNull(testee.recordNtpCardShown("message"))
        assertNull(testee.recordNtpCardShown("message"))
        assertEquals(2, testee.recordNtpCardShown("other")?.nthInWeek)
    }

    @Test
    fun whenNtpCardShownThroughANewTrackerThenItIsStillDeduped() = runTest {
        testee.recordNtpCardShown("message")

        val recreated = PromptExposureWeekTracker(testDataStore, appInstall)

        assertNull(recreated.recordNtpCardShown("message"))
    }

    @Test
    fun whenClockMovesBackAWeekThenTheStoredWeekIsKept() = runTest {
        installAge = 7.days
        testee.recordPromptShown()
        installAge = 6.days

        assertEquals(1L, testee.currentWeek()?.weekIndex)
        assertEquals(2, testee.recordPromptShown()?.nthInWeek)
        installAge = 7.days
        assertEquals(3, testee.recordPromptShown()?.nthInWeek)
    }

    @Test
    fun whenInstallAgeUnknownThenNothingIsRecorded() = runTest {
        installAge = null

        assertNull(testee.currentWeek())
        assertNull(testee.recordPromptShown())
        assertNull(testee.recordNtpCardShown("message"))
        assertEquals(emptyMap<Preferences.Key<*>, Any>(), testDataStore.data.first().asMap())
    }
}
