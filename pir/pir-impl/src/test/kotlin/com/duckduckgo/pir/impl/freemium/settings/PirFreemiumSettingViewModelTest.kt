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

package com.duckduckgo.pir.impl.freemium.settings

import androidx.lifecycle.LifecycleOwner
import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.pir.impl.freemium.PirFreemium
import com.duckduckgo.pir.impl.freemium.PirFreemiumState.ELIGIBLE
import com.duckduckgo.pir.impl.freemium.PirFreemiumState.NOT_ELIGIBLE
import com.duckduckgo.pir.impl.freemium.PirFreemiumState.USED
import com.duckduckgo.pir.impl.freemium.settings.PirFreemiumSettingViewModel.Command.OpenPirDashboard
import com.duckduckgo.pir.impl.pixels.PirFreemiumCtaState
import com.duckduckgo.pir.impl.pixels.PirPixelSender
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
    private val pixelSender: PirPixelSender = mock()
    private val lifecycleOwner: LifecycleOwner = mock()

    private lateinit var testee: PirFreemiumSettingViewModel

    @Before
    fun before() {
        testee = PirFreemiumSettingViewModel(pirFreemium, pixelSender)
    }

    @Test
    fun whenEligibleThenStateIsEligible() = runTest {
        whenever(pirFreemium.getPirFreemiumState()).thenReturn(ELIGIBLE)

        testee.onResume(lifecycleOwner)

        testee.viewState.test {
            assertEquals(ELIGIBLE, expectMostRecentItem().freemiumState)
        }
    }

    @Test
    fun whenUsedThenStateIsUsed() = runTest {
        whenever(pirFreemium.getPirFreemiumState()).thenReturn(USED)

        testee.onResume(lifecycleOwner)

        testee.viewState.test {
            assertEquals(USED, expectMostRecentItem().freemiumState)
        }
    }

    @Test
    fun whenNotEligibleThenStateIsNotEligibleAndNoImpressionFired() = runTest {
        whenever(pirFreemium.getPirFreemiumState()).thenReturn(NOT_ELIGIBLE)

        testee.onResume(lifecycleOwner)

        testee.viewState.test {
            assertEquals(NOT_ELIGIBLE, expectMostRecentItem().freemiumState)
        }
        verify(pixelSender, never()).reportFreemiumSettingsEntryPointImpression()
    }

    @Test
    fun whenClickedThenClickPixelFiredAndDashboardOpened() = runTest {
        whenever(pirFreemium.getPirFreemiumState()).thenReturn(ELIGIBLE)
        testee.onResume(lifecycleOwner)

        testee.commands().test {
            testee.onEntryPointClicked()

            assertEquals(OpenPirDashboard, awaitItem())
        }
        verify(pixelSender).reportFreemiumSettingsEntryPointClicked(PirFreemiumCtaState.START_FREE_SCAN)
    }

    @Test
    fun whenUsedAndClickedThenClickPixelCarriesViewScanResults() = runTest {
        whenever(pirFreemium.getPirFreemiumState()).thenReturn(USED)
        testee.onResume(lifecycleOwner)

        testee.onEntryPointClicked()

        verify(pixelSender).reportFreemiumSettingsEntryPointClicked(PirFreemiumCtaState.VIEW_SCAN_RESULTS)
    }

    @Test
    fun whenResumedRepeatedlyThenImpressionFiredOnlyOnce() = runTest {
        whenever(pirFreemium.getPirFreemiumState()).thenReturn(ELIGIBLE)

        testee.onResume(lifecycleOwner)
        testee.onResume(lifecycleOwner)
        testee.onResume(lifecycleOwner)

        verify(pixelSender, times(1)).reportFreemiumSettingsEntryPointImpression()
    }

    @Test
    fun whenUserSignsInWhileSettingsOpenThenRowHidesOnNextResume() = runTest {
        whenever(pirFreemium.getPirFreemiumState()).thenReturn(ELIGIBLE)
        testee.onResume(lifecycleOwner)

        whenever(pirFreemium.getPirFreemiumState()).thenReturn(NOT_ELIGIBLE)
        testee.onResume(lifecycleOwner)

        testee.viewState.test {
            assertEquals(NOT_ELIGIBLE, expectMostRecentItem().freemiumState)
        }
    }

    @Test
    fun whenNotEligibleThenClickIsIgnored() = runTest {
        whenever(pirFreemium.getPirFreemiumState()).thenReturn(NOT_ELIGIBLE)
        testee.onResume(lifecycleOwner)

        testee.onEntryPointClicked()

        verifyNoInteractions(pixelSender)
    }

    @Test
    fun whenNotEligibleThenNoCommandEmitted() = runTest {
        whenever(pirFreemium.getPirFreemiumState()).thenReturn(NOT_ELIGIBLE)
        testee.onResume(lifecycleOwner)

        testee.commands().test {
            testee.onEntryPointClicked()

            expectNoEvents()
        }
    }
}
