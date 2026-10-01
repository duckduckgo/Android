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

package com.duckduckgo.subscriptions.impl.settings.views

import androidx.lifecycle.LifecycleOwner
import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.pir.api.freemium.PirFreemium
import com.duckduckgo.pir.api.freemium.PirFreemiumEntryPoint.HIDDEN
import com.duckduckgo.pir.api.freemium.PirFreemiumEntryPoint.START_FREE_SCAN
import com.duckduckgo.pir.api.freemium.PirFreemiumEntryPoint.VIEW_SCAN_RESULTS
import com.duckduckgo.subscriptions.impl.pixels.PirFreemiumCtaState
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixelSender
import com.duckduckgo.subscriptions.impl.settings.views.PirFreemiumSettingViewModel.Command.OpenPirDashboard
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class PirFreemiumSettingViewModelTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val pirFreemium: PirFreemium = mock()
    private val pixelSender: SubscriptionPixelSender = mock()
    private val lifecycleOwner: LifecycleOwner = mock()

    private lateinit var testee: PirFreemiumSettingViewModel

    @Before
    fun before() {
        testee = PirFreemiumSettingViewModel(pirFreemium, pixelSender)
    }

    @Test
    fun whenEligibleAndNoScanCompletedThenStateIsStartFreeScan() = runTest {
        whenever(pirFreemium.getSettingsEntryPoint()).thenReturn(START_FREE_SCAN)

        testee.onResume(lifecycleOwner)

        testee.viewState.test {
            assertEquals(START_FREE_SCAN, expectMostRecentItem().entryPoint)
        }
    }

    @Test
    fun whenScanCompletedThenStateIsViewScanResults() = runTest {
        whenever(pirFreemium.getSettingsEntryPoint()).thenReturn(VIEW_SCAN_RESULTS)

        testee.onResume(lifecycleOwner)

        testee.viewState.test {
            assertEquals(VIEW_SCAN_RESULTS, expectMostRecentItem().entryPoint)
        }
    }

    @Test
    fun whenNotEligibleThenStateIsHiddenAndNoImpressionFired() = runTest {
        whenever(pirFreemium.getSettingsEntryPoint()).thenReturn(HIDDEN)

        testee.onResume(lifecycleOwner)

        testee.viewState.test {
            assertEquals(HIDDEN, expectMostRecentItem().entryPoint)
        }
        verify(pixelSender, never()).reportAppSettingsPirFreemiumImpression()
    }

    @Test
    fun whenClickedThenClickPixelFiredAndDashboardOpened() = runTest {
        whenever(pirFreemium.getSettingsEntryPoint()).thenReturn(START_FREE_SCAN)
        testee.onResume(lifecycleOwner)

        testee.commands().test {
            testee.onEntryPointClicked()

            assertEquals(OpenPirDashboard, awaitItem())
        }
        verify(pixelSender).reportAppSettingsPirFreemiumClick(PirFreemiumCtaState.START_FREE_SCAN)
    }

    @Test
    fun whenResumedRepeatedlyThenImpressionFiredOnlyOnce() = runTest {
        whenever(pirFreemium.getSettingsEntryPoint()).thenReturn(START_FREE_SCAN)

        testee.onResume(lifecycleOwner)
        testee.onResume(lifecycleOwner)
        testee.onResume(lifecycleOwner)

        verify(pixelSender, times(1)).reportAppSettingsPirFreemiumImpression()
    }

    @Test
    fun whenUserSignsInWhileSettingsOpenThenRowHidesOnNextResume() = runTest {
        whenever(pirFreemium.getSettingsEntryPoint()).thenReturn(START_FREE_SCAN)
        testee.onResume(lifecycleOwner)

        whenever(pirFreemium.getSettingsEntryPoint()).thenReturn(HIDDEN)
        testee.onResume(lifecycleOwner)

        testee.viewState.test {
            assertEquals(HIDDEN, expectMostRecentItem().entryPoint)
        }
    }

    @Test
    fun whenStateIsHiddenThenClickIsIgnored() = runTest {
        whenever(pirFreemium.getSettingsEntryPoint()).thenReturn(HIDDEN)
        testee.onResume(lifecycleOwner)

        testee.onEntryPointClicked()

        verifyNoInteractions(pixelSender)
    }

    @Test
    fun whenStateIsHiddenThenNoCommandEmitted() = runTest {
        whenever(pirFreemium.getSettingsEntryPoint()).thenReturn(HIDDEN)
        testee.onResume(lifecycleOwner)

        testee.commands().test {
            testee.onEntryPointClicked()

            expectNoEvents()
        }
    }
}
