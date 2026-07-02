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

import android.annotation.SuppressLint
import app.cash.turbine.test
import com.duckduckgo.adblocking.api.duckplayer.DuckPlayer
import com.duckduckgo.adblocking.api.duckplayer.DuckPlayer.UserPreferences
import com.duckduckgo.adblocking.api.duckplayer.PrivatePlayerMode
import com.duckduckgo.adblocking.api.duckplayer.PrivatePlayerMode.AlwaysAsk
import com.duckduckgo.adblocking.api.duckplayer.PrivatePlayerMode.Disabled
import com.duckduckgo.adblocking.api.duckplayer.PrivatePlayerMode.Enabled
import com.duckduckgo.adblocking.impl.AdBlockingPixelNames
import com.duckduckgo.adblocking.impl.AdBlockingSettingsRepository
import com.duckduckgo.adblocking.impl.domain.AdBlockingState
import com.duckduckgo.adblocking.impl.domain.AdBlockingStatusChecker
import com.duckduckgo.adblocking.impl.remoteconfig.AdBlockingExtensionFeature
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeToggleStore
import com.duckduckgo.feature.toggles.api.FeatureToggles
import com.duckduckgo.feature.toggles.api.Toggle
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

@SuppressLint("DenyListedApi") // setRawStoredState
class AdBlockingSettingsViewModelTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val statusChecker: AdBlockingStatusChecker = mock()
    private val feature = FeatureToggles.Builder()
        .store(FakeToggleStore())
        .appVersionProvider { Int.MAX_VALUE }
        .featureName("adBlockingExtension")
        .ioDispatcher(coroutineRule.testDispatcher)
        .build()
        .create(AdBlockingExtensionFeature::class.java)
    private val repository: AdBlockingSettingsRepository = mock()
    private val pixel: Pixel = mock()

    private fun createViewModel(duckPlayerMode: PrivatePlayerMode = AlwaysAsk): AdBlockingSettingsViewModel {
        whenever(duckPlayer.observeUserPreferences()).thenReturn(flowOf(userPreferences(duckPlayerMode)))
        return AdBlockingSettingsViewModel(statusChecker, feature, repository, pixel, duckPlayer)
    }

    private fun setToggles(uxImprovements: Boolean, contingency: Boolean) {
        feature.adBlockingUXImprovements().setRawStoredState(Toggle.State(remoteEnableState = uxImprovements))
        feature.enableContingencyMode().setRawStoredState(Toggle.State(remoteEnableState = contingency))
    }

    private fun userPreferences(privatePlayerMode: PrivatePlayerMode) =
        UserPreferences(overlayInteracted = false, privatePlayerMode = privatePlayerMode)

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
    fun whenDuckPlayerModeIsEnabledThenViewStateReflectsIt() = runTest {
        whenever(statusChecker.observeState()).thenReturn(flowOf(AdBlockingState.Enabled.Default))

        createViewModel(duckPlayerMode = Enabled).viewState.test {
            assertEquals(Enabled, expectMostRecentItem().duckPlayerMode)
        }
    }

    @Test
    fun whenDuckPlayerModeIsDisabledThenViewStateReflectsIt() = runTest {
        whenever(statusChecker.observeState()).thenReturn(flowOf(AdBlockingState.Enabled.Default))

        createViewModel(duckPlayerMode = Disabled).viewState.test {
            assertEquals(Disabled, expectMostRecentItem().duckPlayerMode)
        }
    }

    @Test
    fun whenContingencyModeAndUxImprovementsOnThenIsContingencyMode() = runTest {
        whenever(statusChecker.observeState()).thenReturn(flowOf(AdBlockingState.Enabled.Default))
        setToggles(uxImprovements = true, contingency = true)

        createViewModel().viewState.test {
            assertTrue(expectMostRecentItem().isContingencyMode)
        }
    }

    @Test
    fun whenContingencyModeOnButUxImprovementsOffThenNotContingencyMode() = runTest {
        whenever(statusChecker.observeState()).thenReturn(flowOf(AdBlockingState.Enabled.Default))
        setToggles(uxImprovements = false, contingency = true)

        createViewModel().viewState.test {
            assertFalse(expectMostRecentItem().isContingencyMode)
        }
    }

    @Test
    fun whenContingencyModeOffThenNotContingencyMode() = runTest {
        whenever(statusChecker.observeState()).thenReturn(flowOf(AdBlockingState.Enabled.Default))
        setToggles(uxImprovements = true, contingency = false)

        createViewModel().viewState.test {
            assertFalse(expectMostRecentItem().isContingencyMode)
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
