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
import com.duckduckgo.app.pixels.AppPixelName.ONBOARDING_QUICK_SETUP
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

class RealQuickSetupPixelSenderTest {

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

    private val testee = RealQuickSetupPixelSender(
        appCoroutineScope = coroutineRule.testScope,
        pixel = mockPixel,
        dispatchers = coroutineRule.testDispatcherProvider,
        appInstallStore = mockAppInstallStore,
        defaultBrowserDetector = mockDefaultBrowserDetector,
        widgetCapabilities = mockWidgetCapabilities,
        deviceInfo = mockDeviceInfo,
    )

    @Test
    fun whenFireShownThenFiresShownPixelWithStandardParams() = runTest {
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(3))

        testee.fireShown(isReinstallUser = true)

        verify(mockPixel).fire(
            ONBOARDING_QUICK_SETUP,
            mapOf(
                "it" to "reinstall",
                "source" to "default",
                "flow" to "default",
                "pixelSource" to "phone",
                "d" to "3",
                "e" to "shown",
            ),
            type = Unique(tag = "onboarding_quick-setup_shown"),
        )
    }

    @Test
    fun whenFireShownForNewUserThenInstallTypeIsNew() = runTest {
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis())

        testee.fireShown(isReinstallUser = false)

        verify(mockPixel).fire(
            ONBOARDING_QUICK_SETUP,
            mapOf(
                "it" to "new",
                "source" to "default",
                "flow" to "default",
                "pixelSource" to "phone",
                "d" to "0",
                "e" to "shown",
            ),
            type = Unique(tag = "onboarding_quick-setup_shown"),
        )
    }

    @Test
    fun whenFireShownAndInstalledMoreThan28DaysAgoThenOmitDaysParam() = runTest {
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(40))

        testee.fireShown(isReinstallUser = true)

        verify(mockPixel).fire(
            ONBOARDING_QUICK_SETUP,
            mapOf(
                "it" to "reinstall",
                "source" to "default",
                "flow" to "default",
                "pixelSource" to "phone",
                "e" to "shown",
            ),
            type = Unique(tag = "onboarding_quick-setup_shown"),
        )
    }

    @Test
    fun whenFireShownOnTabletThenPixelSourceIsTablet() = runTest {
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis())
        whenever(mockDeviceInfo.formFactor()).thenReturn(DeviceInfo.FormFactor.TABLET)

        testee.fireShown(isReinstallUser = true)

        verify(mockPixel).fire(
            ONBOARDING_QUICK_SETUP,
            mapOf(
                "it" to "reinstall",
                "source" to "default",
                "flow" to "default",
                "pixelSource" to "tablet",
                "d" to "0",
                "e" to "shown",
            ),
            type = Unique(tag = "onboarding_quick-setup_shown"),
        )
    }

    @Test
    fun whenFireClickedWithNoConfigurationThenFiresClickedPixelWithCompositeValueAndStandardParams() = runTest {
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis())
        whenever(mockDefaultBrowserDetector.isDefaultBrowser()).thenReturn(false)
        whenever(mockWidgetCapabilities.hasInstalledWidgets).thenReturn(false)

        testee.fireClicked(
            isReinstallUser = true,
            addressBarPosition = OmnibarType.SINGLE_TOP,
            inputScreenSelected = true,
        )

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
    fun whenFireClickedWithDefaultBrowserSetAndBottomSearchOnlyThenValueReflectsSelections() = runTest {
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis())
        whenever(mockDefaultBrowserDetector.isDefaultBrowser()).thenReturn(true)
        whenever(mockWidgetCapabilities.hasInstalledWidgets).thenReturn(false)

        testee.fireClicked(
            isReinstallUser = true,
            addressBarPosition = OmnibarType.SINGLE_BOTTOM,
            inputScreenSelected = false,
        )

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
    fun whenFireClickedWithSplitWidgetAndDuckAiThenValueReflectsSelections() = runTest {
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis())
        whenever(mockDefaultBrowserDetector.isDefaultBrowser()).thenReturn(false)
        whenever(mockWidgetCapabilities.hasInstalledWidgets).thenReturn(true)

        testee.fireClicked(
            isReinstallUser = true,
            addressBarPosition = OmnibarType.SPLIT,
            inputScreenSelected = true,
        )

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
