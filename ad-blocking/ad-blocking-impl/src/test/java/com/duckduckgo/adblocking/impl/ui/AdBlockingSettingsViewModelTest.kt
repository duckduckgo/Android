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
import com.duckduckgo.adblocking.impl.duckplayer.DuckPlayerSettingsPlacement
import com.duckduckgo.adblocking.impl.duckplayer.DuckPlayerSettingsPlacementProvider
import com.duckduckgo.adblocking.impl.remoteconfig.AdBlockingExtensionFeature
import com.duckduckgo.adblocking.impl.segmentskip.SegmentSkip
import com.duckduckgo.adblocking.impl.segmentskip.SegmentSkipConfig
import com.duckduckgo.adblocking.impl.segmentskip.SegmentSkipFeature
import com.duckduckgo.adblocking.impl.segmentskip.SegmentSkipMode
import com.duckduckgo.adblocking.impl.segmentskip.SegmentSkipSegment
import com.duckduckgo.adblocking.impl.store.RealAdBlockingSessionStore
import com.duckduckgo.adblocking.impl.ui.AdBlockingSettingsViewModel.DuckPlayerSection
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeToggleStore
import com.duckduckgo.feature.toggles.api.FeatureToggles
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
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
    private val sessionStore = RealAdBlockingSessionStore()
    private val pixel: Pixel = mock()
    private val duckPlayer: DuckPlayer = mock()
    private val duckPlayerSettingsPlacementProvider: DuckPlayerSettingsPlacementProvider = mock()
    private val segmentSkipFeature = FeatureToggles.Builder()
        .store(FakeToggleStore())
        .appVersionProvider { Int.MAX_VALUE }
        .featureName("segmentSkip")
        .ioDispatcher(coroutineRule.testDispatcher)
        .build()
        .create(SegmentSkipFeature::class.java)
    private val segmentSkip = FakeSegmentSkip()

    private fun createViewModel(
        duckPlayerMode: PrivatePlayerMode = AlwaysAsk,
        duckPlayerSettingsPlacement: DuckPlayerSettingsPlacement = DuckPlayerSettingsPlacement.InAdBlocking,
    ): AdBlockingSettingsViewModel {
        whenever(duckPlayer.observeUserPreferences()).thenReturn(flowOf(userPreferences(duckPlayerMode)))
        whenever(duckPlayerSettingsPlacementProvider.placementFlow()).thenReturn(flowOf(duckPlayerSettingsPlacement))
        return AdBlockingSettingsViewModel(
            statusChecker,
            feature,
            repository,
            sessionStore,
            pixel,
            duckPlayer,
            duckPlayerSettingsPlacementProvider,
            segmentSkipFeature,
            segmentSkip,
        )
    }

    private fun setSegmentSkipEnabled(enabled: Boolean) {
        segmentSkipFeature.self().setRawStoredState(Toggle.State(remoteEnableState = true))
        segmentSkipFeature.mvpEnabled().setRawStoredState(Toggle.State(remoteEnableState = enabled))
    }

    private fun setToggles(contingency: Boolean) {
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
    fun whenEnabledWithoutPixelConsentThenDoesNotShowConsentDescription() = runTest {
        whenever(statusChecker.observeState()).thenReturn(flowOf(AdBlockingState.Enabled.WithoutPixelConsent))

        createViewModel().viewState.test {
            val state = expectMostRecentItem()
            assertTrue(state.isEnabled)
            assertEquals(false, state.showConsentDescription)
        }
    }

    @Test
    fun whenEnabledWithPixelConsentThenShowsConsentDescription() = runTest {
        whenever(statusChecker.observeState()).thenReturn(flowOf(AdBlockingState.Enabled.WithPixelConsent))

        createViewModel().viewState.test {
            val state = expectMostRecentItem()
            assertTrue(state.isEnabled)
            assertEquals(true, state.showConsentDescription)
        }
    }

    @Test
    fun whenDisabledThenShowsConsentDescription() = runTest {
        whenever(statusChecker.observeState()).thenReturn(flowOf(AdBlockingState.Disabled.Permanent))

        createViewModel().viewState.test {
            val state = expectMostRecentItem()
            assertFalse(state.isEnabled)
            assertEquals(true, state.showConsentDescription)
        }
    }

    @Test
    fun whenDisabledUntilRelaunchThenNotEnabledAndFlagSet() = runTest {
        whenever(statusChecker.observeState()).thenReturn(flowOf(AdBlockingState.Disabled.UntilRelaunch))

        createViewModel().viewState.test {
            val state = expectMostRecentItem()
            assertFalse(state.isEnabled)
            assertTrue(state.disabledUntilRelaunch)
        }
    }

    @Test
    fun whenEnabledThenDisabledUntilRelaunchFlagNotSet() = runTest {
        whenever(statusChecker.observeState()).thenReturn(flowOf(AdBlockingState.Enabled.WithPixelConsent))

        createViewModel().viewState.test {
            assertFalse(expectMostRecentItem().disabledUntilRelaunch)
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
    fun whenDuckPlayerSettingsInAdBlockingThenShowsEntry() = runTest {
        whenever(statusChecker.observeState()).thenReturn(flowOf(AdBlockingState.Enabled.Default))

        createViewModel(duckPlayerSettingsPlacement = DuckPlayerSettingsPlacement.InAdBlocking).viewState.test {
            assertEquals(DuckPlayerSection.Entry, expectMostRecentItem().duckPlayerSection)
        }
    }

    @Test
    fun whenDuckPlayerSettingsStandaloneThenShowsLink() = runTest {
        whenever(statusChecker.observeState()).thenReturn(flowOf(AdBlockingState.Enabled.Default))

        createViewModel(duckPlayerSettingsPlacement = DuckPlayerSettingsPlacement.Standalone).viewState.test {
            assertEquals(DuckPlayerSection.Link, expectMostRecentItem().duckPlayerSection)
        }
    }

    @Test
    fun whenDuckPlayerSettingsHiddenThenShowsNoDuckPlayerSection() = runTest {
        whenever(statusChecker.observeState()).thenReturn(flowOf(AdBlockingState.Enabled.Default))

        createViewModel(duckPlayerSettingsPlacement = DuckPlayerSettingsPlacement.Hidden).viewState.test {
            assertEquals(DuckPlayerSection.None, expectMostRecentItem().duckPlayerSection)
        }
    }

    @Test
    fun whenInterferenceLearnMoreClickedThenOpensInterferenceHelpPage() = runTest {
        whenever(statusChecker.observeState()).thenReturn(flowOf(AdBlockingState.Enabled.Default))
        val viewModel = createViewModel()

        viewModel.commands.test {
            viewModel.onInterferenceLearnMoreClicked()
            assertEquals(
                AdBlockingSettingsViewModel.Command.OpenLearnMore(
                    "https://duckduckgo.com/duckduckgo-help-pages/privacy/detecting-ad-blocking-interference-anonymously",
                ),
                awaitItem(),
            )
        }
    }

    @Test
    fun whenDuckPlayerClickedThenOpensDuckPlayerSettings() = runTest {
        whenever(statusChecker.observeState()).thenReturn(flowOf(AdBlockingState.Enabled.Default))
        val viewModel = createViewModel(duckPlayerSettingsPlacement = DuckPlayerSettingsPlacement.Standalone)

        viewModel.commands.test {
            viewModel.onDuckPlayerClicked()
            assertEquals(AdBlockingSettingsViewModel.Command.OpenDuckPlayerSettings, awaitItem())
        }
    }

    @Test
    fun whenContingencyModeOnThenIsContingencyMode() = runTest {
        whenever(statusChecker.observeState()).thenReturn(flowOf(AdBlockingState.Enabled.Default))
        setToggles(contingency = true)

        createViewModel().viewState.test {
            assertTrue(expectMostRecentItem().isContingencyMode)
        }
    }

    @Test
    fun whenContingencyModeOffThenNotContingencyMode() = runTest {
        whenever(statusChecker.observeState()).thenReturn(flowOf(AdBlockingState.Enabled.Default))
        setToggles(contingency = false)

        createViewModel().viewState.test {
            assertFalse(expectMostRecentItem().isContingencyMode)
        }
    }

    @Test
    fun whenViewModelCreatedThenFiresSettingsOpenedPixels() = runTest {
        whenever(statusChecker.observeState()).thenReturn(flowOf(AdBlockingState.Disabled.Permanent))

        createViewModel()

        verify(pixel).fire(AdBlockingPixelNames.AD_BLOCKING_SETTINGS_OPENED_DAILY, type = Pixel.PixelType.Daily())
        verify(pixel).fire(AdBlockingPixelNames.AD_BLOCKING_SETTINGS_OPENED_COUNT)
    }

    @Test
    fun whenBlockAdsToggledOnThenFiresEnabledPixels() = runTest {
        whenever(statusChecker.observeState()).thenReturn(flowOf(AdBlockingState.Disabled.Permanent))

        createViewModel().onBlockAdsToggled(enabled = true)

        verify(repository).enable(withPixelConsent = true)
        verify(pixel).fire(AdBlockingPixelNames.AD_BLOCKING_ENABLED_DAILY, type = Pixel.PixelType.Daily())
        verify(pixel).fire(AdBlockingPixelNames.AD_BLOCKING_ENABLED_COUNT)
    }

    @Test
    fun whenBlockAdsToggledThenClearsSessionOverride() = runTest {
        whenever(statusChecker.observeState()).thenReturn(flowOf(AdBlockingState.Disabled.UntilRelaunch))
        sessionStore.setDisabledUntilRelaunch()

        createViewModel().onBlockAdsToggled(enabled = true)

        assertFalse(sessionStore.isDisabledUntilRelaunch())
    }

    @Test
    fun whenBlockAdsToggledOffThenFiresDisabledPixels() = runTest {
        whenever(statusChecker.observeState()).thenReturn(flowOf(AdBlockingState.Enabled.WithPixelConsent))

        createViewModel().onBlockAdsToggled(enabled = false)

        verify(repository).disable()
        verify(pixel).fire(AdBlockingPixelNames.AD_BLOCKING_DISABLED_DAILY, type = Pixel.PixelType.Daily())
        verify(pixel).fire(AdBlockingPixelNames.AD_BLOCKING_DISABLED_COUNT)
    }

    @Test
    fun whenSegmentSkipDisabledThenHidesSegmentSkipMode() = runTest {
        whenever(statusChecker.observeState()).thenReturn(flowOf(AdBlockingState.Enabled.Default))
        setSegmentSkipEnabled(false)

        createViewModel().viewState.test {
            val state = expectMostRecentItem()
            assertNull(state.segmentSkipMode)
            assertFalse(state.showSegmentSkipConsent)
        }
    }

    @Test
    fun whenSegmentSkipEnabledThenShowsStoredSegmentSkipMode() = runTest {
        whenever(statusChecker.observeState()).thenReturn(flowOf(AdBlockingState.Enabled.Default))
        setSegmentSkipEnabled(true)
        segmentSkip.mode.value = SegmentSkipMode.ALWAYS_SKIP

        createViewModel().viewState.test {
            assertEquals(SegmentSkipMode.ALWAYS_SKIP, expectMostRecentItem().segmentSkipMode)
        }
    }

    @Test
    fun whenMvpEnabledThenNoticeIsHiddenAcrossModeChanges() = runTest {
        whenever(statusChecker.observeState()).thenReturn(flowOf(AdBlockingState.Enabled.Default))
        setSegmentSkipEnabled(true)
        segmentSkip.mode.value = SegmentSkipMode.OFF
        createViewModel().viewState.test {
            assertEquals(false, expectMostRecentItem().showSegmentSkipConsent)
            segmentSkip.mode.value = SegmentSkipMode.ALWAYS_ASK
            assertEquals(false, awaitItem().showSegmentSkipConsent)
            segmentSkip.mode.value = SegmentSkipMode.OFF
            assertEquals(false, awaitItem().showSegmentSkipConsent)
        }
    }

    @Test
    fun whenMvpEnabledWithExistingConsentThenNoticeIsShown() = runTest {
        whenever(statusChecker.observeState()).thenReturn(flowOf(AdBlockingState.Enabled.Default))
        setSegmentSkipEnabled(true)
        segmentSkip.mode.value = SegmentSkipMode.ALWAYS_ASK
        segmentSkip.consent.value = true
        createViewModel().viewState.test {
            assertEquals(true, expectMostRecentItem().showSegmentSkipConsent)
            segmentSkip.consent.value = false
            assertEquals(false, awaitItem().showSegmentSkipConsent)
        }
    }

    @Test
    fun whenOnlySoftLaunchEnabledThenShowsSettingAndConsentNoticeInNeverMode() = runTest {
        whenever(statusChecker.observeState()).thenReturn(flowOf(AdBlockingState.Enabled.Default))
        setSegmentSkipEnabled(false)
        segmentSkipFeature.softLaunchEnabled().setRawStoredState(Toggle.State(remoteEnableState = true))
        segmentSkip.mode.value = SegmentSkipMode.OFF
        createViewModel().viewState.test {
            val state = expectMostRecentItem()
            assertEquals(SegmentSkipMode.OFF, state.segmentSkipMode)
            assertTrue(state.showSegmentSkipConsent)
            segmentSkip.mode.value = SegmentSkipMode.ALWAYS_ASK
            assertEquals(false, awaitItem().showSegmentSkipConsent)
            segmentSkip.consent.value = true
            assertEquals(true, awaitItem().showSegmentSkipConsent)
        }
    }

    @Test
    fun whenNoticeWasShownThenEnablingFromOffGrantsConsent() = runTest {
        segmentSkip.mode.value = SegmentSkipMode.OFF
        createViewModel().onSegmentSkipModeDialogSaved(SegmentSkipMode.ALWAYS_SKIP, userConsent = true)
        assertEquals(true, segmentSkip.consent.value)
    }

    @Test
    fun whenSegmentSkipModeClickedThenShowsDialogWithCurrentMode() = runTest {
        whenever(statusChecker.observeState()).thenReturn(flowOf(AdBlockingState.Enabled.Default))
        segmentSkip.mode.value = SegmentSkipMode.OFF
        val viewModel = createViewModel()

        viewModel.commands.test {
            viewModel.onSegmentSkipModeClicked()
            assertEquals(AdBlockingSettingsViewModel.Command.ShowSegmentSkipModeDialog(SegmentSkipMode.OFF), awaitItem())
        }
        verify(pixel).fire(AdBlockingPixelNames.SEGMENT_SKIP_SETTINGS_CLICKED_DAILY, type = Pixel.PixelType.Daily())
        verify(pixel).fire(AdBlockingPixelNames.SEGMENT_SKIP_SETTINGS_CLICKED_COUNT)
    }

    @Test
    fun whenDialogIsOpenedWithoutSelectionThenModeAndConsentStayUnchanged() = runTest {
        segmentSkip.mode.value = SegmentSkipMode.OFF
        val viewModel = createViewModel()
        viewModel.commands.test {
            viewModel.onSegmentSkipModeClicked()
            awaitItem()
        }
        assertEquals(SegmentSkipMode.OFF, segmentSkip.mode.value)
        assertEquals(false, segmentSkip.consent.value)
    }

    @Test
    fun whenEnablingFromOffThenModeIsSavedWithoutConsent() = runTest {
        segmentSkip.mode.value = SegmentSkipMode.OFF
        createViewModel().onSegmentSkipModeDialogSaved(SegmentSkipMode.ALWAYS_SKIP, userConsent = false)
        assertEquals(SegmentSkipMode.ALWAYS_SKIP, segmentSkip.mode.value)
        assertEquals(false, segmentSkip.consent.value)
    }

    @Test
    fun whenEnabledModeChangesThenConsentIsUnchanged() = runTest {
        createViewModel().onSegmentSkipModeDialogSaved(SegmentSkipMode.ALWAYS_SKIP, userConsent = false)
        assertEquals(SegmentSkipMode.ALWAYS_SKIP, segmentSkip.mode.value)
        assertEquals(false, segmentSkip.consent.value)
    }

    @Test
    fun whenSegmentSkipModeSelectedThenStoresIt() = runTest {
        whenever(statusChecker.observeState()).thenReturn(flowOf(AdBlockingState.Enabled.Default))

        createViewModel().onSegmentSkipModeSelected(SegmentSkipMode.ALWAYS_SKIP)

        assertEquals(SegmentSkipMode.ALWAYS_SKIP, segmentSkip.mode.value)
        val parameters = mapOf("segment_skip_status" to "skip")
        verify(pixel).fire(AdBlockingPixelNames.SEGMENT_SKIP_SETTINGS_UPDATED_DAILY, parameters = parameters, type = Pixel.PixelType.Daily())
        verify(pixel).fire(AdBlockingPixelNames.SEGMENT_SKIP_SETTINGS_UPDATED_COUNT, parameters = parameters)
    }

    @Test
    fun whenModeIsReselectedThenNoUpdatedPixelFires() = runTest {
        createViewModel().onSegmentSkipModeSelected(SegmentSkipMode.ALWAYS_ASK)
        verify(pixel, never()).fire(AdBlockingPixelNames.SEGMENT_SKIP_SETTINGS_UPDATED_COUNT, parameters = mapOf("segment_skip_status" to "ask"))
    }

    @Test
    fun whenRowIsClickedTwiceThenBothClicksAreCounted() = runTest {
        val viewModel = createViewModel()
        viewModel.onSegmentSkipModeClicked()
        viewModel.onSegmentSkipModeClicked()
        verify(pixel, times(2)).fire(AdBlockingPixelNames.SEGMENT_SKIP_SETTINGS_CLICKED_COUNT)
        verify(pixel, never()).fire(AdBlockingPixelNames.SEGMENT_SKIP_SETTINGS_UPDATED_COUNT, parameters = mapOf("segment_skip_status" to "ask"))
    }

    @Test
    fun whenSaveFailsThenNoUpdatedPixelFires() = runTest {
        segmentSkip.failSave = true
        createViewModel().onSegmentSkipModeSelected(SegmentSkipMode.ALWAYS_SKIP)
        assertEquals(SegmentSkipMode.ALWAYS_ASK, segmentSkip.mode.value)
        verify(pixel, never()).fire(
            AdBlockingPixelNames.SEGMENT_SKIP_SETTINGS_UPDATED_COUNT,
            parameters = mapOf("segment_skip_status" to "skip"),
        )
        verify(pixel, never()).fire(
            AdBlockingPixelNames.SEGMENT_SKIP_SETTINGS_UPDATED_DAILY,
            parameters = mapOf("segment_skip_status" to "skip"),
            type = Pixel.PixelType.Daily(),
        )
    }

    private class FakeSegmentSkip : SegmentSkip {
        val mode = MutableStateFlow(SegmentSkipMode.ALWAYS_ASK)
        val consent = MutableStateFlow(false)
        var failSave = false

        override fun config(): SegmentSkipConfig = throw UnsupportedOperationException()
        override suspend fun getSegments(videoId: String): List<SegmentSkipSegment> = throw UnsupportedOperationException()
        override fun modeFlow(): Flow<SegmentSkipMode> = mode
        override fun consentFlow(): Flow<Boolean> = consent
        override suspend fun setMode(mode: SegmentSkipMode, userConsent: Boolean): Boolean {
            if (failSave) throw java.io.IOException()
            if (this.mode.value == SegmentSkipMode.OFF && mode != SegmentSkipMode.OFF) consent.value = userConsent
            val changed = this.mode.value != mode
            this.mode.value = mode
            return changed
        }
    }
}
