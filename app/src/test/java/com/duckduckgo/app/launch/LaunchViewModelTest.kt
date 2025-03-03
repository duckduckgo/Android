/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.app.launch

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import com.duckduckgo.app.launch.LaunchViewModel.Command.Home
import com.duckduckgo.app.launch.LaunchViewModel.Command.Onboarding
import com.duckduckgo.app.onboarding.store.AppStage
import com.duckduckgo.app.onboarding.store.UserStageStore
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.referral.StubAppReferrerFoundStateListener
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.fakes.FakePixel
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class LaunchViewModelTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val userStageStore = mock<UserStageStore>()
    private val mockCommandObserver: Observer<LaunchViewModel.Command> = mock()

    private val fakePixel: FakePixel = FakePixel()

    private lateinit var testee: LaunchViewModel

    @After
    fun after() {
        testee.command.removeObserver(mockCommandObserver)
    }

    @Test
    fun whenOnboardingShouldShowAndReferrerDataReturnsQuicklyThenCommandIsOnboarding() = runTest {
        testee = LaunchViewModel(
            userStageStore,
            StubAppReferrerFoundStateListener("xx"),
            fakePixel,
        )
        whenever(userStageStore.getUserAppStage()).thenReturn(AppStage.NEW)
        testee.command.observeForever(mockCommandObserver)

        testee.determineViewToShow()

        verify(mockCommandObserver).onChanged(any<Onboarding>())
    }

    @Test
    fun whenOnboardingShouldShowAndReferrerDataReturnsButNotInstantlyThenCommandIsOnboarding() = runTest {
        testee = LaunchViewModel(
            userStageStore,
            StubAppReferrerFoundStateListener("xx", mockDelayMs = 1_000),
            fakePixel,
        )
        whenever(userStageStore.getUserAppStage()).thenReturn(AppStage.NEW)
        testee.command.observeForever(mockCommandObserver)

        testee.determineViewToShow()

        verify(mockCommandObserver).onChanged(any<Onboarding>())
    }

    @Test
    fun whenOnboardingShouldShowAndReferrerDataTimesOutThenCommandIsOnboarding() = runTest {
        testee = LaunchViewModel(
            userStageStore,
            StubAppReferrerFoundStateListener("xx", mockDelayMs = Long.MAX_VALUE),
            fakePixel,
        )
        whenever(userStageStore.getUserAppStage()).thenReturn(AppStage.NEW)
        testee.command.observeForever(mockCommandObserver)

        testee.determineViewToShow()

        verify(mockCommandObserver).onChanged(any<Onboarding>())
    }

    @Test
    fun whenOnboardingShouldNotShowAndReferrerDataReturnsQuicklyThenCommandIsHome() = runTest {
        testee = LaunchViewModel(
            userStageStore,
            StubAppReferrerFoundStateListener("xx"),
            fakePixel,
        )
        whenever(userStageStore.getUserAppStage()).thenReturn(AppStage.DAX_ONBOARDING)
        testee.command.observeForever(mockCommandObserver)
        testee.determineViewToShow()
        verify(mockCommandObserver).onChanged(any<Home>())
    }

    @Test
    fun whenOnboardingShouldNotShowAndReferrerDataReturnsButNotInstantlyThenCommandIsHome() = runTest {
        testee = LaunchViewModel(
            userStageStore,
            StubAppReferrerFoundStateListener("xx", mockDelayMs = 1_000),
            fakePixel,
        )
        whenever(userStageStore.getUserAppStage()).thenReturn(AppStage.DAX_ONBOARDING)
        testee.command.observeForever(mockCommandObserver)
        testee.determineViewToShow()
        verify(mockCommandObserver).onChanged(any<Home>())
    }

    @Test
    fun whenOnboardingShouldNotShowAndReferrerDataTimesOutThenCommandIsHome() = runTest {
        testee = LaunchViewModel(
            userStageStore,
            StubAppReferrerFoundStateListener("xx", mockDelayMs = Long.MAX_VALUE),
            fakePixel,
        )
        whenever(userStageStore.getUserAppStage()).thenReturn(AppStage.DAX_ONBOARDING)
        testee.command.observeForever(mockCommandObserver)
        testee.determineViewToShow()
        verify(mockCommandObserver).onChanged(any<Home>())
    }

    @Test
    fun whenSendingWelcomeScreenPixelThenSplashScreenShownPixelIsSent() = runTest {
        testee = LaunchViewModel(
            userStageStore,
            StubAppReferrerFoundStateListener("xx", mockDelayMs = Long.MAX_VALUE),
            fakePixel,
        )

        testee.sendWelcomeScreenPixel()

        assertEquals(AppPixelName.SPLASHSCREEN_SHOWN.pixelName, fakePixel.firedPixels.first())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun whenLaunchSplashScreenFailToExitJobCalledThenItExecutesFallbackAfterDelay() = runTest {
        whenever(userStageStore.getUserAppStage()).thenReturn(AppStage.ESTABLISHED)

        testee = LaunchViewModel(
            userStageStore,
            StubAppReferrerFoundStateListener("xx", mockDelayMs = Long.MAX_VALUE),
            fakePixel,
        )

        testee.command.observeForever(mockCommandObserver)
        testee.launchSplashScreenFailToExitJob()

        // Wait for fail to exit timeout and referrer timeout
        advanceTimeBy(3.5.seconds)

        assertEquals(AppPixelName.SPLASHSCREEN_SHOWN.pixelName, fakePixel.firedPixels.first())
        verify(mockCommandObserver).onChanged(any<Home>())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun whenCancelSplashScreenFailToExitJobThenStopsFallback() = runTest {
        whenever(userStageStore.getUserAppStage()).thenReturn(AppStage.ESTABLISHED)

        testee = LaunchViewModel(
            userStageStore,
            StubAppReferrerFoundStateListener("xx", mockDelayMs = Long.MAX_VALUE),
            fakePixel,
        )

        testee.command.observeForever(mockCommandObserver)
        testee.launchSplashScreenFailToExitJob()
        testee.cancelSplashScreenFailToExitJob()

        // advance time to ensure that the code does not execute after the delay
        advanceTimeBy(3.seconds)

        assertTrue(fakePixel.firedPixels.isEmpty())
        verifyNoInteractions(mockCommandObserver)
    }
}
