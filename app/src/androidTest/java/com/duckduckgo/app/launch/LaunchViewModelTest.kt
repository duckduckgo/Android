/*
 * Copyright (c) 2018 DuckDuckGo
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
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.launch.LaunchViewModel.Command.Home
import com.duckduckgo.app.launch.LaunchViewModel.Command.Onboarding
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.referral.AppInstallationReferrerStateListener
import com.duckduckgo.app.referral.ParsedReferrerResult
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runBlockingTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.any


@ExperimentalCoroutinesApi
class LaunchViewModelTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private var onboardingStore: OnboardingStore = mock()
    private var mockCommandObserver: Observer<LaunchViewModel.Command> = mock()

    private lateinit var testee: LaunchViewModel

    @After
    fun after() {
        testee.command.removeObserver(mockCommandObserver)
    }

    @Test
    fun whenOnboardingShouldShowAndReferrerDataReturnsQuicklyThenCommandIsOnboarding() = runBlockingTest {
        testee = LaunchViewModel(onboardingStore, StubAppReferrerFoundStateListener("xx"))
        whenever(onboardingStore.shouldShow).thenReturn(true)
        testee.command.observeForever(mockCommandObserver)

        testee.determineViewToShow()

        verify(mockCommandObserver).onChanged(any(Onboarding::class.java))
    }

    @Test
    fun whenOnboardingShouldShowAndReferrerDataReturnsButNotInstantlyThenCommandIsOnboarding() = runBlockingTest {
        testee = LaunchViewModel(onboardingStore, StubAppReferrerFoundStateListener("xx", mockDelayMs = 1_000))
        whenever(onboardingStore.shouldShow).thenReturn(true)
        testee.command.observeForever(mockCommandObserver)

        testee.determineViewToShow()

        verify(mockCommandObserver).onChanged(any(Onboarding::class.java))
    }

    @Test
    fun whenOnboardingShouldShowAndReferrerDataTimesOutThenCommandIsOnboarding() = runBlockingTest {
        testee = LaunchViewModel(onboardingStore, StubAppReferrerFoundStateListener("xx", mockDelayMs = Long.MAX_VALUE))
        whenever(onboardingStore.shouldShow).thenReturn(true)
        testee.command.observeForever(mockCommandObserver)

        testee.determineViewToShow()

        verify(mockCommandObserver).onChanged(any(Onboarding::class.java))
    }

    @Test
    fun whenOnboardingShouldNotShowAndReferrerDataReturnsQuicklyThenCommandIsHome() = runBlockingTest {
        testee = LaunchViewModel(onboardingStore, StubAppReferrerFoundStateListener("xx"))
        whenever(onboardingStore.shouldShow).thenReturn(false)
        testee.command.observeForever(mockCommandObserver)
        testee.determineViewToShow()
        verify(mockCommandObserver).onChanged(any(Home::class.java))
    }

    @Test
    fun whenOnboardingShouldNotShowAndReferrerDataReturnsButNotInstantlyThenCommandIsHome() = runBlockingTest {
        testee = LaunchViewModel(onboardingStore, StubAppReferrerFoundStateListener("xx", mockDelayMs = 1_000))
        whenever(onboardingStore.shouldShow).thenReturn(false)
        testee.command.observeForever(mockCommandObserver)
        testee.determineViewToShow()
        verify(mockCommandObserver).onChanged(any(Home::class.java))
    }

    @Test
    fun whenOnboardingShouldNotShowAndReferrerDataTimesOutThenCommandIsHome() = runBlockingTest {
        testee = LaunchViewModel(onboardingStore, StubAppReferrerFoundStateListener("xx", mockDelayMs = Long.MAX_VALUE))
        whenever(onboardingStore.shouldShow).thenReturn(false)
        testee.command.observeForever(mockCommandObserver)
        testee.determineViewToShow()
        verify(mockCommandObserver).onChanged(any(Home::class.java))
    }

    class StubAppReferrerFoundStateListener(private val referrer: String, private val mockDelayMs: Long = 0) : AppInstallationReferrerStateListener {
        override suspend fun waitForReferrerCode(): ParsedReferrerResult {
            if (mockDelayMs > 0) delay(mockDelayMs)

            return ParsedReferrerResult.ReferrerFound(referrer)
        }

        override fun initialiseReferralRetrieval() {
        }
    }
}