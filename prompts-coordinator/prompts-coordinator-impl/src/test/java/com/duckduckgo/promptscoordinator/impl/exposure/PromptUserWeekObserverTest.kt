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
import androidx.lifecycle.LifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.onboarding.OnboardingFlowChecker
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.browser.api.install.AppInstall
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

@RunWith(AndroidJUnit4::class)
class PromptUserWeekObserverTest {

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private val appInstall: AppInstall = mock()
    private val onboardingFlowChecker: OnboardingFlowChecker = mock()
    private val pixel: Pixel = mock()
    private val lifecycleOwner: LifecycleOwner = mock()

    private lateinit var testDataStoreFile: File
    private lateinit var testDataStore: DataStore<Preferences>
    private lateinit var weekTracker: PromptExposureWeekTracker
    private lateinit var testee: PromptUserWeekObserver

    private var installAge: Duration? = 0.days
    private var onboardingComplete = true

    @Before
    fun setUp() = runTest {
        whenever(appInstall.getInstallAge()).thenAnswer { installAge }
        whenever(onboardingFlowChecker.isOnboardingComplete()).thenAnswer { onboardingComplete }

        testDataStoreFile = File.createTempFile("prompt_user_week_test", ".preferences_pb")
        testDataStore = PreferenceDataStoreFactory.create(
            scope = coroutinesTestRule.testScope,
            produceFile = { testDataStoreFile },
        )
        weekTracker = PromptExposureWeekTracker(testDataStore, appInstall)
        testee = PromptUserWeekObserver(
            appCoroutineScope = coroutinesTestRule.testScope,
            weekTracker = weekTracker,
            onboardingFlowChecker = onboardingFlowChecker,
            pixel = pixel,
            dispatchers = coroutinesTestRule.testDispatcherProvider,
        )
    }

    @After
    fun tearDown() {
        testDataStoreFile.delete()
    }

    @Test
    fun whenForegroundedThenDenominatorFiresTaggedWithTheWeekIndex() = runTest {
        installAge = 8.days

        resume()

        verify(pixel).fire(
            PromptExposurePixelName.PROMPT_USER_WEEK,
            mapOf("days_since_install" to "d7_13", "opens_prev_week" to "0"),
            type = Pixel.PixelType.Unique(tag = "prompt_user_week_1"),
        )
    }

    @Test
    fun whenNewWeekStartsThenDenominatorCarriesTheOpensOfTheWeekBefore() = runTest {
        repeat(3) { resume() }
        installAge = 7.days

        resume()

        verify(pixel).fire(
            PromptExposurePixelName.PROMPT_USER_WEEK,
            mapOf("days_since_install" to "d7_13", "opens_prev_week" to "3_5"),
            type = Pixel.PixelType.Unique(tag = "prompt_user_week_1"),
        )
    }

    @Test
    fun whenOnboardingIncompleteThenDenominatorDoesNotFireButOpensAreCounted() = runTest {
        onboardingComplete = false

        repeat(2) { resume() }

        verify(pixel, never()).fire(any<Pixel.PixelName>(), any(), any(), any())
        installAge = 7.days
        assertOpensPrevWeek(2)
    }

    @Test
    fun whenOnboardingCompletesMidWeekThenDenominatorFiresThatWeek() = runTest {
        onboardingComplete = false
        resume()
        onboardingComplete = true

        resume()

        verify(pixel).fire(
            PromptExposurePixelName.PROMPT_USER_WEEK,
            mapOf("days_since_install" to "d0_6", "opens_prev_week" to "0"),
            type = Pixel.PixelType.Unique(tag = "prompt_user_week_0"),
        )
    }

    @Test
    fun whenInstallAgeUnknownThenNothingFires() = runTest {
        installAge = null

        resume()

        verify(pixel, never()).fire(any<Pixel.PixelName>(), any(), any(), any())
    }

    private fun resume() {
        testee.onResume(lifecycleOwner)
        coroutinesTestRule.testScope.testScheduler.advanceUntilIdle()
    }

    private suspend fun assertOpensPrevWeek(expected: Int) {
        org.junit.Assert.assertEquals(expected, weekTracker.recordPromptShown()?.opensPrevWeek)
    }
}
