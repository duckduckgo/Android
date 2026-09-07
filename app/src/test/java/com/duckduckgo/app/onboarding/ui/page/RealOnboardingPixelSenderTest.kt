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
import com.duckduckgo.app.onboarding.CustomAiOnboardingStore
import com.duckduckgo.app.onboarding.OnboardingPreference
import com.duckduckgo.app.onboarding.orchestrator.PasswordImportOutcome
import com.duckduckgo.app.onboarding.ui.page.configdriven.DownloadReasonSelection
import com.duckduckgo.app.pixels.OnboardingPixelName.ONBOARDING_ADDRESS_BAR_POSITION
import com.duckduckgo.app.pixels.OnboardingPixelName.ONBOARDING_AI_INTRO
import com.duckduckgo.app.pixels.OnboardingPixelName.ONBOARDING_DOWNLOAD_CHOICE
import com.duckduckgo.app.pixels.OnboardingPixelName.ONBOARDING_END
import com.duckduckgo.app.pixels.OnboardingPixelName.ONBOARDING_NOTIFICATIONS
import com.duckduckgo.app.pixels.OnboardingPixelName.ONBOARDING_PASSWORD_IMPORT
import com.duckduckgo.app.pixels.OnboardingPixelName.ONBOARDING_PREFERENCES_AD_BLOCKING
import com.duckduckgo.app.pixels.OnboardingPixelName.ONBOARDING_PREFERENCES_AI_MODEL
import com.duckduckgo.app.pixels.OnboardingPixelName.ONBOARDING_PREFERENCES_AI_SEARCH
import com.duckduckgo.app.pixels.OnboardingPixelName.ONBOARDING_PREFERENCES_SERP
import com.duckduckgo.app.pixels.OnboardingPixelName.ONBOARDING_QUICK_SETUP
import com.duckduckgo.app.pixels.OnboardingPixelName.ONBOARDING_SEARCH
import com.duckduckgo.app.pixels.OnboardingPixelName.ONBOARDING_SEARCH_CHAT_TOGGLE
import com.duckduckgo.app.pixels.OnboardingPixelName.ONBOARDING_SEARCH_EXPERIENCE
import com.duckduckgo.app.pixels.OnboardingPixelName.ONBOARDING_SET_DEFAULT
import com.duckduckgo.app.pixels.OnboardingPixelName.ONBOARDING_VISIT_SITE
import com.duckduckgo.app.pixels.OnboardingPixelName.ONBOARDING_WELCOME
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Unique
import com.duckduckgo.app.widget.ui.WidgetCapabilities
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.test.api.InMemorySharedPreferences
import com.duckduckgo.common.utils.device.DeviceInfo
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.TimeUnit

class RealOnboardingPixelSenderTest {

    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

    private val mockPixel: Pixel = mock()
    private val mockAppInstallStore: AppInstallStore = mock()
    private val mockCustomAiOnboardingStore: CustomAiOnboardingStore = mock {
        onBlocking { isEnabled() } doReturn false
    }
    private val fakePreferences = InMemorySharedPreferences()
    private val fakeSharedPreferencesProvider: SharedPreferencesProvider = mock {
        on { getSharedPreferences(any(), any(), any()) } doReturn fakePreferences
    }
    private val mockDefaultBrowserDetector: DefaultBrowserDetector = mock()
    private val mockWidgetCapabilities: WidgetCapabilities = mock()
    private val mockDeviceInfo: DeviceInfo = mock {
        on { formFactor() } doReturn DeviceInfo.FormFactor.PHONE
    }
    private val mockAppBuildConfig: AppBuildConfig = mock { onBlocking { isAppReinstall() } doReturn false }

    private val testee = RealOnboardingPixelSender(
        appCoroutineScope = coroutineRule.testScope,
        pixel = mockPixel,
        dispatchers = coroutineRule.testDispatcherProvider,
        appInstallStore = mockAppInstallStore,
        customAiOnboardingStore = mockCustomAiOnboardingStore,
        sharedPreferencesProvider = fakeSharedPreferencesProvider,
        defaultBrowserDetector = mockDefaultBrowserDetector,
        widgetCapabilities = mockWidgetCapabilities,
        deviceInfo = mockDeviceInfo,
        appBuildConfig = mockAppBuildConfig,
    )

    @Test
    fun whenFireWelcomeShownForNewUserThenStandardShownParams() = runTest {
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis())

        testee.fire(ONBOARDING_WELCOME, OnboardingPixelAction.Shown)

        verify(mockPixel).fire(
            ONBOARDING_WELCOME,
            mapOf("installType" to "new", "flow" to "default", "pixelSource" to "phone", "daysSinceInstall" to "0", "event" to "shown"),
            type = Unique(tag = "onboarding_welcome_shown"),
        )
    }

    @Test
    fun whenFireWelcomeClickedEngagedForReinstallThenValueEngageAndTagIncludesValue() = runTest {
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(3))

        val mockReinstallBuildConfig: AppBuildConfig = mock { onBlocking { isAppReinstall() } doReturn true }
        val reinstallTestee = RealOnboardingPixelSender(
            appCoroutineScope = coroutineRule.testScope,
            pixel = mockPixel,
            dispatchers = coroutineRule.testDispatcherProvider,
            appInstallStore = mockAppInstallStore,
            customAiOnboardingStore = mockCustomAiOnboardingStore,
            sharedPreferencesProvider = fakeSharedPreferencesProvider,
            defaultBrowserDetector = mockDefaultBrowserDetector,
            widgetCapabilities = mockWidgetCapabilities,
            deviceInfo = mockDeviceInfo,
            appBuildConfig = mockReinstallBuildConfig,
        )
        reinstallTestee.fire(ONBOARDING_WELCOME, OnboardingPixelAction.Clicked(engaged = true))

        verify(mockPixel).fire(
            ONBOARDING_WELCOME,
            mapOf(
                "installType" to "reinstall",
                "flow" to "default",
                "pixelSource" to "phone",
                "daysSinceInstall" to "1-3",
                "event" to "clicked",
                "value" to "engage",
            ),
            type = Unique(tag = "onboarding_welcome_clicked_engage"),
        )
    }

    @Test
    fun whenFireWelcomeClickedNotEngagedThenValueDismiss() = runTest {
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis())

        testee.fire(ONBOARDING_WELCOME, OnboardingPixelAction.Clicked(engaged = false))

        verify(mockPixel).fire(
            ONBOARDING_WELCOME,
            mapOf(
                "installType" to "new",
                "flow" to "default",
                "pixelSource" to "phone",
                "daysSinceInstall" to "0",
                "event" to "clicked",
                "value" to "dismiss",
            ),
            type = Unique(tag = "onboarding_welcome_clicked_dismiss"),
        )
    }

    @Test
    fun whenFireAddressBarPositionClickedSplitThenValueSplit() = runTest {
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis())

        testee.fire(ONBOARDING_ADDRESS_BAR_POSITION, OnboardingPixelAction.AddressBarClicked(position = OmnibarType.SPLIT))

        verify(mockPixel).fire(
            ONBOARDING_ADDRESS_BAR_POSITION,
            mapOf(
                "installType" to "new",
                "flow" to "default",
                "pixelSource" to "phone",
                "daysSinceInstall" to "0",
                "event" to "clicked",
                "value" to "split",
            ),
            type = Unique(tag = "onboarding_address-bar-position_clicked_split"),
        )
    }

    @Test
    fun whenFireSearchExperienceClickedWithAiThenValueSearchPlusDuckai() = runTest {
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis())

        testee.fire(ONBOARDING_SEARCH_EXPERIENCE, OnboardingPixelAction.SearchExperienceClicked(withAi = true))

        verify(mockPixel).fire(
            ONBOARDING_SEARCH_EXPERIENCE,
            mapOf(
                "installType" to "new",
                "flow" to "default",
                "pixelSource" to "phone",
                "daysSinceInstall" to "0",
                "event" to "clicked",
                "value" to "search_plus_duckai",
            ),
            type = Unique(tag = "onboarding_search-experience_clicked_search_plus_duckai"),
        )
    }

    @Test
    fun whenFireSearchExperienceClickedWithoutAiThenValueSearchOnly() = runTest {
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis())

        testee.fire(ONBOARDING_SEARCH_EXPERIENCE, OnboardingPixelAction.SearchExperienceClicked(withAi = false))

        verify(mockPixel).fire(
            ONBOARDING_SEARCH_EXPERIENCE,
            mapOf(
                "installType" to "new",
                "flow" to "default",
                "pixelSource" to "phone",
                "daysSinceInstall" to "0",
                "event" to "clicked",
                "value" to "search_only",
            ),
            type = Unique(tag = "onboarding_search-experience_clicked_search_only"),
        )
    }

    @Test
    fun whenFireSetDefaultConfirmedDdgThenValueDdg() = runTest {
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis())

        testee.fire(ONBOARDING_SET_DEFAULT, OnboardingPixelAction.SetDefaultConfirmed(isDdgDefault = true))

        verify(mockPixel).fire(
            ONBOARDING_SET_DEFAULT,
            mapOf(
                "installType" to "new",
                "flow" to "default",
                "pixelSource" to "phone",
                "daysSinceInstall" to "0",
                "event" to "confirmed",
                "value" to "ddg",
            ),
            type = Unique(tag = "onboarding_set-default_confirmed_ddg"),
        )
    }

    @Test
    fun whenFireSetDefaultConfirmedNotDdgThenValueOther() = runTest {
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis())

        testee.fire(ONBOARDING_SET_DEFAULT, OnboardingPixelAction.SetDefaultConfirmed(isDdgDefault = false))

        verify(mockPixel).fire(
            ONBOARDING_SET_DEFAULT,
            mapOf(
                "installType" to "new",
                "flow" to "default",
                "pixelSource" to "phone",
                "daysSinceInstall" to "0",
                "event" to "confirmed",
                "value" to "other",
            ),
            type = Unique(tag = "onboarding_set-default_confirmed_other"),
        )
    }

    @Test
    fun whenFireSetDefaultClickedEngagedThenValueEngage() = runTest {
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis())

        testee.fire(ONBOARDING_SET_DEFAULT, OnboardingPixelAction.Clicked(engaged = true))

        verify(mockPixel).fire(
            ONBOARDING_SET_DEFAULT,
            mapOf(
                "installType" to "new",
                "flow" to "default",
                "pixelSource" to "phone",
                "daysSinceInstall" to "0",
                "event" to "clicked",
                "value" to "engage",
            ),
            type = Unique(tag = "onboarding_set-default_clicked_engage"),
        )
    }

    @Test
    fun whenFireNotificationsConfirmedDeniedOnTabletThenValueDeniedAndPixelSourceTablet() = runTest {
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis())
        whenever(mockDeviceInfo.formFactor()).thenReturn(DeviceInfo.FormFactor.TABLET)

        testee.fire(ONBOARDING_NOTIFICATIONS, OnboardingPixelAction.NotificationsConfirmed(granted = false))

        verify(mockPixel).fire(
            ONBOARDING_NOTIFICATIONS,
            mapOf(
                "installType" to "new",
                "flow" to "default",
                "pixelSource" to "tablet",
                "daysSinceInstall" to "0",
                "event" to "confirmed",
                "value" to "denied",
            ),
            type = Unique(tag = "onboarding_notifications_confirmed_denied"),
        )
    }

    @Test
    fun whenFireQuickSetupShownThenFiresShownPixelWithStandardParams() = runTest {
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(3))

        val mockReinstallBuildConfig: AppBuildConfig = mock { onBlocking { isAppReinstall() } doReturn true }
        val reinstallTestee = RealOnboardingPixelSender(
            appCoroutineScope = coroutineRule.testScope,
            pixel = mockPixel,
            dispatchers = coroutineRule.testDispatcherProvider,
            appInstallStore = mockAppInstallStore,
            customAiOnboardingStore = mockCustomAiOnboardingStore,
            sharedPreferencesProvider = fakeSharedPreferencesProvider,
            defaultBrowserDetector = mockDefaultBrowserDetector,
            widgetCapabilities = mockWidgetCapabilities,
            deviceInfo = mockDeviceInfo,
            appBuildConfig = mockReinstallBuildConfig,
        )
        reinstallTestee.fire(ONBOARDING_QUICK_SETUP, OnboardingPixelAction.Shown)

        verify(mockPixel).fire(
            ONBOARDING_QUICK_SETUP,
            mapOf("installType" to "reinstall", "flow" to "default", "pixelSource" to "phone", "daysSinceInstall" to "1-3", "event" to "shown"),
            type = Unique(tag = "onboarding_quick-setup_shown"),
        )
    }

    @Test
    fun whenFireQuickSetupClickedWithNoConfigurationThenFiresClickedPixelWithCompositeValue() = runTest {
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis())
        whenever(mockDefaultBrowserDetector.isDefaultBrowser()).thenReturn(false)
        whenever(mockWidgetCapabilities.hasInstalledWidgets).thenReturn(false)

        val mockReinstallBuildConfig: AppBuildConfig = mock { onBlocking { isAppReinstall() } doReturn true }
        val reinstallTestee = RealOnboardingPixelSender(
            appCoroutineScope = coroutineRule.testScope,
            pixel = mockPixel,
            dispatchers = coroutineRule.testDispatcherProvider,
            appInstallStore = mockAppInstallStore,
            customAiOnboardingStore = mockCustomAiOnboardingStore,
            sharedPreferencesProvider = fakeSharedPreferencesProvider,
            defaultBrowserDetector = mockDefaultBrowserDetector,
            widgetCapabilities = mockWidgetCapabilities,
            deviceInfo = mockDeviceInfo,
            appBuildConfig = mockReinstallBuildConfig,
        )
        reinstallTestee.fire(
            ONBOARDING_QUICK_SETUP,
            OnboardingPixelAction.QuickSetupClicked(
                addressBarPosition = OmnibarType.SINGLE_TOP,
                inputScreenSelected = true,
            ),
        )

        verify(mockPixel).fire(
            ONBOARDING_QUICK_SETUP,
            mapOf(
                "installType" to "reinstall",
                "flow" to "default",
                "pixelSource" to "phone",
                "daysSinceInstall" to "0",
                "event" to "clicked",
                "value" to "set_as_default:off,widget:off,address_bar:top,input_type:search_and_duckai",
            ),
            type = Unique(
                tag = "onboarding_quick-setup_clicked_" +
                    "set_as_default:off,widget:off,address_bar:top,input_type:search_and_duckai",
            ),
        )
    }

    @Test
    fun whenFireQuickSetupClickedWithDefaultBrowserSetAndBottomSearchOnlyThenValueReflectsSelections() = runTest {
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis())
        whenever(mockDefaultBrowserDetector.isDefaultBrowser()).thenReturn(true)
        whenever(mockWidgetCapabilities.hasInstalledWidgets).thenReturn(false)

        val mockReinstallBuildConfig: AppBuildConfig = mock { onBlocking { isAppReinstall() } doReturn true }
        val reinstallTestee = RealOnboardingPixelSender(
            appCoroutineScope = coroutineRule.testScope,
            pixel = mockPixel,
            dispatchers = coroutineRule.testDispatcherProvider,
            appInstallStore = mockAppInstallStore,
            customAiOnboardingStore = mockCustomAiOnboardingStore,
            sharedPreferencesProvider = fakeSharedPreferencesProvider,
            defaultBrowserDetector = mockDefaultBrowserDetector,
            widgetCapabilities = mockWidgetCapabilities,
            deviceInfo = mockDeviceInfo,
            appBuildConfig = mockReinstallBuildConfig,
        )
        reinstallTestee.fire(
            ONBOARDING_QUICK_SETUP,
            OnboardingPixelAction.QuickSetupClicked(
                addressBarPosition = OmnibarType.SINGLE_BOTTOM,
                inputScreenSelected = false,
            ),
        )

        verify(mockPixel).fire(
            ONBOARDING_QUICK_SETUP,
            mapOf(
                "installType" to "reinstall",
                "flow" to "default",
                "pixelSource" to "phone",
                "daysSinceInstall" to "0",
                "event" to "clicked",
                "value" to "set_as_default:on,widget:off,address_bar:bottom,input_type:search",
            ),
            type = Unique(
                tag = "onboarding_quick-setup_clicked_" +
                    "set_as_default:on,widget:off,address_bar:bottom,input_type:search",
            ),
        )
    }

    @Test
    fun whenFireQuickSetupClickedWithSplitWidgetAndDuckAiThenValueReflectsSelections() = runTest {
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis())
        whenever(mockDefaultBrowserDetector.isDefaultBrowser()).thenReturn(false)
        whenever(mockWidgetCapabilities.hasInstalledWidgets).thenReturn(true)

        val mockReinstallBuildConfig: AppBuildConfig = mock { onBlocking { isAppReinstall() } doReturn true }
        val reinstallTestee = RealOnboardingPixelSender(
            appCoroutineScope = coroutineRule.testScope,
            pixel = mockPixel,
            dispatchers = coroutineRule.testDispatcherProvider,
            appInstallStore = mockAppInstallStore,
            customAiOnboardingStore = mockCustomAiOnboardingStore,
            sharedPreferencesProvider = fakeSharedPreferencesProvider,
            defaultBrowserDetector = mockDefaultBrowserDetector,
            widgetCapabilities = mockWidgetCapabilities,
            deviceInfo = mockDeviceInfo,
            appBuildConfig = mockReinstallBuildConfig,
        )
        reinstallTestee.fire(
            ONBOARDING_QUICK_SETUP,
            OnboardingPixelAction.QuickSetupClicked(
                addressBarPosition = OmnibarType.SPLIT,
                inputScreenSelected = true,
            ),
        )

        verify(mockPixel).fire(
            ONBOARDING_QUICK_SETUP,
            mapOf(
                "installType" to "reinstall",
                "flow" to "default",
                "pixelSource" to "phone",
                "daysSinceInstall" to "0",
                "event" to "clicked",
                "value" to "set_as_default:off,widget:on,address_bar:split,input_type:search_and_duckai",
            ),
            type = Unique(
                tag = "onboarding_quick-setup_clicked_" +
                    "set_as_default:off,widget:on,address_bar:split,input_type:search_and_duckai",
            ),
        )
    }

    @Test
    fun whenCustomAiFlowThenFlowDuckai() = runTest {
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis())
        whenever(mockCustomAiOnboardingStore.isEnabled()).thenReturn(true)

        testee.fire(ONBOARDING_WELCOME, OnboardingPixelAction.Shown)

        verify(mockPixel).fire(
            ONBOARDING_WELCOME,
            mapOf("installType" to "new", "flow" to "duckai", "pixelSource" to "phone", "daysSinceInstall" to "0", "event" to "shown"),
            type = Unique(tag = "onboarding_welcome_shown"),
        )
    }

    @Test
    fun whenDuckAiOnboardingFlowThenVariantParamIsChat() = runTest {
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis())
        whenever(mockCustomAiOnboardingStore.isEnabled()).thenReturn(true)
        val prefs = InMemorySharedPreferences()
        val variantTestee = RealOnboardingPixelSender(
            appCoroutineScope = coroutineRule.testScope,
            pixel = mockPixel,
            dispatchers = coroutineRule.testDispatcherProvider,
            appInstallStore = mockAppInstallStore,
            customAiOnboardingStore = mockCustomAiOnboardingStore,
            sharedPreferencesProvider = mock { on { getSharedPreferences(any(), any(), any()) } doReturn prefs },
            defaultBrowserDetector = mockDefaultBrowserDetector,
            widgetCapabilities = mockWidgetCapabilities,
            deviceInfo = mockDeviceInfo,
            appBuildConfig = mockAppBuildConfig,
        )
        variantTestee.chatBranchSelected()

        variantTestee.fire(ONBOARDING_SET_DEFAULT, OnboardingPixelAction.Shown)

        verify(mockPixel).fire(
            ONBOARDING_SET_DEFAULT,
            mapOf(
                "installType" to "new",
                "flow" to "duckai",
                "variant" to "search_plus_duckai-chat",
                "pixelSource" to "phone",
                "daysSinceInstall" to "0",
                "event" to "shown",
            ),
            type = Unique(tag = "onboarding_set-default_shown"),
        )
    }

    @Test
    fun whenSearchOnboardingFlowThenVariantParamIsSearch() = runTest {
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis())
        val prefs = InMemorySharedPreferences()
        val variantTestee = RealOnboardingPixelSender(
            appCoroutineScope = coroutineRule.testScope,
            pixel = mockPixel,
            dispatchers = coroutineRule.testDispatcherProvider,
            appInstallStore = mockAppInstallStore,
            customAiOnboardingStore = mockCustomAiOnboardingStore,
            sharedPreferencesProvider = mock { on { getSharedPreferences(any(), any(), any()) } doReturn prefs },
            defaultBrowserDetector = mockDefaultBrowserDetector,
            widgetCapabilities = mockWidgetCapabilities,
            deviceInfo = mockDeviceInfo,
            appBuildConfig = mockAppBuildConfig,
        )
        variantTestee.searchBranchSelected()

        variantTestee.fire(ONBOARDING_ADDRESS_BAR_POSITION, OnboardingPixelAction.Shown)

        verify(mockPixel).fire(
            ONBOARDING_ADDRESS_BAR_POSITION,
            mapOf(
                "installType" to "new",
                "flow" to "default",
                "variant" to "search_plus_duckai-search",
                "pixelSource" to "phone",
                "daysSinceInstall" to "0",
                "event" to "shown",
            ),
            type = Unique(tag = "onboarding_address-bar-position_shown"),
        )
    }

    @Test
    fun whenBranchSelectionClearedThenVariantParamNotSent() = runTest {
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis())
        whenever(mockCustomAiOnboardingStore.isEnabled()).thenReturn(true)
        val prefs = InMemorySharedPreferences()
        val variantTestee = RealOnboardingPixelSender(
            appCoroutineScope = coroutineRule.testScope,
            pixel = mockPixel,
            dispatchers = coroutineRule.testDispatcherProvider,
            appInstallStore = mockAppInstallStore,
            customAiOnboardingStore = mockCustomAiOnboardingStore,
            sharedPreferencesProvider = mock { on { getSharedPreferences(any(), any(), any()) } doReturn prefs },
            defaultBrowserDetector = mockDefaultBrowserDetector,
            widgetCapabilities = mockWidgetCapabilities,
            deviceInfo = mockDeviceInfo,
            appBuildConfig = mockAppBuildConfig,
        )
        variantTestee.chatBranchSelected()

        variantTestee.clearFlowAttribution()
        variantTestee.fire(ONBOARDING_AI_INTRO, OnboardingPixelAction.Shown)

        verify(mockPixel).fire(
            ONBOARDING_AI_INTRO,
            mapOf(
                "installType" to "new",
                "flow" to "duckai",
                "pixelSource" to "phone",
                "daysSinceInstall" to "0",
                "event" to "shown",
            ),
            type = Unique(tag = "onboarding_ai-intro_shown"),
        )
    }

    @Test
    fun whenFireSyncRestoreShownThenStandardShownParams() = runTest {
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis())

        testee.fire(ONBOARDING_WELCOME, OnboardingPixelAction.Shown)

        verify(mockPixel).fire(
            ONBOARDING_WELCOME,
            mapOf("installType" to "new", "flow" to "default", "pixelSource" to "phone", "daysSinceInstall" to "0", "event" to "shown"),
            type = Unique(tag = "onboarding_welcome_shown"),
        )
    }

    @Test
    fun whenFireSyncRestoreClickedEngagedThenValueEngage() = runTest {
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis())

        testee.fire(ONBOARDING_WELCOME, OnboardingPixelAction.Clicked(engaged = true))

        verify(mockPixel).fire(
            ONBOARDING_WELCOME,
            mapOf(
                "installType" to "new",
                "flow" to "default",
                "pixelSource" to "phone",
                "daysSinceInstall" to "0",
                "event" to "clicked",
                "value" to "engage",
            ),
            type = Unique(tag = "onboarding_welcome_clicked_engage"),
        )
    }

    @Test
    fun whenFireSyncRestoreClickedNotEngagedThenValueDismiss() = runTest {
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis())

        testee.fire(ONBOARDING_WELCOME, OnboardingPixelAction.Clicked(engaged = false))

        verify(mockPixel).fire(
            ONBOARDING_WELCOME,
            mapOf(
                "installType" to "new",
                "flow" to "default",
                "pixelSource" to "phone",
                "daysSinceInstall" to "0",
                "event" to "clicked",
                "value" to "dismiss",
            ),
            type = Unique(tag = "onboarding_welcome_clicked_dismiss"),
        )
    }

    @Test
    fun whenFireTryASearchShownThenStandardShownParams() = runTest {
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis())

        testee.fire(ONBOARDING_SEARCH_CHAT_TOGGLE, OnboardingPixelAction.Shown)

        verify(mockPixel).fire(
            ONBOARDING_SEARCH_CHAT_TOGGLE,
            mapOf("installType" to "new", "flow" to "default", "pixelSource" to "phone", "daysSinceInstall" to "0", "event" to "shown"),
            type = Unique(tag = "onboarding_search-chat-toggle_shown"),
        )
    }

    @Test
    fun whenFireTryInputClickedSuggestedSearchThenValueSuggestedSearch() = runTest {
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis())

        testee.fire(
            ONBOARDING_SEARCH_CHAT_TOGGLE,
            OnboardingPixelAction.TryInputClicked(fromSuggestion = true, isChat = false),
        )

        verify(mockPixel).fire(
            ONBOARDING_SEARCH_CHAT_TOGGLE,
            mapOf(
                "installType" to "new",
                "flow" to "default",
                "pixelSource" to "phone",
                "daysSinceInstall" to "0",
                "event" to "clicked",
                "value" to "suggested_search",
            ),
            type = Unique(tag = "onboarding_search-chat-toggle_clicked_suggested_search"),
        )
    }

    @Test
    fun whenFireTryInputClickedCustomChatThenValueCustomChat() = runTest {
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis())

        testee.fire(
            ONBOARDING_SEARCH_CHAT_TOGGLE,
            OnboardingPixelAction.TryInputClicked(fromSuggestion = false, isChat = true),
        )

        verify(mockPixel).fire(
            ONBOARDING_SEARCH_CHAT_TOGGLE,
            mapOf(
                "installType" to "new",
                "flow" to "default",
                "pixelSource" to "phone",
                "daysSinceInstall" to "0",
                "event" to "clicked",
                "value" to "custom_chat",
            ),
            type = Unique(tag = "onboarding_search-chat-toggle_clicked_custom_chat"),
        )
    }

    @Test
    fun whenFireAiComparisonShownThenStandardShownParams() = runTest {
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis())

        testee.fire(ONBOARDING_AI_INTRO, OnboardingPixelAction.Shown)

        verify(mockPixel).fire(
            ONBOARDING_AI_INTRO,
            mapOf("installType" to "new", "flow" to "default", "pixelSource" to "phone", "daysSinceInstall" to "0", "event" to "shown"),
            type = Unique(tag = "onboarding_ai-intro_shown"),
        )
    }

    @Test
    fun whenFireAiComparisonClickedEngagedThenValueEngage() = runTest {
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis())

        testee.fire(ONBOARDING_AI_INTRO, OnboardingPixelAction.Clicked(engaged = true))

        verify(mockPixel).fire(
            ONBOARDING_AI_INTRO,
            mapOf(
                "installType" to "new",
                "flow" to "default",
                "pixelSource" to "phone",
                "daysSinceInstall" to "0",
                "event" to "clicked",
                "value" to "engage",
            ),
            type = Unique(tag = "onboarding_ai-intro_clicked_engage"),
        )
    }

    @Test
    fun whenFireSuggestionClickedFromSuggestionThenValueSuggested() = runTest {
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis())

        testee.fire(ONBOARDING_SEARCH, OnboardingPixelAction.SuggestionClicked(fromSuggestion = true))

        verify(mockPixel).fire(
            ONBOARDING_SEARCH,
            mapOf(
                "installType" to "new",
                "flow" to "default",
                "pixelSource" to "phone",
                "daysSinceInstall" to "0",
                "event" to "clicked",
                "value" to "suggested",
            ),
            type = Unique(tag = "onboarding_search_clicked_suggested"),
        )
    }

    @Test
    fun whenFireSuggestionClickedNotFromSuggestionThenValueCustom() = runTest {
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis())

        testee.fire(ONBOARDING_VISIT_SITE, OnboardingPixelAction.SuggestionClicked(fromSuggestion = false))

        verify(mockPixel).fire(
            ONBOARDING_VISIT_SITE,
            mapOf(
                "installType" to "new",
                "flow" to "default",
                "pixelSource" to "phone",
                "daysSinceInstall" to "0",
                "event" to "clicked",
                "value" to "custom",
            ),
            type = Unique(tag = "onboarding_visit-site_clicked_custom"),
        )
    }

    @Test
    fun whenFireContextualShownThenTagHasNoValueSuffix() = runTest {
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis())

        testee.fireContextual(ONBOARDING_SEARCH, OnboardingPixelAction.Shown)

        verify(mockPixel).fire(
            ONBOARDING_SEARCH,
            mapOf("installType" to "new", "flow" to "default", "pixelSource" to "phone", "daysSinceInstall" to "0", "event" to "shown"),
            type = Unique(tag = "onboarding_search_shown"),
        )
    }

    @Test
    fun whenFireContextualClickedEngagedThenValueSetButTagOmitsIt() = runTest {
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis())

        testee.fireContextual(ONBOARDING_SEARCH, OnboardingPixelAction.Clicked(engaged = true))

        verify(mockPixel).fire(
            ONBOARDING_SEARCH,
            mapOf(
                "installType" to "new",
                "flow" to "default",
                "pixelSource" to "phone",
                "daysSinceInstall" to "0",
                "event" to "clicked",
                "value" to "engage",
            ),
            type = Unique(tag = "onboarding_search_clicked"),
        )
    }

    @Test
    fun whenFireContextualClickedEngagedAndDismissedThenBothShareTheSameDedupTag() = runTest {
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis())

        testee.fireContextual(ONBOARDING_SEARCH, OnboardingPixelAction.Clicked(engaged = true))
        testee.fireContextual(ONBOARDING_SEARCH, OnboardingPixelAction.Clicked(engaged = false))

        verify(mockPixel).fire(
            ONBOARDING_SEARCH,
            mapOf(
                "installType" to "new",
                "flow" to "default",
                "pixelSource" to "phone",
                "daysSinceInstall" to "0",
                "event" to "clicked",
                "value" to "engage",
            ),
            type = Unique(tag = "onboarding_search_clicked"),
        )
        verify(mockPixel).fire(
            ONBOARDING_SEARCH,
            mapOf(
                "installType" to "new",
                "flow" to "default",
                "pixelSource" to "phone",
                "daysSinceInstall" to "0",
                "event" to "clicked",
                "value" to "dismiss",
            ),
            type = Unique(tag = "onboarding_search_clicked"),
        )
    }

    @Test
    fun whenFireContextualSuggestionClickedThenTagOmitsSuggestedOrCustom() = runTest {
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis())

        testee.fireContextual(ONBOARDING_VISIT_SITE, OnboardingPixelAction.SuggestionClicked(fromSuggestion = true))

        verify(mockPixel).fire(
            ONBOARDING_VISIT_SITE,
            mapOf(
                "installType" to "new",
                "flow" to "default",
                "pixelSource" to "phone",
                "daysSinceInstall" to "0",
                "event" to "clicked",
                "value" to "suggested",
            ),
            type = Unique(tag = "onboarding_visit-site_clicked"),
        )
    }

    @Test(expected = IllegalStateException::class)
    fun whenFireContextualCalledWithUnsupportedActionThenThrows() = runTest {
        testee.fireContextual(ONBOARDING_SEARCH, OnboardingPixelAction.SetDefaultConfirmed(isDdgDefault = true))
    }

    @Test
    fun whenInstalledTodayThenDaysSinceInstallBucketIsZero() = runTest {
        fireWelcomeShownInstalledDaysAgo(0L)

        verifyWelcomeShownWithDaysSinceInstall("0")
    }

    @Test
    fun whenInstalledOneToThreeDaysAgoThenDaysSinceInstallBucketIsOneToThree() = runTest {
        fireWelcomeShownInstalledDaysAgo(1L, 3L)

        verifyWelcomeShownWithDaysSinceInstall("1-3", times = 2)
    }

    @Test
    fun whenInstalledFourToTenDaysAgoThenDaysSinceInstallBucketIsFourToTen() = runTest {
        fireWelcomeShownInstalledDaysAgo(4L, 10L)

        verifyWelcomeShownWithDaysSinceInstall("4-10", times = 2)
    }

    @Test
    fun whenInstalledElevenToTwentyEightDaysAgoThenDaysSinceInstallBucketIsElevenToTwentyEight() = runTest {
        fireWelcomeShownInstalledDaysAgo(11L, 28L)

        verifyWelcomeShownWithDaysSinceInstall("11-28", times = 2)
    }

    @Test
    fun whenInstalledMoreThanTwentyEightDaysAgoThenDaysSinceInstallBucketIsOverTwentyEight() = runTest {
        fireWelcomeShownInstalledDaysAgo(29L, 400L)

        verifyWelcomeShownWithDaysSinceInstall("28+", times = 2)
    }

    private fun fireWelcomeShownInstalledDaysAgo(vararg daysAgo: Long) {
        daysAgo.forEach {
            whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(it))
            testee.fire(ONBOARDING_WELCOME, OnboardingPixelAction.Shown)
        }
    }

    private fun verifyWelcomeShownWithDaysSinceInstall(bucket: String, times: Int = 1) {
        verify(mockPixel, times(times)).fire(
            ONBOARDING_WELCOME,
            mapOf("installType" to "new", "flow" to "default", "pixelSource" to "phone", "daysSinceInstall" to bucket, "event" to "shown"),
            type = Unique(tag = "onboarding_welcome_shown"),
        )
    }

    @Test
    fun whenFirePasswordImportConfirmedSuccessThenValueSuccess() = runTest {
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis())

        testee.fire(ONBOARDING_PASSWORD_IMPORT, OnboardingPixelAction.PasswordImportConfirmed(PasswordImportOutcome.SUCCESS))

        verify(mockPixel).fire(
            ONBOARDING_PASSWORD_IMPORT,
            mapOf(
                "installType" to "new",
                "flow" to "default",
                "pixelSource" to "phone",
                "daysSinceInstall" to "0",
                "event" to "confirmed",
                "value" to "success",
            ),
            type = Unique(tag = "onboarding_password-import_confirmed_success"),
        )
    }

    @Test
    fun whenFirePasswordImportConfirmedForEitherErrorThenBothReportTheSameValue() = runTest {
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis())

        testee.fire(ONBOARDING_PASSWORD_IMPORT, OnboardingPixelAction.PasswordImportConfirmed(PasswordImportOutcome.TRANSIENT_ERROR))
        testee.fire(ONBOARDING_PASSWORD_IMPORT, OnboardingPixelAction.PasswordImportConfirmed(PasswordImportOutcome.PERMANENT_ERROR))

        verify(mockPixel, times(2)).fire(
            ONBOARDING_PASSWORD_IMPORT,
            mapOf(
                "installType" to "new",
                "flow" to "default",
                "pixelSource" to "phone",
                "daysSinceInstall" to "0",
                "event" to "confirmed",
                "value" to "error",
            ),
            type = Unique(tag = "onboarding_password-import_confirmed_error"),
        )
    }

    @Test
    fun whenSegmentedFlowStartedThenFlowParamIsTailoredByDownloadReason() = runTest {
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis())

        testee.segmentedFlowStarted()
        testee.fire(ONBOARDING_DOWNLOAD_CHOICE, OnboardingPixelAction.Shown)

        verify(mockPixel).fire(
            ONBOARDING_DOWNLOAD_CHOICE,
            mapOf(
                "installType" to "new",
                "flow" to "tailored_by_download_reason",
                "pixelSource" to "phone",
                "daysSinceInstall" to "0",
                "event" to "shown",
            ),
            type = Unique(tag = "onboarding_download-choice_shown"),
        )
    }

    @Test
    fun whenSegmentedFlowStartedThenItWinsOverTheCustomAiFlow() = runTest {
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis())
        whenever(mockCustomAiOnboardingStore.isEnabled()).thenReturn(true)

        testee.segmentedFlowStarted()
        testee.fire(ONBOARDING_WELCOME, OnboardingPixelAction.Shown)

        verify(mockPixel).fire(
            ONBOARDING_WELCOME,
            mapOf(
                "installType" to "new",
                "flow" to "tailored_by_download_reason",
                "pixelSource" to "phone",
                "daysSinceInstall" to "0",
                "event" to "shown",
            ),
            type = Unique(tag = "onboarding_welcome_shown"),
        )
    }

    @Test
    fun whenDownloadReasonSelectedThenSegmentedVariantParamIsThatReason() = runTest {
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis())

        testee.downloadReasonSelected(DownloadReasonSelection.AI_CHAT)
        testee.fire(ONBOARDING_SET_DEFAULT, OnboardingPixelAction.Shown)

        verify(mockPixel).fire(
            ONBOARDING_SET_DEFAULT,
            mapOf(
                "installType" to "new",
                "flow" to "default",
                "variant_segmented" to "download_reason_ai-chat",
                "pixelSource" to "phone",
                "daysSinceInstall" to "0",
                "event" to "shown",
            ),
            type = Unique(tag = "onboarding_set-default_shown"),
        )
    }

    @Test
    fun whenBothDownloadReasonAndBranchSelectedThenEachGetsItsOwnParam() = runTest {
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis())

        testee.downloadReasonSelected(DownloadReasonSelection.BLOCK_ADS)
        testee.chatBranchSelected()
        testee.fire(ONBOARDING_END, OnboardingPixelAction.Shown)

        verify(mockPixel).fire(
            ONBOARDING_END,
            mapOf(
                "installType" to "new",
                "flow" to "default",
                "variant" to "search_plus_duckai-chat",
                "variant_segmented" to "download_reason_ad-blocking",
                "pixelSource" to "phone",
                "daysSinceInstall" to "0",
                "event" to "shown",
            ),
            type = Unique(tag = "onboarding_end_shown"),
        )
    }

    @Test
    fun whenOnlyTheBranchIsSelectedThenSegmentedVariantIsAbsent() = runTest {
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis())

        testee.searchBranchSelected()
        testee.fire(ONBOARDING_END, OnboardingPixelAction.Shown)

        verify(mockPixel).fire(
            ONBOARDING_END,
            mapOf(
                "installType" to "new",
                "flow" to "default",
                "variant" to "search_plus_duckai-search",
                "pixelSource" to "phone",
                "daysSinceInstall" to "0",
                "event" to "shown",
            ),
            type = Unique(tag = "onboarding_end_shown"),
        )
    }

    @Test
    fun whenFlowAttributionClearedThenSegmentedFlowAndReasonAreBothDropped() = runTest {
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis())

        testee.segmentedFlowStarted()
        testee.downloadReasonSelected(DownloadReasonSelection.SEARCH)
        testee.clearFlowAttribution()
        testee.fire(ONBOARDING_WELCOME, OnboardingPixelAction.Shown)

        verify(mockPixel).fire(
            ONBOARDING_WELCOME,
            mapOf("installType" to "new", "flow" to "default", "pixelSource" to "phone", "daysSinceInstall" to "0", "event" to "shown"),
            type = Unique(tag = "onboarding_welcome_shown"),
        )
    }

    @Test
    fun whenFireDownloadReasonClickedThenValueIsTheReasonToken() = runTest {
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis())

        testee.fire(ONBOARDING_DOWNLOAD_CHOICE, OnboardingPixelAction.DownloadReasonClicked(DownloadReasonSelection.NO_AI))

        verify(mockPixel).fire(
            ONBOARDING_DOWNLOAD_CHOICE,
            mapOf(
                "installType" to "new",
                "flow" to "default",
                "pixelSource" to "phone",
                "daysSinceInstall" to "0",
                "event" to "clicked",
                "value" to "no-ai",
            ),
            type = Unique(tag = "onboarding_download-choice_clicked_no-ai"),
        )
    }

    /** The reason is persisted as the choice is confirmed, so the step's own clicked pixel already carries it. */
    @Test
    fun whenFireDownloadReasonClickedAfterTheReasonIsPersistedThenTheSegmentedVariantIsAlsoSent() = runTest {
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis())

        testee.downloadReasonSelected(DownloadReasonSelection.NO_AI)
        testee.fire(ONBOARDING_DOWNLOAD_CHOICE, OnboardingPixelAction.DownloadReasonClicked(DownloadReasonSelection.NO_AI))

        verify(mockPixel).fire(
            ONBOARDING_DOWNLOAD_CHOICE,
            mapOf(
                "installType" to "new",
                "flow" to "default",
                "variant_segmented" to "download_reason_no-ai",
                "pixelSource" to "phone",
                "daysSinceInstall" to "0",
                "event" to "clicked",
                "value" to "no-ai",
            ),
            type = Unique(tag = "onboarding_download-choice_clicked_no-ai"),
        )
    }

    @Test
    fun whenFireSerpPreferencesClickedThenEachToggleIsItsOwnParam() = runTest {
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis())

        testee.fire(
            ONBOARDING_PREFERENCES_SERP,
            OnboardingPixelAction.PreferencesClicked(
                mapOf(
                    OnboardingPreference.SEARCH_HISTORY to true,
                    OnboardingPreference.SAFE_SEARCH to false,
                ),
            ),
        )

        verify(mockPixel).fire(
            ONBOARDING_PREFERENCES_SERP,
            mapOf(
                "installType" to "new",
                "flow" to "default",
                "pixelSource" to "phone",
                "daysSinceInstall" to "0",
                "event" to "clicked",
                "recently_visited_sites_enabled" to "true",
                "safe_search_enabled" to "false",
            ),
            type = Unique(tag = "onboarding_preferences_serp_clicked"),
        )
    }

    @Test
    fun whenFireAiSearchPreferencesClickedThenHideAiImagesIsInvertedIntoImagesEnabled() = runTest {
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis())

        testee.fire(
            ONBOARDING_PREFERENCES_AI_SEARCH,
            OnboardingPixelAction.PreferencesClicked(
                mapOf(
                    OnboardingPreference.SEARCH_ASSIST to true,
                    OnboardingPreference.HIDE_AI_GENERATED_IMAGES to true,
                ),
            ),
        )

        verify(mockPixel).fire(
            ONBOARDING_PREFERENCES_AI_SEARCH,
            mapOf(
                "installType" to "new",
                "flow" to "default",
                "pixelSource" to "phone",
                "daysSinceInstall" to "0",
                "event" to "clicked",
                "search_assist_enabled" to "true",
                "ai_generated_images_enabled" to "false",
            ),
            type = Unique(tag = "onboarding_preferences_ai-search_clicked"),
        )
    }

    @Test
    fun whenFireAdBlockingPreferencesClickedThenAllThreeTogglesAreSent() = runTest {
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis())

        testee.fire(
            ONBOARDING_PREFERENCES_AD_BLOCKING,
            OnboardingPixelAction.PreferencesClicked(
                mapOf(
                    OnboardingPreference.BLOCK_ADS to true,
                    OnboardingPreference.REJECT_OPTIONAL_COOKIES to true,
                    OnboardingPreference.ACCEPT_NON_OPT_OUT_COOKIES to false,
                ),
            ),
        )

        verify(mockPixel).fire(
            ONBOARDING_PREFERENCES_AD_BLOCKING,
            mapOf(
                "installType" to "new",
                "flow" to "default",
                "pixelSource" to "phone",
                "daysSinceInstall" to "0",
                "event" to "clicked",
                "youtube_ad_blocking_enabled" to "true",
                "cookie_popup_protection_enabled" to "true",
                "popups_without_optouts_enabled" to "false",
            ),
            type = Unique(tag = "onboarding_preferences_ad-blocking_clicked"),
        )
    }

    @Test
    fun whenFireSingleChoiceClickedThenValueIsTheOptionIdVerbatim() = runTest {
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis())

        testee.fire(ONBOARDING_PREFERENCES_AI_MODEL, OnboardingPixelAction.SingleChoiceClicked("anthropic"))

        verify(mockPixel).fire(
            ONBOARDING_PREFERENCES_AI_MODEL,
            mapOf(
                "installType" to "new",
                "flow" to "default",
                "pixelSource" to "phone",
                "daysSinceInstall" to "0",
                "event" to "clicked",
                "value" to "anthropic",
            ),
            type = Unique(tag = "onboarding_preferences_ai-model_clicked_anthropic"),
        )
    }
}
