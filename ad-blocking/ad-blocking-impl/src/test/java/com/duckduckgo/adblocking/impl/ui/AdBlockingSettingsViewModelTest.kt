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

package com.duckduckgo.adblocking.impl.ui

import app.cash.turbine.test
import com.duckduckgo.adblocking.impl.AdBlockingPixelNames
import com.duckduckgo.adblocking.impl.AdBlockingSettingsRepository
import com.duckduckgo.adblocking.impl.domain.AdBlockingState
import com.duckduckgo.adblocking.impl.domain.AdBlockingStatusChecker
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AdBlockingSettingsViewModelTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val statusChecker: AdBlockingStatusChecker = mock()
    private val repository: AdBlockingSettingsRepository = mock()
    private val pixel: Pixel = mock()

    private fun createViewModel() = AdBlockingSettingsViewModel(statusChecker, repository, pixel)

    @Test
    fun whenEnabledByDefaultThenDoesNotShowConsentDescription() = runTest {
        whenever(statusChecker.observeState()).thenReturn(flowOf(AdBlockingState.Enabled.Default))

        createViewModel().viewState.test {
            val state = expectMostRecentItem()
            assertTrue(state.isEnabled)
            assertEquals(false, state.showConsentDescription)
        }
    }

    @Test
    fun whenUserEnabledThenShowsConsentDescription() = runTest {
        whenever(statusChecker.observeState()).thenReturn(flowOf(AdBlockingState.Enabled.UserEnabled))

        createViewModel().viewState.test {
            val state = expectMostRecentItem()
            assertTrue(state.isEnabled)
            assertEquals(true, state.showConsentDescription)
        }
    }

    @Test
    fun whenDisabledThenShowsConsentDescription() = runTest {
        whenever(statusChecker.observeState()).thenReturn(flowOf(AdBlockingState.Disabled))

        createViewModel().viewState.test {
            val state = expectMostRecentItem()
            assertFalse(state.isEnabled)
            assertEquals(true, state.showConsentDescription)
        }
    }

    @Test
    fun whenViewModelCreatedThenFiresSettingsOpenedPixels() = runTest {
        whenever(statusChecker.observeState()).thenReturn(flowOf(AdBlockingState.Disabled))

        createViewModel()

        verify(pixel).fire(AdBlockingPixelNames.AD_BLOCKING_SETTINGS_OPENED_DAILY, type = Pixel.PixelType.Daily())
        verify(pixel).fire(AdBlockingPixelNames.AD_BLOCKING_SETTINGS_OPENED_COUNT)
    }

    @Test
    fun whenBlockAdsToggledOnThenFiresEnabledPixels() = runTest {
        whenever(statusChecker.observeState()).thenReturn(flowOf(AdBlockingState.Disabled))

        createViewModel().onBlockAdsToggled(enabled = true)

        verify(repository).setEnabled(true)
        verify(pixel).fire(AdBlockingPixelNames.AD_BLOCKING_ENABLED_DAILY, type = Pixel.PixelType.Daily())
        verify(pixel).fire(AdBlockingPixelNames.AD_BLOCKING_ENABLED_COUNT)
    }

    @Test
    fun whenBlockAdsToggledOffThenFiresDisabledPixels() = runTest {
        whenever(statusChecker.observeState()).thenReturn(flowOf(AdBlockingState.Enabled.UserEnabled))

        createViewModel().onBlockAdsToggled(enabled = false)

        verify(repository).setEnabled(false)
        verify(pixel).fire(AdBlockingPixelNames.AD_BLOCKING_DISABLED_DAILY, type = Pixel.PixelType.Daily())
        verify(pixel).fire(AdBlockingPixelNames.AD_BLOCKING_DISABLED_COUNT)
    }
}
