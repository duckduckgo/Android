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

package com.duckduckgo.app.onboarding.ui.page

import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserDetector
import com.duckduckgo.app.browser.omnibar.OmnibarType
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.pixels.AppPixelName.ONBOARDING_ADDRESS_BAR_POSITION
import com.duckduckgo.app.pixels.AppPixelName.ONBOARDING_NOTIFICATIONS
import com.duckduckgo.app.pixels.AppPixelName.ONBOARDING_QUICK_SETUP
import com.duckduckgo.app.pixels.AppPixelName.ONBOARDING_SEARCH_EXPERIENCE
import com.duckduckgo.app.pixels.AppPixelName.ONBOARDING_SET_DEFAULT
import com.duckduckgo.app.pixels.AppPixelName.ONBOARDING_SKIP_ONBOARDING
import com.duckduckgo.app.pixels.AppPixelName.ONBOARDING_WELCOME
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Unique
import com.duckduckgo.app.widget.ui.WidgetCapabilities
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.device.DeviceInfo
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.TimeUnit

class RealBrandDesignOnboardingPixelSenderTest {

    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

    private val mockPixel: Pixel = mock()
    private val mockAppInstallStore: AppInstallStore = mock()
    private val mockDefaultBrowserDetector: DefaultBrowserDetector = mock()
    private val mockWidgetCapabilities: WidgetCapabilities = mock()
    private val mockDeviceInfo: DeviceInfo = mock {
        on { formFactor() } doReturn DeviceInfo.FormFactor.PHONE
    }

    private val testee = RealBrandDesignOnboardingPixelSender(
        appCoroutineScope = coroutineRule.testScope,
        pixel = mockPixel,
        dispatchers = coroutineRule.testDispatcherProvider,
        appInstallStore = mockAppInstallStore,
        defaultBrowserDetector = mockDefaultBrowserDetector,
        widgetCapabilities = mockWidgetCapabilities,
        deviceInfo = mockDeviceInfo,
    )

    @Test
    fun whenFireWelcomeShownForNewUserThenStandardShownParams() = runTest {
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis())

        testee.fireWelcomeShown(isReinstallUser = false)

        verify(mockPixel).fire(
            ONBOARDING_WELCOME,
            mapOf("it" to "new", "source" to "default", "flow" to "default", "pixelSource" to "phone", "d" to "0", "e" to "shown"),
            type = Unique(tag = "onboarding_welcome_shown"),
        )
    }

    @Test
    fun whenFireWelcomeClickedEngagedForReinstallThenValueEngageAndTagIncludesValue() = runTest {
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(3))

        testee.fireWelcomeClicked(isReinstallUser = true, engaged = true)

        verify(mockPixel).fire(
            ONBOARDING_WELCOME,
            mapOf(
                "it" to "reinstall",
                "source" to "default",
                "flow" to "default",
                "pixelSource" to "phone",
                "d" to "3",
                "e" to "clicked",
                "value" to "engage",
            ),
            type = Unique(tag = "onboarding_welcome_clicked_engage"),
        )
    }

    @Test
    fun whenFireWelcomeClickedNotEngagedThenValueDismiss() = runTest {
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis())

        testee.fireWelcomeClicked(isReinstallUser = false, engaged = false)

        verify(mockPixel).fire(
            ONBOARDING_WELCOME,
            mapOf(
                "it" to "new",
                "source" to "default",
                "flow" to "default",
                "pixelSource" to "phone",
                "d" to "0",
                "e" to "clicked",
                "value" to "dismiss",
            ),
            type = Unique(tag = "onboarding_welcome_clicked_dismiss"),
        )
    }

    @Test
    fun whenInstalledMoreThan28DaysAgoThenDaysParamOmitted() = runTest {
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(40))

        testee.fireSkipOnboardingShown(isReinstallUser = false)

        verify(mockPixel).fire(
            ONBOARDING_SKIP_ONBOARDING,
            mapOf("it" to "new", "source" to "default", "flow" to "default", "pixelSource" to "phone", "e" to "shown"),
            type = Unique(tag = "onboarding_skip-onboarding_shown"),
        )
    }

    @Test
    fun whenFireAddressBarPositionClickedSplitThenValueSplit() = runTest {
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis())

        testee.fireAddressBarPositionClicked(isReinstallUser = false, position = OmnibarType.SPLIT)

        verify(mockPixel).fire(
            ONBOARDING_ADDRESS_BAR_POSITION,
            mapOf(
                "it" to "new",
                "source" to "default",
                "flow" to "default",
                "pixelSource" to "phone",
                "d" to "0",
                "e" to "clicked",
                "value" to "split",
            ),
            type = Unique(tag = "onboarding_address-bar-position_clicked_split"),
        )
    }

    @Test
    fun whenFireSearchExperienceClickedWithAiThenValueSearchPlusDuckai() = runTest {
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis())

        testee.fireSearchExperienceClicked(isReinstallUser = false, withAi = true)

        verify(mockPixel).fire(
            ONBOARDING_SEARCH_EXPERIENCE,
            mapOf(
                "it" to "new",
                "source" to "default",
                "flow" to "default",
                "pixelSource" to "phone",
                "d" to "0",
                "e" to "clicked",
                "value" to "search_plus_duckai",
            ),
            type = Unique(tag = "onboarding_search-experience_clicked_search_plus_duckai"),
        )
    }

    @Test
    fun whenFireSearchExperienceClickedWithoutAiThenValueSearchOnly() = runTest {
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis())

        testee.fireSearchExperienceClicked(isReinstallUser = false, withAi = false)

        verify(mockPixel).fire(
            ONBOARDING_SEARCH_EXPERIENCE,
            mapOf(
                "it" to "new",
                "source" to "default",
                "flow" to "default",
                "pixelSource" to "phone",
                "d" to "0",
                "e" to "clicked",
                "value" to "search_only",
            ),
            type = Unique(tag = "onboarding_search-experience_clicked_search_only"),
        )
    }

    @Test
    fun whenFireSetDefaultConfirmedDdgThenValueDdg() = runTest {
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis())

        testee.fireSetDefaultConfirmed(isReinstallUser = false, isDdgDefault = true)

        verify(mockPixel).fire(
            ONBOARDING_SET_DEFAULT,
            mapOf(
                "it" to "new",
                "source" to "default",
                "flow" to "default",
                "pixelSource" to "phone",
                "d" to "0",
                "e" to "confirmed",
                "value" to "ddg",
            ),
            type = Unique(tag = "onboarding_set-default_confirmed_ddg"),
        )
    }

    @Test
    fun whenFireSetDefaultConfirmedNotDdgThenValueOther() = runTest {
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis())

        testee.fireSetDefaultConfirmed(isReinstallUser = false, isDdgDefault = false)

        verify(mockPixel).fire(
            ONBOARDING_SET_DEFAULT,
            mapOf(
                "it" to "new",
                "source" to "default",
                "flow" to "default",
                "pixelSource" to "phone",
                "d" to "0",
                "e" to "confirmed",
                "value" to "other",
            ),
            type = Unique(tag = "onboarding_set-default_confirmed_other"),
        )
    }

    @Test
    fun whenFireSetDefaultClickedThenNoValueAndTagWithoutValue() = runTest {
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis())

        testee.fireSetDefaultClicked(isReinstallUser = false)

        verify(mockPixel).fire(
            ONBOARDING_SET_DEFAULT,
            mapOf("it" to "new", "source" to "default", "flow" to "default", "pixelSource" to "phone", "d" to "0", "e" to "clicked"),
            type = Unique(tag = "onboarding_set-default_clicked"),
        )
    }

    @Test
    fun whenFireNotificationsConfirmedDeniedOnTabletThenValueDeniedAndPixelSourceTablet() = runTest {
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis())
        whenever(mockDeviceInfo.formFactor()).thenReturn(DeviceInfo.FormFactor.TABLET)

        testee.fireNotificationsConfirmed(isReinstallUser = false, granted = false)

        verify(mockPixel).fire(
            ONBOARDING_NOTIFICATIONS,
            mapOf(
                "it" to "new",
                "source" to "default",
                "flow" to "default",
                "pixelSource" to "tablet",
                "d" to "0",
                "e" to "confirmed",
                "value" to "denied",
            ),
            type = Unique(tag = "onboarding_notifications_confirmed_denied"),
        )
    }

    @Test
    fun whenFireQuickSetupShownThenFiresShownPixelWithStandardParams() = runTest {
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(3))

        testee.fireQuickSetupShown(isReinstallUser = true)

        verify(mockPixel).fire(
            ONBOARDING_QUICK_SETUP,
            mapOf("it" to "reinstall", "source" to "default", "flow" to "default", "pixelSource" to "phone", "d" to "3", "e" to "shown"),
            type = Unique(tag = "onboarding_quick-setup_shown"),
        )
    }

    @Test
    fun whenFireQuickSetupClickedWithNoConfigurationThenFiresClickedPixelWithCompositeValue() = runTest {
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis())
        whenever(mockDefaultBrowserDetector.isDefaultBrowser()).thenReturn(false)
        whenever(mockWidgetCapabilities.hasInstalledWidgets).thenReturn(false)

        testee.fireQuickSetupClicked(isReinstallUser = true, addressBarPosition = OmnibarType.SINGLE_TOP, inputScreenSelected = true)

        verify(mockPixel).fire(
            ONBOARDING_QUICK_SETUP,
            mapOf(
                "it" to "reinstall",
                "source" to "default",
                "flow" to "default",
                "pixelSource" to "phone",
                "d" to "0",
                "e" to "clicked",
                "value" to "set_as_default:off,widget:off,address_bar:top,input_type:search_and_duckai",
            ),
            type = Unique(tag = "onboarding_quick-setup_clicked"),
        )
    }

    @Test
    fun whenFireQuickSetupClickedWithDefaultBrowserSetAndBottomSearchOnlyThenValueReflectsSelections() = runTest {
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis())
        whenever(mockDefaultBrowserDetector.isDefaultBrowser()).thenReturn(true)
        whenever(mockWidgetCapabilities.hasInstalledWidgets).thenReturn(false)

        testee.fireQuickSetupClicked(isReinstallUser = true, addressBarPosition = OmnibarType.SINGLE_BOTTOM, inputScreenSelected = false)

        verify(mockPixel).fire(
            ONBOARDING_QUICK_SETUP,
            mapOf(
                "it" to "reinstall",
                "source" to "default",
                "flow" to "default",
                "pixelSource" to "phone",
                "d" to "0",
                "e" to "clicked",
                "value" to "set_as_default:on,widget:off,address_bar:bottom,input_type:search",
            ),
            type = Unique(tag = "onboarding_quick-setup_clicked"),
        )
    }

    @Test
    fun whenFireQuickSetupClickedWithSplitWidgetAndDuckAiThenValueReflectsSelections() = runTest {
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis())
        whenever(mockDefaultBrowserDetector.isDefaultBrowser()).thenReturn(false)
        whenever(mockWidgetCapabilities.hasInstalledWidgets).thenReturn(true)

        testee.fireQuickSetupClicked(isReinstallUser = true, addressBarPosition = OmnibarType.SPLIT, inputScreenSelected = true)

        verify(mockPixel).fire(
            ONBOARDING_QUICK_SETUP,
            mapOf(
                "it" to "reinstall",
                "source" to "default",
                "flow" to "default",
                "pixelSource" to "phone",
                "d" to "0",
                "e" to "clicked",
                "value" to "set_as_default:off,widget:on,address_bar:split,input_type:search_and_duckai",
            ),
            type = Unique(tag = "onboarding_quick-setup_clicked"),
        )
    }
}
