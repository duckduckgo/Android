/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.app.permissionsandprivacy

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.permissionsandprivacy.PermissionsAndPrivacyViewModel.Command
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.settings.clear.AppLinkSettingType
import com.duckduckgo.app.settings.clear.ClearWhatOption
import com.duckduckgo.app.settings.clear.ClearWhenOption
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autoconsent.api.Autoconsent
import com.duckduckgo.feature.toggles.api.FeatureToggle
import com.duckduckgo.privacy.config.api.Gpc
import com.duckduckgo.privacy.config.api.PrivacyFeatureName
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class PermissionsAndPrivacyViewModelTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var testee: PermissionsAndPrivacyViewModel

    @Mock
    private lateinit var mockAppSettingsDataStore: SettingsDataStore

    @Mock
    private lateinit var mockPixel: Pixel

    @Mock
    private lateinit var mockGpc: Gpc

    @Mock
    private lateinit var mockFeatureToggle: FeatureToggle

    @Mock
    private lateinit var mockAutoconsent: Autoconsent

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    @Before
    fun before() {
        MockitoAnnotations.openMocks(this)

        whenever(mockAppSettingsDataStore.automaticallyClearWhenOption).thenReturn(ClearWhenOption.APP_EXIT_ONLY)
        whenever(mockAppSettingsDataStore.automaticallyClearWhatOption).thenReturn(ClearWhatOption.CLEAR_NONE)

        testee = PermissionsAndPrivacyViewModel(
            mockAppSettingsDataStore,
            mockPixel,
            mockGpc,
            mockFeatureToggle,
            mockAutoconsent,
        )
    }

    @Test
    fun whenStartIfGpcToggleDisabledAndGpcEnabledThenGpgDisabled() = runTest {
        whenever(mockFeatureToggle.isFeatureEnabled(eq(PrivacyFeatureName.GpcFeatureName.value), any())).thenReturn(false)
        whenever(mockGpc.isEnabled()).thenReturn(true)

        testee.start()

        testee.viewState().test {
            assertFalse(awaitItem().globalPrivacyControlEnabled)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenStartIfGpcToggleEnabledAndGpcDisabledThenGpgDisabled() = runTest {
        whenever(mockFeatureToggle.isFeatureEnabled(eq(PrivacyFeatureName.GpcFeatureName.value), any())).thenReturn(true)
        whenever(mockGpc.isEnabled()).thenReturn(false)

        testee.start()

        testee.viewState().test {
            assertFalse(awaitItem().globalPrivacyControlEnabled)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenStartIfGpcToggleEnabledAndGpcEnabledThenGpgEnabled() = runTest {
        whenever(mockFeatureToggle.isFeatureEnabled(eq(PrivacyFeatureName.GpcFeatureName.value), any())).thenReturn(true)
        whenever(mockGpc.isEnabled()).thenReturn(true)

        testee.start()

        testee.viewState().test {
            assertTrue(awaitItem().globalPrivacyControlEnabled)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenStartNotCalledYetThenViewStateInitialisedDefaultValues() = runTest {
        testee.viewState().test {
            val value = awaitItem()
            assertTrue(value.autoCompleteSuggestionsEnabled)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenStartCalledWithNotificationsEnabledThenNotificationsSettingSubtitleSetCorrectlyAsEnabled() = runTest {
        testee.start(notificationsEnabled = true)

        testee.viewState().test {
            val value = expectMostRecentItem()

            assertEquals(R.string.settingsSubtitleNotificationsEnabled, value.notificationsSettingSubtitleId)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenStartCalledWithNotificationsDisabledThenNotificationsSettingSubtitleSetCorrectlyAsDisabled() = runTest {
        testee.start(notificationsEnabled = false)

        testee.viewState().test {
            val value = expectMostRecentItem()

            assertEquals(R.string.settingsSubtitleNotificationsDisabled, value.notificationsSettingSubtitleId)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenAutocompleteSwitchedOnThenDataStoreIsUpdated() {
        testee.onAutocompleteSettingChanged(true)

        verify(mockAppSettingsDataStore).autoCompleteSuggestionsEnabled = true
    }

    @Test
    fun whenAutocompleteSwitchedOffThenDataStoreIsUpdated() {
        testee.onAutocompleteSettingChanged(false)

        verify(mockAppSettingsDataStore).autoCompleteSuggestionsEnabled = false
    }

    @Test
    fun whenAppLinksSetToAskEverytimeThenDataStoreIsUpdatedAndPixelIsSent() {
        testee.onAppLinksSettingChanged(AppLinkSettingType.ASK_EVERYTIME)

        verify(mockAppSettingsDataStore).appLinksEnabled = true
        verify(mockAppSettingsDataStore).showAppLinksPrompt = true
        verify(mockPixel).fire(AppPixelName.SETTINGS_APP_LINKS_ASK_EVERY_TIME_SELECTED)
    }

    @Test
    fun whenAppLinksSetToAlwaysThenDataStoreIsUpdatedAndPixelIsSent() {
        testee.onAppLinksSettingChanged(AppLinkSettingType.ALWAYS)

        verify(mockAppSettingsDataStore).appLinksEnabled = true
        verify(mockAppSettingsDataStore).showAppLinksPrompt = false
        verify(mockPixel).fire(AppPixelName.SETTINGS_APP_LINKS_ALWAYS_SELECTED)
    }

    @Test
    fun whenAppLinksSetToNeverThenDataStoreIsUpdatedAndPixelIsSent() {
        testee.onAppLinksSettingChanged(AppLinkSettingType.NEVER)

        verify(mockAppSettingsDataStore).appLinksEnabled = false
        verify(mockAppSettingsDataStore).showAppLinksPrompt = false
        verify(mockPixel).fire(AppPixelName.SETTINGS_APP_LINKS_NEVER_SELECTED)
    }

    @Test
    fun whenOnGlobalPrivacyControlClickedThenCommandIsLaunchGlobalPrivacyControl() = runTest {
        testee.commands().test {
            testee.onGlobalPrivacyControlClicked()

            assertEquals(Command.LaunchGlobalPrivacyControl, awaitItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenOnAutomaticallyClearWhatClickedEmitCommandShowClearWhatDialog() = runTest {
        testee.commands().test {
            testee.onAutomaticallyClearWhatClicked()

            assertEquals(Command.ShowClearWhatDialog(ClearWhatOption.CLEAR_NONE), awaitItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenOnAutomaticallyClearWhenClickedEmitCommandShowClearWhenDialog() = runTest {
        testee.commands().test {
            testee.onAutomaticallyClearWhenClicked()

            assertEquals(Command.ShowClearWhenDialog(ClearWhenOption.APP_EXIT_ONLY), awaitItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenOnFireproofWebsitesClickedThenEmitCommandLaunchFireproofWebsites() = runTest {
        testee.commands().test {
            testee.onFireproofWebsitesClicked()

            assertEquals(Command.LaunchFireproofWebsites, awaitItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenOnAutoconsentClickedThenEmitCommandLaunchAutoconsent() = runTest {
        testee.commands().test {
            testee.onAutoconsentClicked()

            assertEquals(Command.LaunchAutoconsent, awaitItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenAutoconsentEnabledThenAutoconsentEnabledIsTrue() = runTest {
        whenever(mockAutoconsent.isSettingEnabled()).thenReturn(true)

        testee.start()

        testee.viewState().test {
            assertTrue(awaitItem().autoconsentEnabled)
        }
    }

    @Test
    fun whenAutoconsentDisabledThenAutoconsentEnabledIsFalse() = runTest {
        whenever(mockAutoconsent.isSettingEnabled()).thenReturn(false)

        testee.start()

        testee.viewState().test {
            assertFalse(awaitItem().autoconsentEnabled)
        }
    }

    @Test
    fun whenOnManageWhitelistSelectedThenEmitCommandLaunchWhitelistAndSendPixel() = runTest {
        testee.commands().test {
            testee.onManageWhitelistSelected()

            assertEquals(Command.LaunchWhitelist, awaitItem())
            verify(mockPixel).fire(AppPixelName.SETTINGS_MANAGE_WHITELIST)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenOnSitePermissionsClickedThenEmitCommandLaunchLocation() = runTest {
        testee.commands().test {
            testee.onSitePermissionsClicked()

            assertEquals(Command.LaunchLocation, awaitItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenUserRequestedToChangeNotificationsSettingThenEmitCommandLaunchNotificationsSettingsAndSendPixel() = runTest {
        testee.commands().test {
            testee.userRequestedToChangeNotificationsSetting()

            assertEquals(Command.LaunchNotificationsSettings, awaitItem())
            verify(mockPixel).fire(AppPixelName.SETTINGS_NOTIFICATIONS_PRESSED)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenUserRequestedToChangeAppLinkSettingThenEmitCommandLaunchAppLinkSettings() = runTest {
        testee.commands().test {
            testee.userRequestedToChangeAppLinkSetting()

            assertEquals(Command.LaunchAppLinkSettings(AppLinkSettingType.ASK_EVERYTIME), awaitItem())
            verify(mockPixel).fire(AppPixelName.SETTINGS_APP_LINKS_PRESSED)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenOnAutomaticallyWhatOptionSelectedWithNewOptionThenDataStoreIsUpdatedAndPixelSent() = runTest {
        whenever(mockAppSettingsDataStore.isCurrentlySelected(ClearWhatOption.CLEAR_TABS_AND_DATA)).thenReturn(false)

        testee.commands().test {
            testee.onAutomaticallyWhatOptionSelected(ClearWhatOption.CLEAR_TABS_AND_DATA)

            verify(mockAppSettingsDataStore).automaticallyClearWhatOption = ClearWhatOption.CLEAR_TABS_AND_DATA
            verify(mockPixel).fire(AppPixelName.AUTOMATIC_CLEAR_DATA_WHAT_OPTION_TABS_AND_DATA)
        }
    }

    @Test
    fun whenOnAutomaticallyWhatOptionSelectedWithSameOptionThenDataStoreIsNotUpdatedAndPixelNotSent() = runTest {
        whenever(mockAppSettingsDataStore.isCurrentlySelected(ClearWhatOption.CLEAR_NONE)).thenReturn(true)

        testee.commands().test {
            testee.onAutomaticallyWhatOptionSelected(ClearWhatOption.CLEAR_NONE)

            verify(mockAppSettingsDataStore, never()).automaticallyClearWhatOption
            verify(mockPixel, never()).fire(AppPixelName.AUTOMATIC_CLEAR_DATA_WHAT_OPTION_NONE)
        }
    }

    @Test
    fun whenOnAutomaticallyWhenOptionSelectedWithNewOptionThenDataStoreIsUpdatedAndPixelSent() = runTest {
        whenever(mockAppSettingsDataStore.isCurrentlySelected(ClearWhenOption.APP_EXIT_ONLY)).thenReturn(false)

        testee.commands().test {
            testee.onAutomaticallyWhenOptionSelected(ClearWhenOption.APP_EXIT_ONLY)

            verify(mockAppSettingsDataStore).automaticallyClearWhenOption = ClearWhenOption.APP_EXIT_ONLY
            verify(mockPixel).fire(AppPixelName.AUTOMATIC_CLEAR_DATA_WHEN_OPTION_APP_EXIT_ONLY)
        }
    }

    @Test
    fun whenOnAutomaticallyWhenOptionSelectedWithSameOptionThenDataStoreIsNotUpdatedAndPixelNotSent() = runTest {
        whenever(mockAppSettingsDataStore.isCurrentlySelected(ClearWhenOption.APP_EXIT_ONLY)).thenReturn(true)

        testee.commands().test {
            testee.onAutomaticallyWhenOptionSelected(ClearWhenOption.APP_EXIT_ONLY)

            verify(mockAppSettingsDataStore, never()).automaticallyClearWhenOption
            verify(mockPixel, never()).fire(AppPixelName.AUTOMATIC_CLEAR_DATA_WHAT_OPTION_NONE)
        }
    }
}
