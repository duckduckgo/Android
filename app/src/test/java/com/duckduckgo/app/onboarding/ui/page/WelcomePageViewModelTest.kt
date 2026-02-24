/*
 * Copyright (c) 2024 DuckDuckGo
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

import android.content.Context
import android.content.Intent
import app.cash.turbine.test
import com.duckduckgo.app.browser.omnibar.OmnibarType
import com.duckduckgo.app.global.DefaultRoleBrowserDialog
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.onboarding.ui.page.WelcomePageViewModel.Command.Finish
import com.duckduckgo.app.onboarding.ui.page.WelcomePageViewModel.Command.OnboardingSkipped
import com.duckduckgo.app.onboarding.ui.page.WelcomePageViewModel.Command.ShowAddressBarPositionDialog
import com.duckduckgo.app.onboarding.ui.page.WelcomePageViewModel.Command.ShowComparisonChart
import com.duckduckgo.app.onboarding.ui.page.WelcomePageViewModel.Command.ShowDefaultBrowserDialog
import com.duckduckgo.app.onboarding.ui.page.WelcomePageViewModel.Command.ShowInitialDialog
import com.duckduckgo.app.onboarding.ui.page.WelcomePageViewModel.Command.ShowInitialReinstallUserDialog
import com.duckduckgo.app.onboarding.ui.page.WelcomePageViewModel.Command.ShowSkipOnboardingOption
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.pixels.AppPixelName.NOTIFICATION_RUNTIME_PERMISSION_SHOWN
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
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class WelcomePageViewModelTest {
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

    private fun createViewModel(): WelcomePageViewModel {
        return WelcomePageViewModel(
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

    private val testee: WelcomePageViewModel by lazy {
        WelcomePageViewModel(
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

    @Test
    fun whenInitialDialogIsShownThenSendPixel() {
        testee.onDialogShown(PreOnboardingDialogType.INITIAL)

        verify(mockPixel).fire(PREONBOARDING_INTRO_SHOWN_UNIQUE, type = Unique())
    }

    @Test
    fun whenComparisonChartDialogIsShownThenSendPixel() {
        testee.onDialogShown(PreOnboardingDialogType.COMPARISON_CHART)

        verify(mockPixel).fire(PREONBOARDING_COMPARISON_CHART_SHOWN_UNIQUE, type = Unique())
    }

    @Test
    fun whenNotificationsRuntimePermissionsAreRequestedSendPixel() {
        testee.notificationRuntimePermissionRequested()

        verify(mockPixel).fire(NOTIFICATION_RUNTIME_PERMISSION_SHOWN)
    }

    @Test
    fun whenNotificationsRuntimePermissionsAreGrantedThenSendPixel() {
        testee.notificationRuntimePermissionGranted()

        verify(mockPixel).fire(
            AppPixelName.NOTIFICATIONS_ENABLED,
            mapOf(PixelParameter.FROM_ONBOARDING to true.toString()),
        )
    }

    @Test
    fun givenInitialDialogWhenOnPrimaryCtaClickedThenShowComparisonChart() =
        runTest {
            testee.onPrimaryCtaClicked(PreOnboardingDialogType.INITIAL)

            testee.commands.test {
                val command = awaitItem()
                Assert.assertTrue(command is ShowComparisonChart)
            }
        }

    @Test
    fun givenComparisonChartDialogWhenOnPrimaryCtaClickedThenSendPixel() {
        whenever(mockDefaultRoleBrowserDialog.shouldShowDialog()).thenReturn(true)
        testee.onPrimaryCtaClicked(PreOnboardingDialogType.COMPARISON_CHART)

        verify(mockPixel).fire(
            PREONBOARDING_CHOOSE_BROWSER_PRESSED,
            mapOf(PixelParameter.DEFAULT_BROWSER to "false"),
        )
    }

    @Test
    fun whenChooseBrowserClickedIfDDGNotSetAsDefaultThenShowChooseBrowserDialog() =
        runTest {
            val mockIntent: Intent = mock()
            whenever(mockDefaultRoleBrowserDialog.createIntent(mockContext)).thenReturn(mockIntent)
            whenever(mockDefaultRoleBrowserDialog.shouldShowDialog()).thenReturn(true)
            testee.onPrimaryCtaClicked(PreOnboardingDialogType.COMPARISON_CHART)

            testee.commands.test {
                val command = awaitItem()
                Assert.assertTrue(command is ShowDefaultBrowserDialog)
            }
        }

    @Test
    fun whenChooseBrowserClickedIfDDGSetAsDefaultThenFinishFlow() =
        runTest {
            whenever(mockDefaultRoleBrowserDialog.shouldShowDialog()).thenReturn(false)
            testee.onPrimaryCtaClicked(PreOnboardingDialogType.COMPARISON_CHART)

            testee.commands.test {
                val command = awaitItem()
                Assert.assertTrue(command is Finish)
            }
        }

    @Test
    fun whenDDGIsNOTSetAsDefaultBrowserFromSystemDialogThenSetPreferenceAndSendPixel() {
        testee.onDefaultBrowserNotSet()

        verify(mockDefaultRoleBrowserDialog).dialogShown()
        verify(mockAppInstallStore).defaultBrowser = false
        verify(mockPixel).fire(
            AppPixelName.DEFAULT_BROWSER_NOT_SET,
            mapOf(PixelParameter.DEFAULT_BROWSER_SET_FROM_ONBOARDING to true.toString()),
        )
    }

    @Test
    fun whenDDGIsSetAsDefaultBrowserFromSystemDialogThenSetPreferenceAndSendPixel() {
        testee.onDefaultBrowserSet()

        verify(mockDefaultRoleBrowserDialog).dialogShown()
        verify(mockAppInstallStore).defaultBrowser = true
        verify(mockPixel).fire(
            AppPixelName.DEFAULT_BROWSER_SET,
            mapOf(PixelParameter.DEFAULT_BROWSER_SET_FROM_ONBOARDING to true.toString()),
        )
    }

    @Test
    fun whenDDGIsSetAsDefaultBrowserFromOnboardingThenShowAddressBarPositionDialog() =
        runTest {
            testee.onDefaultBrowserSet()

            testee.commands.test {
                val command = awaitItem()
                Assert.assertTrue(command is ShowAddressBarPositionDialog)
            }
        }

    @Test
    fun whenSplitOmnibarFeatureIsEnabledThenShowAddressBarPositionDialogWithSplitOption() =
        runTest {
            mockAndroidBrowserConfigFeature.splitOmnibar().setRawStoredState(Toggle.State(remoteEnableState = true))
            mockAndroidBrowserConfigFeature.splitOmnibarWelcomePage().setRawStoredState(Toggle.State(remoteEnableState = true))
            testee.onDefaultBrowserSet()

            testee.commands.test {
                val command = awaitItem()
                Assert.assertTrue(command is ShowAddressBarPositionDialog)
                Assert.assertTrue((command as ShowAddressBarPositionDialog).showSplitOption)
            }
        }

    @Test
    fun whenOnPrimaryCtaClickedThenFinishFlow() =
        runTest {
            testee.onPrimaryCtaClicked(PreOnboardingDialogType.ADDRESS_BAR_POSITION)

            testee.commands.test {
                val command = awaitItem()
                Assert.assertTrue(command is Finish)
            }
        }

    @Test
    fun whenBottomAddressBarIsSelectedThenSendPixel() =
        runTest {
            testee.onAddressBarPositionOptionSelected(OmnibarType.SINGLE_BOTTOM)
            testee.onPrimaryCtaClicked(PreOnboardingDialogType.ADDRESS_BAR_POSITION)

            verify(mockPixel).fire(PREONBOARDING_BOTTOM_ADDRESS_BAR_SELECTED_UNIQUE)
        }

    @Test
    fun whenBottomAddressBarIsSelectedThenSetUserSetting() =
        runTest {
            testee.onAddressBarPositionOptionSelected(OmnibarType.SINGLE_BOTTOM)
            testee.onPrimaryCtaClicked(PreOnboardingDialogType.ADDRESS_BAR_POSITION)

            verify(mockSettingsDataStore).omnibarType = OmnibarType.SINGLE_BOTTOM
        }

    @Test
    fun whenSplitAddressBarIsSelectedAndFeatureEnabledThenSetUserSetting() =
        runTest {
            mockAndroidBrowserConfigFeature.splitOmnibar().setRawStoredState(Toggle.State(remoteEnableState = true))
            mockAndroidBrowserConfigFeature.splitOmnibarWelcomePage().setRawStoredState(Toggle.State(remoteEnableState = true))
            testee.onAddressBarPositionOptionSelected(OmnibarType.SPLIT)
            testee.onPrimaryCtaClicked(PreOnboardingDialogType.ADDRESS_BAR_POSITION)

            verify(mockSettingsDataStore).omnibarType = OmnibarType.SPLIT
        }

    @Test
    fun whenSplitAddressBarIsSelectedAndFeatureEnabledThenSendPixel() =
        runTest {
            mockAndroidBrowserConfigFeature.splitOmnibar().setRawStoredState(Toggle.State(remoteEnableState = true))
            mockAndroidBrowserConfigFeature.splitOmnibarWelcomePage().setRawStoredState(Toggle.State(remoteEnableState = true))
            testee.onAddressBarPositionOptionSelected(OmnibarType.SPLIT)
            testee.onPrimaryCtaClicked(PreOnboardingDialogType.ADDRESS_BAR_POSITION)

            verify(mockPixel).fire(PREONBOARDING_SPLIT_ADDRESS_BAR_SELECTED_UNIQUE)
        }

    @Test
    fun whenSplitAddressBarIsSelectedAndFeatureDisabledThenDoNotSetUserSetting() =
        runTest {
            // When splitOmnibarWelcomePage is disabled, split option should not be available
            mockAndroidBrowserConfigFeature.splitOmnibar().setRawStoredState(Toggle.State(remoteEnableState = true))
            mockAndroidBrowserConfigFeature.splitOmnibarWelcomePage().setRawStoredState(Toggle.State(remoteEnableState = false))
            testee.onAddressBarPositionOptionSelected(OmnibarType.SPLIT)
            testee.onPrimaryCtaClicked(PreOnboardingDialogType.ADDRESS_BAR_POSITION)

            verify(mockSettingsDataStore, org.mockito.kotlin.never()).omnibarType = OmnibarType.SPLIT
        }

    @Test
    fun whenSplitAddressBarIsSelectedAndMainFeatureDisabledThenDoNotSetUserSetting() =
        runTest {
            // When main splitOmnibar feature is disabled, split option should not be available
            mockAndroidBrowserConfigFeature.splitOmnibar().setRawStoredState(Toggle.State(remoteEnableState = false))
            mockAndroidBrowserConfigFeature.splitOmnibarWelcomePage().setRawStoredState(Toggle.State(remoteEnableState = true))
            testee.onAddressBarPositionOptionSelected(OmnibarType.SPLIT)
            testee.onPrimaryCtaClicked(PreOnboardingDialogType.ADDRESS_BAR_POSITION)

            verify(mockSettingsDataStore, org.mockito.kotlin.never()).omnibarType = OmnibarType.SPLIT
        }

    @Test
    fun whenLoadingInitialDaxDialogWithReinstallFalseThenShowDaxInitialCta() =
        runTest {
            whenever(mockAppBuildConfig.isAppReinstall()).thenReturn(false)

            testee.loadDaxDialog()

            testee.commands.test {
                val command = awaitItem()
                Assert.assertTrue(command is ShowInitialDialog)
            }
        }

    @Test
    fun whenInitialReinstallUserDialogIsShownThenSendPixel() {
        testee.onDialogShown(PreOnboardingDialogType.INITIAL_REINSTALL_USER)

        verify(mockPixel).fire(PREONBOARDING_INTRO_REINSTALL_USER_SHOWN_UNIQUE, type = Unique())
    }

    @Test
    fun whenSkipOnboardingDialogIsShownThenSendPixel() {
        testee.onDialogShown(PreOnboardingDialogType.SKIP_ONBOARDING_OPTION)

        verify(mockPixel).fire(PREONBOARDING_SKIP_ONBOARDING_SHOWN_UNIQUE, type = Unique())
    }

    @Test
    fun whenLoadingInitialDaxDialogWithReinstallTrueThenShowDaxInitialReinstallUserCta() =
        runTest {
            whenever(mockAppBuildConfig.isAppReinstall()).thenReturn(true)

            testee.loadDaxDialog()

            testee.commands.test {
                val command = awaitItem()
                Assert.assertTrue(command is ShowInitialReinstallUserDialog)
            }
        }

    @Test
    fun givenInitialReinstallUserDialogWhenOnPrimaryCtaClickedThenShowComparisonChart() =
        runTest {
            testee.onPrimaryCtaClicked(PreOnboardingDialogType.INITIAL_REINSTALL_USER)

            testee.commands.test {
                val command = awaitItem()
                Assert.assertTrue(command is ShowComparisonChart)
            }
        }

    @Test
    fun givenSkipOnboardingDialogWhenOnPrimaryCtaClickedThenShowOnboardingSkippedAndSendPixel() =
        runTest {
            testee.onPrimaryCtaClicked(PreOnboardingDialogType.SKIP_ONBOARDING_OPTION)

            testee.commands.test {
                val command = awaitItem()
                Assert.assertTrue(command is OnboardingSkipped)
            }
            verify(mockPixel).fire(PREONBOARDING_CONFIRM_SKIP_ONBOARDING_PRESSED)
        }

    @Test
    fun givenInitialReinstallUserDialogWhenOnSecondaryCtaClickedThenShowSkipOnboardingOptionAndSendPixel() =
        runTest {
            testee.onSecondaryCtaClicked(PreOnboardingDialogType.INITIAL_REINSTALL_USER)

            testee.commands.test {
                val command = awaitItem()
                Assert.assertTrue(command is ShowSkipOnboardingOption)
            }
            verify(mockPixel).fire(PREONBOARDING_SKIP_ONBOARDING_PRESSED)
        }

    @Test
    fun givenSkipOnboardingDialogWhenOnSecondaryCtaClickedThenShowComparisonChartAndSendPixel() =
        runTest {
            testee.onSecondaryCtaClicked(PreOnboardingDialogType.SKIP_ONBOARDING_OPTION)

            testee.commands.test {
                val command = awaitItem()
                Assert.assertTrue(command is ShowComparisonChart)
            }
            verify(mockPixel).fire(PREONBOARDING_RESUME_ONBOARDING_PRESSED)
        }

    @Test
    fun whenOnPrimaryCtaClickedWithInputScreenSelectedThenFireAiChatSelectedPixelAndStoreSelectionAndFinish() =
        runTest {
            mockAndroidBrowserConfigFeature.showInputScreenOnboarding().setRawStoredState(Toggle.State(enable = true))
            testee.onInputScreenOptionSelected(true)
            testee.onPrimaryCtaClicked(PreOnboardingDialogType.INPUT_SCREEN)

            testee.commands.test {
                val command = awaitItem()
                Assert.assertTrue(command is Finish)
            }
            verify(mockPixel).fire(PREONBOARDING_AICHAT_SELECTED)
            verify(mockOnboardingStore).storeInputScreenSelection(true)
            verify(mockDuckChat).setCosmeticInputScreenUserSetting(true)
            verify(mockInputScreenOnboardingWideEvent).onInputScreenEnabledDuringOnboarding(reinstallUser = false)
        }

    @Test
    fun whenOnPrimaryCtaClickedWithInputScreenNotSelectedThenFireSearchOnlySelectedPixelAndStoreSelectionAndFinish() =
        runTest {
            mockAndroidBrowserConfigFeature.showInputScreenOnboarding().setRawStoredState(Toggle.State(enable = true))
            testee.onInputScreenOptionSelected(false)
            testee.onPrimaryCtaClicked(PreOnboardingDialogType.INPUT_SCREEN)

            testee.commands.test {
                val command = awaitItem()
                Assert.assertTrue(command is Finish)
            }
            verify(mockPixel).fire(PREONBOARDING_SEARCH_ONLY_SELECTED)
            verify(mockOnboardingStore).storeInputScreenSelection(false)
            verify(mockDuckChat).setCosmeticInputScreenUserSetting(false)
        }

    @Test
    fun whenInputScreenOnboardingIsEnabledThenGetMaxPageCountReturns3() =
        runTest {
            mockAndroidBrowserConfigFeature.showInputScreenOnboarding().setRawStoredState(Toggle.State(enable = true))
            val viewModel = createViewModel()

            Assert.assertEquals(3, viewModel.getMaxPageCount())
        }

    @Test
    fun whenInputScreenOnboardingIsDisabledThenGetMaxPageCountReturns2() =
        runTest {
            mockAndroidBrowserConfigFeature.showInputScreenOnboarding().setRawStoredState(Toggle.State(enable = false))
            val viewModel = createViewModel()

            Assert.assertEquals(2, viewModel.getMaxPageCount())
        }

    @Test
    fun whenInputScreenDialogIsShownThenFireChooseSearchExperienceImpressionsUniquePixel() {
        testee.onDialogShown(PreOnboardingDialogType.INPUT_SCREEN)

        verify(mockPixel).fire(PREONBOARDING_CHOOSE_SEARCH_EXPERIENCE_IMPRESSIONS_UNIQUE, type = Unique())
    }

    @Test
    fun whenOnPrimaryCtaClickedWithInputScreenSelectedAndReinstallUserTrueThenCallWideEventWithReinstallUserTrue() =
        runTest {
            mockAndroidBrowserConfigFeature.showInputScreenOnboarding().setRawStoredState(Toggle.State(enable = true))
            testee.onSecondaryCtaClicked(PreOnboardingDialogType.INITIAL_REINSTALL_USER)

            testee.commands.test {
                val skipCommand = awaitItem()
                Assert.assertTrue(skipCommand is ShowSkipOnboardingOption)

                testee.onInputScreenOptionSelected(true)
                testee.onPrimaryCtaClicked(PreOnboardingDialogType.INPUT_SCREEN)

                val finishCommand = awaitItem()
                Assert.assertTrue(finishCommand is Finish)
            }
            verify(mockInputScreenOnboardingWideEvent).onInputScreenEnabledDuringOnboarding(reinstallUser = true)
        }
}
