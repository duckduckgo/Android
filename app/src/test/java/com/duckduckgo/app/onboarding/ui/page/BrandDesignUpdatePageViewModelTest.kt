/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.app.onboarding.ui.page

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import app.cash.turbine.test
import com.duckduckgo.app.browser.omnibar.OmnibarType
import com.duckduckgo.app.global.DefaultRoleBrowserDialog
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.onboarding.ui.page.BrandDesignUpdatePageViewModel.Command
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.pixels.AppPixelName.NOTIFICATION_RUNTIME_PERMISSION_SHOWN
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_ADDRESS_BAR_POSITION_SHOWN_UNIQUE
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_AICHAT_SELECTED
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_BOTTOM_ADDRESS_BAR_SELECTED_UNIQUE
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_CHOOSE_BROWSER_PRESSED
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_CHOOSE_SEARCH_EXPERIENCE_IMPRESSIONS_UNIQUE
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_COMPARISON_CHART_SHOWN_UNIQUE
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_CONFIRM_SKIP_ONBOARDING_PRESSED
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_INTRO_REINSTALL_USER_SHOWN_UNIQUE
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_INTRO_SHOWN_UNIQUE
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_RESUME_ONBOARDING_PRESSED
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_SEARCH_ONLY_SELECTED
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_SKIP_ONBOARDING_PRESSED
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_SKIP_ONBOARDING_SHOWN_UNIQUE
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_SPLIT_ADDRESS_BAR_SELECTED_UNIQUE
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Unique
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.impl.inputscreen.wideevents.InputScreenOnboardingWideEvent
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SuppressLint("DenyListedApi")
class BrandDesignUpdatePageViewModelTest {
    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

    private val mockDefaultRoleBrowserDialog: DefaultRoleBrowserDialog = mock()
    private val mockContext: Context = mock()
    private val mockPixel: Pixel = mock()
    private val mockAppInstallStore: AppInstallStore = mock()
    private val mockSettingsDataStore: SettingsDataStore = mock()
    private val mockAppBuildConfig: AppBuildConfig = mock()
    private val mockOnboardingStore: OnboardingStore = mock()
    private val mockAndroidBrowserConfigFeature: AndroidBrowserConfigFeature = FakeFeatureToggleFactory.create(
        AndroidBrowserConfigFeature::class.java,
    )
    private val mockDuckChat: DuckChat = mock()
    private val mockInputScreenOnboardingWideEvent: InputScreenOnboardingWideEvent = mock()

    private fun createViewModel(): BrandDesignUpdatePageViewModel {
        return BrandDesignUpdatePageViewModel(
            mockDefaultRoleBrowserDialog,
            mockContext,
            mockPixel,
            mockAppInstallStore,
            mockSettingsDataStore,
            coroutineRule.testDispatcherProvider,
            mockAppBuildConfig,
            mockOnboardingStore,
            mockAndroidBrowserConfigFeature,
            mockDuckChat,
            mockInputScreenOnboardingWideEvent,
        )
    }

    // region Initial state

    @Test
    fun whenViewModelCreatedThenInitialViewStateHasNullDialog() = runTest {
        val testee = createViewModel()
        testee.viewState.test {
            val state = awaitItem()
            assertNull(state.currentDialog)
            assertEquals(OmnibarType.SINGLE_TOP, state.selectedAddressBarPosition)
            assertTrue(state.inputScreenSelected)
            assertEquals(false, state.showSplitOption)
            assertEquals(false, state.isReinstallUser)
            cancelAndConsumeRemainingEvents()
        }
    }

    // endregion

    // region loadDaxDialog

    @Test
    fun whenLoadDaxDialogAndNotReinstallThenViewStateShowsInitialDialog() = runTest {
        whenever(mockAppBuildConfig.isAppReinstall()).thenReturn(false)
        val testee = createViewModel()
        testee.viewState.test {
            awaitItem() // initial
            testee.loadDaxDialog()
            val state = awaitItem()
            assertEquals(PreOnboardingDialogType.INITIAL, state.currentDialog)
            assertEquals(false, state.isReinstallUser)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenLoadDaxDialogAndReinstallThenViewStateShowsReinstallDialog() = runTest {
        whenever(mockAppBuildConfig.isAppReinstall()).thenReturn(true)
        val testee = createViewModel()
        testee.viewState.test {
            awaitItem() // initial
            testee.loadDaxDialog()
            val state = awaitItem()
            assertEquals(PreOnboardingDialogType.INITIAL_REINSTALL_USER, state.currentDialog)
            assertTrue(state.isReinstallUser)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenLoadDaxDialogNotReinstallThenFiresInitialShownPixel() = runTest {
        whenever(mockAppBuildConfig.isAppReinstall()).thenReturn(false)
        val testee = createViewModel()
        testee.loadDaxDialog()
        verify(mockPixel).fire(PREONBOARDING_INTRO_SHOWN_UNIQUE, type = Unique())
    }

    @Test
    fun whenLoadDaxDialogReinstallThenFiresReinstallShownPixel() = runTest {
        whenever(mockAppBuildConfig.isAppReinstall()).thenReturn(true)
        val testee = createViewModel()
        testee.loadDaxDialog()
        verify(mockPixel).fire(PREONBOARDING_INTRO_REINSTALL_USER_SHOWN_UNIQUE, type = Unique())
    }

    // endregion

    // region onPrimaryCtaClicked - initial/reinstall -> comparison chart

    @Test
    fun whenPrimaryCtaFromInitialThenShowComparisonChart() = runTest {
        whenever(mockAppBuildConfig.isAppReinstall()).thenReturn(false)
        val testee = createViewModel()
        testee.viewState.test {
            awaitItem()
            testee.loadDaxDialog()
            awaitItem() // INITIAL
            testee.onPrimaryCtaClicked()
            val state = awaitItem()
            assertEquals(PreOnboardingDialogType.COMPARISON_CHART, state.currentDialog)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenPrimaryCtaFromInitialThenFiresComparisonChartShownPixel() = runTest {
        whenever(mockAppBuildConfig.isAppReinstall()).thenReturn(false)
        val testee = createViewModel()
        testee.loadDaxDialog()
        testee.onPrimaryCtaClicked()
        verify(mockPixel).fire(PREONBOARDING_COMPARISON_CHART_SHOWN_UNIQUE, type = Unique())
    }

    @Test
    fun whenPrimaryCtaFromReinstallUserThenShowComparisonChart() = runTest {
        whenever(mockAppBuildConfig.isAppReinstall()).thenReturn(true)
        val testee = createViewModel()
        testee.viewState.test {
            awaitItem()
            testee.loadDaxDialog()
            awaitItem() // INITIAL_REINSTALL_USER
            testee.onPrimaryCtaClicked()
            val state = awaitItem()
            assertEquals(PreOnboardingDialogType.COMPARISON_CHART, state.currentDialog)
            cancelAndConsumeRemainingEvents()
        }
    }

    // endregion

    // region onPrimaryCtaClicked - comparison chart

    @Test
    fun whenPrimaryCtaFromComparisonChartAndDefaultBrowserDialogAvailableThenSendShowDefaultBrowserCommand() = runTest {
        val mockIntent: Intent = mock()
        whenever(mockDefaultRoleBrowserDialog.shouldShowDialog()).thenReturn(true)
        whenever(mockDefaultRoleBrowserDialog.createIntent(mockContext)).thenReturn(mockIntent)
        whenever(mockAppBuildConfig.isAppReinstall()).thenReturn(false)
        val testee = createViewModel()
        testee.loadDaxDialog()
        testee.onPrimaryCtaClicked() // INITIAL -> COMPARISON_CHART
        testee.commands.test {
            testee.onPrimaryCtaClicked() // COMPARISON_CHART -> ShowDefaultBrowserDialog
            val command = awaitItem()
            assertTrue(command is Command.ShowDefaultBrowserDialog)
        }
    }

    @Test
    fun whenPrimaryCtaFromComparisonChartAndAlreadyDefaultBrowserThenSendFinishCommand() = runTest {
        whenever(mockDefaultRoleBrowserDialog.shouldShowDialog()).thenReturn(false)
        whenever(mockAppBuildConfig.isAppReinstall()).thenReturn(false)
        val testee = createViewModel()
        testee.loadDaxDialog()
        testee.onPrimaryCtaClicked() // INITIAL -> COMPARISON_CHART
        testee.commands.test {
            testee.onPrimaryCtaClicked() // COMPARISON_CHART -> Finish
            val command = awaitItem()
            assertTrue(command is Command.Finish)
        }
    }

    @Test
    fun whenPrimaryCtaFromComparisonChartThenFiresChooseBrowserPressedPixel() = runTest {
        whenever(mockDefaultRoleBrowserDialog.shouldShowDialog()).thenReturn(false)
        whenever(mockAppBuildConfig.isAppReinstall()).thenReturn(false)
        val testee = createViewModel()
        testee.loadDaxDialog()
        testee.onPrimaryCtaClicked() // -> COMPARISON_CHART
        testee.onPrimaryCtaClicked() // triggers pixel
        verify(mockPixel).fire(
            PREONBOARDING_CHOOSE_BROWSER_PRESSED,
            mapOf(PixelParameter.DEFAULT_BROWSER to "true"),
        )
    }

    // endregion

    // region onPrimaryCtaClicked - skip onboarding

    @Test
    fun whenPrimaryCtaFromSkipOnboardingThenSendOnboardingSkippedCommand() = runTest {
        whenever(mockAppBuildConfig.isAppReinstall()).thenReturn(true)
        val testee = createViewModel()
        testee.loadDaxDialog()
        testee.onSecondaryCtaClicked() // INITIAL_REINSTALL_USER -> SKIP_ONBOARDING_OPTION
        testee.commands.test {
            testee.onPrimaryCtaClicked()
            val command = awaitItem()
            assertTrue(command is Command.OnboardingSkipped)
        }
        verify(mockPixel).fire(PREONBOARDING_CONFIRM_SKIP_ONBOARDING_PRESSED)
    }

    // endregion

    // region onPrimaryCtaClicked - address bar position

    @Test
    fun whenPrimaryCtaFromAddressBarPositionWithTopThenSaveTopAndSendFinish() = runTest {
        mockAndroidBrowserConfigFeature.showInputScreenOnboarding().setRawStoredState(Toggle.State(enable = false))
        val testee = createViewModel()
        // Navigate to ADDRESS_BAR_POSITION via onDefaultBrowserSet
        testee.onDefaultBrowserSet()
        testee.onAddressBarPositionOptionSelected(OmnibarType.SINGLE_TOP)
        testee.commands.test {
            testee.onPrimaryCtaClicked()
            val command = awaitItem()
            assertTrue(command is Command.Finish)
        }
        verify(mockSettingsDataStore).omnibarType = OmnibarType.SINGLE_TOP
    }

    @Test
    fun whenPrimaryCtaFromAddressBarPositionWithBottomThenSaveBottomAndFirePixelAndFinish() = runTest {
        mockAndroidBrowserConfigFeature.showInputScreenOnboarding().setRawStoredState(Toggle.State(enable = false))
        val testee = createViewModel()
        testee.onDefaultBrowserSet()
        testee.onAddressBarPositionOptionSelected(OmnibarType.SINGLE_BOTTOM)
        testee.commands.test {
            testee.onPrimaryCtaClicked()
            val command = awaitItem()
            assertTrue(command is Command.Finish)
        }
        verify(mockSettingsDataStore).omnibarType = OmnibarType.SINGLE_BOTTOM
        verify(mockPixel).fire(PREONBOARDING_BOTTOM_ADDRESS_BAR_SELECTED_UNIQUE)
    }

    @Test
    fun whenPrimaryCtaFromAddressBarPositionWithSplitAndFeatureEnabledThenSaveSplitAndFirePixel() = runTest {
        mockAndroidBrowserConfigFeature.splitOmnibar().setRawStoredState(Toggle.State(remoteEnableState = true))
        mockAndroidBrowserConfigFeature.splitOmnibarWelcomePage().setRawStoredState(Toggle.State(remoteEnableState = true))
        val testee = createViewModel()
        testee.onDefaultBrowserSet()
        testee.onAddressBarPositionOptionSelected(OmnibarType.SPLIT)
        testee.onPrimaryCtaClicked()
        verify(mockSettingsDataStore).omnibarType = OmnibarType.SPLIT
        verify(mockPixel).fire(PREONBOARDING_SPLIT_ADDRESS_BAR_SELECTED_UNIQUE)
    }

    @Test
    fun whenPrimaryCtaFromAddressBarPositionWithSplitAndFeatureDisabledThenFallbackToTop() = runTest {
        mockAndroidBrowserConfigFeature.splitOmnibar().setRawStoredState(Toggle.State(remoteEnableState = false))
        val testee = createViewModel()
        testee.onDefaultBrowserSet()
        testee.onAddressBarPositionOptionSelected(OmnibarType.SPLIT)
        testee.onPrimaryCtaClicked()
        verify(mockSettingsDataStore).omnibarType = OmnibarType.SINGLE_TOP
        verify(mockSettingsDataStore, never()).omnibarType = OmnibarType.SPLIT
    }

    @Test
    fun whenPrimaryCtaFromAddressBarPositionThenFiresAddressBarPositionShownPixel() = runTest {
        val testee = createViewModel()
        testee.onDefaultBrowserSet()
        verify(mockPixel).fire(PREONBOARDING_ADDRESS_BAR_POSITION_SHOWN_UNIQUE, type = Unique())
    }

    // endregion

    // region onPrimaryCtaClicked - input screen

    @Test
    fun whenPrimaryCtaFromInputScreenWithAiSelectedThenFireAiPixelAndStoreAndFinish() = runTest {
        mockAndroidBrowserConfigFeature.showInputScreenOnboarding().setRawStoredState(Toggle.State(enable = true))
        val testee = createViewModel()
        testee.onDefaultBrowserSet()
        testee.onAddressBarPositionOptionSelected(OmnibarType.SINGLE_TOP)
        testee.onInputScreenOptionSelected(true)
        testee.commands.test {
            testee.onPrimaryCtaClicked() // ADDRESS_BAR_POSITION -> INPUT_SCREEN (no command)
            testee.onPrimaryCtaClicked() // INPUT_SCREEN -> Finish
            val command = awaitItem()
            assertTrue(command is Command.Finish)
        }
        verify(mockPixel).fire(PREONBOARDING_AICHAT_SELECTED)
        verify(mockOnboardingStore).storeInputScreenSelection(true)
        verify(mockDuckChat).setCosmeticInputScreenUserSetting(true)
        verify(mockInputScreenOnboardingWideEvent).onInputScreenEnabledDuringOnboarding(reinstallUser = false)
    }

    @Test
    fun whenPrimaryCtaFromInputScreenWithSearchOnlySelectedThenFireSearchOnlyPixelAndStoreAndFinish() = runTest {
        mockAndroidBrowserConfigFeature.showInputScreenOnboarding().setRawStoredState(Toggle.State(enable = true))
        val testee = createViewModel()
        testee.onDefaultBrowserSet()
        testee.onAddressBarPositionOptionSelected(OmnibarType.SINGLE_TOP)
        testee.onInputScreenOptionSelected(false)
        testee.commands.test {
            testee.onPrimaryCtaClicked() // ADDRESS_BAR_POSITION -> INPUT_SCREEN
            testee.onPrimaryCtaClicked() // INPUT_SCREEN -> Finish
            val command = awaitItem()
            assertTrue(command is Command.Finish)
        }
        verify(mockPixel).fire(PREONBOARDING_SEARCH_ONLY_SELECTED)
        verify(mockOnboardingStore).storeInputScreenSelection(false)
        verify(mockDuckChat).setCosmeticInputScreenUserSetting(false)
        verify(mockInputScreenOnboardingWideEvent, never()).onInputScreenEnabledDuringOnboarding(reinstallUser = false)
        verify(mockInputScreenOnboardingWideEvent, never()).onInputScreenEnabledDuringOnboarding(reinstallUser = true)
    }

    @Test
    fun whenPrimaryCtaFromInputScreenWithReinstallUserTrueAndAiSelectedThenCallWideEventWithReinstallTrue() = runTest {
        mockAndroidBrowserConfigFeature.showInputScreenOnboarding().setRawStoredState(Toggle.State(enable = true))
        whenever(mockAppBuildConfig.isAppReinstall()).thenReturn(true)
        val testee = createViewModel()
        testee.loadDaxDialog() // sets isReinstallUser = true
        testee.onDefaultBrowserSet()
        testee.onAddressBarPositionOptionSelected(OmnibarType.SINGLE_TOP)
        testee.onInputScreenOptionSelected(true)
        testee.onPrimaryCtaClicked() // ADDRESS_BAR_POSITION -> INPUT_SCREEN
        testee.onPrimaryCtaClicked() // INPUT_SCREEN -> Finish
        verify(mockInputScreenOnboardingWideEvent).onInputScreenEnabledDuringOnboarding(reinstallUser = true)
    }

    // endregion

    // region onSecondaryCtaClicked

    @Test
    fun whenSecondaryCtaFromReinstallUserThenShowSkipOnboardingAndFirePixel() = runTest {
        whenever(mockAppBuildConfig.isAppReinstall()).thenReturn(true)
        val testee = createViewModel()
        testee.viewState.test {
            awaitItem()
            testee.loadDaxDialog()
            awaitItem() // INITIAL_REINSTALL_USER
            testee.onSecondaryCtaClicked()
            val state = awaitItem()
            assertEquals(PreOnboardingDialogType.SKIP_ONBOARDING_OPTION, state.currentDialog)
            assertTrue(state.isReinstallUser)
            cancelAndConsumeRemainingEvents()
        }
        verify(mockPixel).fire(PREONBOARDING_SKIP_ONBOARDING_PRESSED)
    }

    @Test
    fun whenSecondaryCtaFromSkipOnboardingThenShowComparisonChartAndFirePixel() = runTest {
        whenever(mockAppBuildConfig.isAppReinstall()).thenReturn(true)
        val testee = createViewModel()
        testee.loadDaxDialog()
        testee.onSecondaryCtaClicked() // INITIAL_REINSTALL_USER -> SKIP_ONBOARDING_OPTION
        testee.viewState.test {
            val currentState = awaitItem()
            assertEquals(PreOnboardingDialogType.SKIP_ONBOARDING_OPTION, currentState.currentDialog)
            testee.onSecondaryCtaClicked()
            val state = awaitItem()
            assertEquals(PreOnboardingDialogType.COMPARISON_CHART, state.currentDialog)
            cancelAndConsumeRemainingEvents()
        }
        verify(mockPixel).fire(PREONBOARDING_RESUME_ONBOARDING_PRESSED)
    }

    // endregion

    // region onDefaultBrowserSet / onDefaultBrowserNotSet

    @Test
    fun whenDefaultBrowserSetThenViewStateShowsAddressBarPositionAndStoresTrue() = runTest {
        val testee = createViewModel()
        testee.viewState.test {
            awaitItem()
            testee.onDefaultBrowserSet()
            val state = awaitItem()
            assertEquals(PreOnboardingDialogType.ADDRESS_BAR_POSITION, state.currentDialog)
            cancelAndConsumeRemainingEvents()
        }
        verify(mockAppInstallStore).defaultBrowser = true
    }

    @Test
    fun whenDefaultBrowserNotSetThenViewStateShowsAddressBarPositionAndStoresFalse() = runTest {
        val testee = createViewModel()
        testee.viewState.test {
            awaitItem()
            testee.onDefaultBrowserNotSet()
            val state = awaitItem()
            assertEquals(PreOnboardingDialogType.ADDRESS_BAR_POSITION, state.currentDialog)
            cancelAndConsumeRemainingEvents()
        }
        verify(mockAppInstallStore).defaultBrowser = false
    }

    @Test
    fun whenSplitOmnibarEnabledThenOnDefaultBrowserSetShowsSplitOptionTrue() = runTest {
        mockAndroidBrowserConfigFeature.splitOmnibar().setRawStoredState(Toggle.State(remoteEnableState = true))
        mockAndroidBrowserConfigFeature.splitOmnibarWelcomePage().setRawStoredState(Toggle.State(remoteEnableState = true))
        val testee = createViewModel()
        testee.viewState.test {
            awaitItem()
            testee.onDefaultBrowserSet()
            val state = awaitItem()
            assertTrue(state.showSplitOption)
            cancelAndConsumeRemainingEvents()
        }
    }

    // endregion

    // region Address bar position and input screen selection

    @Test
    fun whenAddressBarPositionSelectedThenViewStateUpdates() = runTest {
        val testee = createViewModel()
        testee.viewState.test {
            awaitItem()
            testee.onAddressBarPositionOptionSelected(OmnibarType.SINGLE_BOTTOM)
            val state = awaitItem()
            assertEquals(OmnibarType.SINGLE_BOTTOM, state.selectedAddressBarPosition)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenInputScreenOptionSelectedThenViewStateUpdates() = runTest {
        val testee = createViewModel()
        testee.viewState.test {
            awaitItem()
            testee.onInputScreenOptionSelected(false)
            val state = awaitItem()
            assertEquals(false, state.inputScreenSelected)
            cancelAndConsumeRemainingEvents()
        }
    }

    // endregion

    // region maxPageCount

    @Test
    fun whenInputScreenOnboardingEnabledThenMaxPageCountIs3() = runTest {
        mockAndroidBrowserConfigFeature.showInputScreenOnboarding().setRawStoredState(Toggle.State(enable = true))
        val testee = createViewModel()
        advanceUntilIdle()
        assertEquals(3, testee.getMaxPageCount())
    }

    @Test
    fun whenInputScreenOnboardingDisabledThenMaxPageCountIs2() = runTest {
        mockAndroidBrowserConfigFeature.showInputScreenOnboarding().setRawStoredState(Toggle.State(enable = false))
        val testee = createViewModel()
        advanceUntilIdle()
        assertEquals(2, testee.getMaxPageCount())
    }

    // endregion

    // region Notification permissions

    @Test
    fun whenNotificationPermissionRequestedThenPixelFired() {
        val testee = createViewModel()
        testee.notificationRuntimePermissionRequested()
        verify(mockPixel).fire(NOTIFICATION_RUNTIME_PERMISSION_SHOWN)
    }

    @Test
    fun whenNotificationPermissionGrantedThenPixelFired() {
        val testee = createViewModel()
        testee.notificationRuntimePermissionGranted()
        verify(mockPixel).fire(
            AppPixelName.NOTIFICATIONS_ENABLED,
            mapOf(PixelParameter.FROM_ONBOARDING to true.toString()),
        )
    }

    // endregion

    // region Skip onboarding impression pixel

    @Test
    fun whenSkipOnboardingDialogShownThenFiresSkipOnboardingShownPixel() = runTest {
        whenever(mockAppBuildConfig.isAppReinstall()).thenReturn(true)
        val testee = createViewModel()
        testee.loadDaxDialog()
        testee.onSecondaryCtaClicked() // navigate to SKIP_ONBOARDING_OPTION
        verify(mockPixel).fire(PREONBOARDING_SKIP_ONBOARDING_SHOWN_UNIQUE, type = Unique())
    }

    @Test
    fun whenInputScreenDialogShownThenFiresChooseSearchExperiencePixel() = runTest {
        mockAndroidBrowserConfigFeature.showInputScreenOnboarding().setRawStoredState(Toggle.State(enable = true))
        val testee = createViewModel()
        testee.onDefaultBrowserSet()
        testee.onAddressBarPositionOptionSelected(OmnibarType.SINGLE_TOP)
        testee.onPrimaryCtaClicked() // ADDRESS_BAR_POSITION -> INPUT_SCREEN
        verify(mockPixel).fire(PREONBOARDING_CHOOSE_SEARCH_EXPERIENCE_IMPRESSIONS_UNIQUE, type = Unique())
    }

    // endregion
}
