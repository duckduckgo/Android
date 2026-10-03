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

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.LifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.onboarding.OnboardingFlowChecker
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.browser.api.install.AppInstall
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

@RunWith(AndroidJUnit4::class)
class PromptExposureLifecycleObserverTest {

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private val appInstall: AppInstall = mock()
    private val onboardingFlowChecker: OnboardingFlowChecker = mock()
    private val pixel: Pixel = mock()
    private val lifecycleOwner: LifecycleOwner = mock()
    private val session = PromptExposureSession()
    private val random = FixedRandom(SAMPLED_IN)

    private lateinit var testDataStoreFile: File
    private lateinit var weekTracker: PromptExposureWeekTracker
    private lateinit var testee: PromptExposureLifecycleObserver

    private var installAge: Duration? = 8.days
    private var onboardingComplete = true

    @Before
    fun setUp() = runTest {
        whenever(appInstall.getInstallAge()).thenAnswer { installAge }
        whenever(onboardingFlowChecker.isOnboardingComplete()).thenAnswer { onboardingComplete }

        testDataStoreFile = File.createTempFile("prompt_exposure_lifecycle_test", ".preferences_pb")
        weekTracker = PromptExposureWeekTracker(
            PreferenceDataStoreFactory.create(
                scope = coroutinesTestRule.testScope,
                produceFile = { testDataStoreFile },
            ),
            appInstall,
        )
        testee = PromptExposureLifecycleObserver(
            appCoroutineScope = coroutinesTestRule.testScope,
            weekTracker = weekTracker,
            session = session,
            onboardingFlowChecker = onboardingFlowChecker,
            appInstall = appInstall,
            pixel = pixel,
            dispatchers = coroutinesTestRule.testDispatcherProvider,
            random = random,
        )
    }

    @After
    fun tearDown() {
        testDataStoreFile.delete()
    }

    @Test
    fun whenForegroundedThenDenominatorFiresTaggedWithTheWeekIndexAndWithoutPetal() = runTest {
        resume()

        verify(pixel).fire(
            PromptExposurePixelName.PROMPT_USER_WEEK,
            mapOf("days_since_install" to "d7_13", "version" to "1"),
            type = Pixel.PixelType.Unique(tag = "prompt_user_week_1"),
        )
    }

    @Test
    fun whenForegroundedThroughTheWeeksThenTheUniqueTagOnlyChangesWithTheWeek() = runTest {
        resume()
        installAge = 13.days
        resume()
        installAge = 14.days
        resume()

        verify(pixel, times(2)).fire(eq(PromptExposurePixelName.PROMPT_USER_WEEK), any(), any(), eq(userWeek(1)))
        verify(pixel).fire(eq(PromptExposurePixelName.PROMPT_USER_WEEK), any(), any(), eq(userWeek(2)))
    }

    @Test
    fun whenOnboardingIncompleteThenDenominatorDoesNotFire() = runTest {
        onboardingComplete = false

        resume()

        verify(pixel, never()).fire(any<Pixel.PixelName>(), any(), any(), any())
    }

    @Test
    fun whenOnboardingCompletesMidWeekThenDenominatorFiresThatWeek() = runTest {
        onboardingComplete = false
        resume()
        onboardingComplete = true

        resume()

        verify(pixel).fire(eq(PromptExposurePixelName.PROMPT_USER_WEEK), any(), any(), eq(userWeek(1)))
    }

    @Test
    fun whenSessionShowedAPromptThenItEndsWithPromptShownTrue() = runTest {
        testee.onStart(lifecycleOwner)
        session.markPromptShown()

        stayInBackground(MIN_BACKGROUND)

        verifySessionPixel(promptShown = true)
    }

    @Test
    fun whenSessionShowedNoPromptThenItEndsWithPromptShownFalse() = runTest {
        testee.onStart(lifecycleOwner)

        stayInBackground(MIN_BACKGROUND)

        verifySessionPixel(promptShown = false)
    }

    @Test
    fun whenPromptReportedJustBeforeGoingToTheBackgroundThenItCounts() = runTest {
        val reporter = RealPromptExposureReporter(
            appCoroutineScope = coroutinesTestRule.testScope,
            weekTracker = weekTracker,
            session = session,
            pixel = pixel,
            dispatchers = coroutinesTestRule.testDispatcherProvider,
        )
        testee.onStart(lifecycleOwner)

        reporter.reportPromptShown("win_back_prompt")
        stayInBackground(MIN_BACKGROUND)

        verifySessionPixel(promptShown = true)
    }

    @Test
    fun whenAppReturnsWithinMinBackgroundThenTheSessionCarriesOnWithItsFlag() = runTest {
        session.markPromptShown()
        stayInBackground(MIN_BACKGROUND - 1.milliseconds)
        testee.onStart(lifecycleOwner)
        advanceBy(MIN_BACKGROUND)

        verify(pixel, never()).fire(eq(PromptExposurePixelName.PROMPT_SESSION), any(), any(), any())

        stayInBackground(MIN_BACKGROUND)

        verifySessionPixel(promptShown = true)
    }

    @Test
    fun whenAwayLongerThanMinBackgroundThenOnePixelFiresAndTheNextSessionStartsClean() = runTest {
        session.markPromptShown()
        stayInBackground(30.minutes)
        testee.onStart(lifecycleOwner)

        stayInBackground(MIN_BACKGROUND)

        verify(pixel).fire(PromptExposurePixelName.PROMPT_SESSION, sessionParams(promptShown = true))
        verify(pixel).fire(PromptExposurePixelName.PROMPT_SESSION, sessionParams(promptShown = false))
    }

    @Test
    fun whenAppReturnsWhileTheEndedSessionIsBeingReportedThenItsPixelStillFires() = runTest {
        val onboardingCheck = CompletableDeferred<Boolean>()
        whenever(onboardingFlowChecker.isOnboardingComplete()).doSuspendableAnswer { onboardingCheck.await() }
        session.markPromptShown()
        stayInBackground(MIN_BACKGROUND)

        testee.onStart(lifecycleOwner)
        onboardingCheck.complete(true)
        advanceBy(Duration.ZERO)

        verifySessionPixel(promptShown = true)
    }

    @Test
    fun whenOnboardingIncompleteThenNoSessionPixelFiresButTheFlagIsReset() = runTest {
        onboardingComplete = false
        session.markPromptShown()

        stayInBackground(MIN_BACKGROUND)

        verify(pixel, never()).fire(any<Pixel.PixelName>(), any(), any(), any())
        assertFalse(session.consumePromptShown())
    }

    @Test
    fun whenSessionSampledOutThenNoPixelFiresAndTheNextSessionStartsClean() = runTest {
        random.value = SESSION_SAMPLE_RATE
        session.markPromptShown()
        stayInBackground(MIN_BACKGROUND)

        verify(pixel, never()).fire(eq(PromptExposurePixelName.PROMPT_SESSION), any(), any(), any())

        random.value = SAMPLED_IN
        testee.onStart(lifecycleOwner)
        stayInBackground(MIN_BACKGROUND)

        verifySessionPixel(promptShown = false)
    }

    @Test
    fun whenInstallAgeUnknownThenNoSessionPixelFires() = runTest {
        installAge = null

        stayInBackground(MIN_BACKGROUND)

        verify(pixel, never()).fire(any<Pixel.PixelName>(), any(), any(), any())
    }

    private fun resume() {
        testee.onResume(lifecycleOwner)
        advanceBy(Duration.ZERO)
    }

    private fun stayInBackground(duration: Duration) {
        testee.onStop(lifecycleOwner)
        advanceBy(duration)
    }

    private fun advanceBy(duration: Duration) {
        coroutinesTestRule.testScope.testScheduler.advanceTimeBy(duration)
        coroutinesTestRule.testScope.testScheduler.runCurrent()
    }

    private fun verifySessionPixel(promptShown: Boolean) {
        verify(pixel).fire(PromptExposurePixelName.PROMPT_SESSION, sessionParams(promptShown))
    }

    private fun sessionParams(promptShown: Boolean) = mapOf(
        "days_since_install" to "d7_13",
        "prompt_shown" to promptShown.toString(),
        "version" to "1",
        Pixel.PixelParameter.PETAL to Pixel.PixelValues.PETAL_RANDOMIZE,
    )

    private fun userWeek(weekIndex: Int) = Pixel.PixelType.Unique(tag = "prompt_user_week_$weekIndex")

    private class FixedRandom(var value: Double) : Random() {
        override fun nextBits(bitCount: Int): Int = 0
        override fun nextDouble(): Double = value
    }

    private companion object {
        const val SAMPLED_IN = 0.0
    }
}
