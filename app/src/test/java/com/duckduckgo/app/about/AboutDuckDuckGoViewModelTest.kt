/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.app.about

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.about.AboutDuckDuckGoViewModel.*
import com.duckduckgo.app.about.AboutDuckDuckGoViewModel.Companion.MAX_EASTER_EGG_COUNT
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.networkprotection.impl.waitlist.NetPWaitlistState
import com.duckduckgo.networkprotection.impl.waitlist.store.NetPWaitlistRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
internal class AboutDuckDuckGoViewModelTest {
    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private lateinit var testee: AboutDuckDuckGoViewModel

    @Mock
    private lateinit var mockNetPWaitlistRepository: NetPWaitlistRepository

    @Mock
    private lateinit var mockAppBuildConfig: AppBuildConfig

    @Mock
    private lateinit var mockVariantManager: VariantManager

    @Before
    fun before() {
        MockitoAnnotations.openMocks(this)

        whenever(mockVariantManager.getVariant()).thenReturn(VariantManager.DEFAULT_VARIANT)
        whenever(mockAppBuildConfig.versionName).thenReturn("name")
        whenever(mockAppBuildConfig.versionCode).thenReturn(1)
        whenever(mockNetPWaitlistRepository.getState(any())).thenReturn(NetPWaitlistState.NotUnlocked)

        testee = AboutDuckDuckGoViewModel(
            mockNetPWaitlistRepository,
            mockAppBuildConfig,
            mockVariantManager,
        )
    }

    @Test
    fun whenStartCalledThenViewStateEmittedWithDefaultValues() = runTest {
        testee.start()

        testee.viewState().test {
            val value = awaitItem()

            assertEquals("name (1)", value.version)
            assertEquals(NetPWaitlistState.NotUnlocked, value.networkProtectionWaitlistState)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenOnLearnMoreLinkClickedThenCommandLaunchBrowserWithLearnMoreUrlIsSent() = runTest {
        testee.commands().test {
            testee.onLearnMoreLinkClicked()

            assertEquals(Command.LaunchBrowserWithLearnMoreUrl, awaitItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenOnPrivacyPolicyClickedThenCommandLaunchWebViewWithPrivacyPolicyUrlIsSent() = runTest {
        testee.commands().test {
            testee.onPrivacyPolicyClicked()

            assertEquals(Command.LaunchWebViewWithPrivacyPolicyUrl, awaitItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenVersionClickedAndNetPWaitlistStateIsOtherThanNotUnlockedThenNoCommandIsSent() = runTest {
        whenever(mockNetPWaitlistRepository.getState(any())).thenReturn(NetPWaitlistState.InBeta)
        testee.start()

        testee.commands().test {
            testee.onVersionClicked()

            expectNoEvents()
        }
    }

    @Test
    fun whenVersionClickedLessThanMaxTimesAndNetPWaitlistStateIsNotUnlockedThenNoCommandIsSent() = runTest {
        whenever(mockNetPWaitlistRepository.getState(any())).thenReturn(NetPWaitlistState.NotUnlocked)
        testee.start()

        testee.commands().test {
            testee.onVersionClicked()

            expectNoEvents()
        }
    }

    @Test
    fun whenVersionClickedMaxTimesAndNetPWaitlistStateIsNotUnlockedThenCommandShowNetPUnlockedSnackbarIsSentAndCounterReset() = runTest {
        whenever(mockNetPWaitlistRepository.getState(any())).thenReturn(NetPWaitlistState.NotUnlocked)
        testee.start()

        testee.commands().test {
            for (i in 1..MAX_EASTER_EGG_COUNT) {
                testee.onVersionClicked()
            }

            assertEquals(Command.ShowNetPUnlockedSnackbar, awaitItem())
            assertTrue(testee.hasResetNetPEasterEggCounter())

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenOnProvideFeedbackClickedThenCommandLaunchFeedbackIsSent() = runTest {
        testee.commands().test {
            testee.onProvideFeedbackClicked()

            assertEquals(Command.LaunchFeedback, awaitItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenOnNetPUnlockedActionClickedThenCommandLaunchNetPWaitlistIsSent() = runTest {
        testee.commands().test {
            testee.onNetPUnlockedActionClicked()

            assertEquals(Command.LaunchNetPWaitlist, awaitItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenResetNetPEasterEggCounterIsCalledThenNetPEasterEggCounterIsZero() = runTest {
        testee.onVersionClicked()
        assertFalse(testee.hasResetNetPEasterEggCounter())

        testee.resetNetPEasterEggCounter()

        assertTrue(testee.hasResetNetPEasterEggCounter())
    }
}
