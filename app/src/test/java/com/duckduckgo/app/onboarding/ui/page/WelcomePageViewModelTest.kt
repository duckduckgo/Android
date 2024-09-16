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
import com.duckduckgo.app.global.DefaultRoleBrowserDialog
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.onboarding.ui.page.WelcomePage.Companion.PreOnboardingDialogType
import com.duckduckgo.app.onboarding.ui.page.WelcomePageViewModel.Command.Finish
import com.duckduckgo.app.onboarding.ui.page.WelcomePageViewModel.Command.ShowComparisonChart
import com.duckduckgo.app.onboarding.ui.page.WelcomePageViewModel.Command.ShowDefaultBrowserDialog
import com.duckduckgo.app.onboarding.ui.page.WelcomePageViewModel.Command.ShowSuccessDialog
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.pixels.AppPixelName.NOTIFICATION_RUNTIME_PERMISSION_SHOWN
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_AFFIRMATION_SHOWN_UNIQUE
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_CHOOSE_BROWSER_PRESSED
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_COMPARISON_CHART_SHOWN_UNIQUE
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_INTRO_SHOWN_UNIQUE
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.UNIQUE
import com.duckduckgo.common.test.CoroutineTestRule
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

    private val testee: WelcomePageViewModel by lazy {
        WelcomePageViewModel(
            mockDefaultRoleBrowserDialog,
            mockContext,
            mockPixel,
            mockAppInstallStore,
        )
    }

    @Test
    fun whenInitialDialogIsShownThenSendPixel() {
        testee.onDialogShown(PreOnboardingDialogType.INITIAL)

        verify(mockPixel).fire(PREONBOARDING_INTRO_SHOWN_UNIQUE, type = UNIQUE)
    }

    @Test
    fun whenComparisonChartDialogIsShownThenSendPixel() {
        testee.onDialogShown(PreOnboardingDialogType.COMPARISON_CHART)

        verify(mockPixel).fire(PREONBOARDING_COMPARISON_CHART_SHOWN_UNIQUE, type = UNIQUE)
    }

    @Test
    fun whenAffirmationDialogIsShownThenSendPixel() {
        testee.onDialogShown(PreOnboardingDialogType.CELEBRATION)

        verify(mockPixel).fire(PREONBOARDING_AFFIRMATION_SHOWN_UNIQUE, type = UNIQUE)
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
    fun givenInitialDialogWhenOnPrimaryCtaClickedThenShowComparisonChart() = runTest {
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
    fun whenChooseBrowserClickedIfDDGNotSetAsDefaultThenShowChooseBrowserDialog() = runTest {
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
    fun whenChooseBrowserClickedIfDDGSetAsDefaultThenFinishFlow() = runTest {
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
            mapOf(Pixel.PixelParameter.DEFAULT_BROWSER_SET_FROM_ONBOARDING to true.toString()),
        )
    }

    @Test
    fun whenDDGIsSetAsDefaultBrowserFromSystemDialogThenSetPreferenceAndSendPixel() {
        testee.onDefaultBrowserSet()

        verify(mockDefaultRoleBrowserDialog).dialogShown()
        verify(mockAppInstallStore).defaultBrowser = true
        verify(mockPixel).fire(
            AppPixelName.DEFAULT_BROWSER_SET,
            mapOf(Pixel.PixelParameter.DEFAULT_BROWSER_SET_FROM_ONBOARDING to true.toString()),
        )
    }

    @Test
    fun whenDDGIsSetAsDefaultBrowserFromOnboardingThenShowCelebrationScreen() = runTest {
        testee.onDefaultBrowserSet()

        testee.commands.test {
            val command = awaitItem()
            Assert.assertTrue(command is ShowSuccessDialog)
        }
    }

    @Test
    fun givenCelebrationDialogWhenOnPrimaryCtaClickedThenFinishFlow() = runTest {
        testee.onPrimaryCtaClicked(PreOnboardingDialogType.CELEBRATION)

        testee.commands.test {
            val command = awaitItem()
            Assert.assertTrue(command is Finish)
        }
    }
}
