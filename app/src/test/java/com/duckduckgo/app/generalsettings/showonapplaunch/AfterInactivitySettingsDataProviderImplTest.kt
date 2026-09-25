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

package com.duckduckgo.app.generalsettings.showonapplaunch

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.test
import com.duckduckgo.app.FakeSettingsDataStore
import com.duckduckgo.app.generalsettings.showonapplaunch.model.ShowOnAppLaunchOption
import com.duckduckgo.app.generalsettings.showonapplaunch.model.ShowOnAppLaunchOption.LastOpenedTab
import com.duckduckgo.app.generalsettings.showonapplaunch.model.ShowOnAppLaunchOption.NewTabPage
import com.duckduckgo.app.generalsettings.showonapplaunch.model.ShowOnAppLaunchOption.SpecificPage
import com.duckduckgo.app.generalsettings.showonapplaunch.store.ShowOnAppLaunchOptionDataStore
import com.duckduckgo.app.generalsettings.showonapplaunch.store.ShowOnAppLaunchOptionPrefsDataStore
import com.duckduckgo.app.pixels.AppPixelName.SETTINGS_AFTER_INACTIVITY_TIMEOUT_CHANGED
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Count
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Daily
import com.duckduckgo.browser.feature.toggles.AndroidBrowserConfigFeature
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.FakeToggleStore
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.internal.CachedToggleStore
import com.duckduckgo.newtabpage.api.NtpAfterIdleManager
import com.duckduckgo.settings.api.AfterInactivityReturnDestination
import com.duckduckgo.settings.api.AfterInactivitySettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AfterInactivitySettingsDataProviderImplTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val optionFlow = MutableStateFlow<ShowOnAppLaunchOption>(NewTabPage)
    private val shortcutEnabled = MutableStateFlow(true)
    private val optionDataStore: ShowOnAppLaunchOptionDataStore = mock {
        on { optionFlow }.thenReturn(optionFlow)
    }
    private val settingsDataStore = FakeSettingsDataStore()
    private val ntpAfterIdleManager: NtpAfterIdleManager = mock {
        on { returnToLastTabEnabled }.thenReturn(shortcutEnabled)
    }
    private val pixel: Pixel = mock()
    private val urlConverter: UrlConverter = mock {
        on { convertUrl(any()) }.thenAnswer { invocation -> "https://${invocation.arguments[0]}/" }
    }
    private val toggleStore = CachedToggleStore(FakeToggleStore())
    private val browserConfigFeature = FakeFeatureToggleFactory.create<AndroidBrowserConfigFeature>(
        AndroidBrowserConfigFeature::class.java,
        store = toggleStore,
        ioDispatcher = coroutineTestRule.testDispatcher,
    )
    private val testee = AfterInactivitySettingsDataProviderImpl(
        optionDataStore,
        settingsDataStore,
        browserConfigFeature,
        RealIdleThresholdResolver(browserConfigFeature),
        urlConverter,
        ntpAfterIdleManager,
        pixel,
    )

    @Test
    fun whenNoTimeoutWasSelectedThenSettingsFollowRemoteDefaultUpdates() = coroutineTestRule.testScope.runTest {
        setRemoteDefault(300L)

        testee.settings.test {
            assertEquals(
                AfterInactivitySettings.NewTabPage(
                    effectiveTimeoutSeconds = 300L,
                    returnToLastTabShortcutEnabled = true,
                ),
                awaitItem(),
            )
            assertNull(settingsDataStore.userSelectedIdleThresholdSeconds)

            setRemoteDefault(600L)
            coroutineTestRule.testScope.testScheduler.runCurrent()

            assertEquals(
                AfterInactivitySettings.NewTabPage(
                    effectiveTimeoutSeconds = 600L,
                    returnToLastTabShortcutEnabled = true,
                ),
                awaitItem(),
            )

            settingsDataStore.userSelectedIdleThresholdSeconds = 60L
            assertEquals(
                AfterInactivitySettings.NewTabPage(
                    effectiveTimeoutSeconds = 60L,
                    returnToLastTabShortcutEnabled = true,
                ),
                awaitItem(),
            )

            setRemoteDefault(1_800L)
            coroutineTestRule.testScope.testScheduler.runCurrent()
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenCanonicalOptionChangesThenSettingsExposeOnlyThatOptionsFields() = coroutineTestRule.testScope.runTest {
        testee.settings.test {
            assertEquals(
                AfterInactivitySettings.NewTabPage(
                    effectiveTimeoutSeconds = 300L,
                    returnToLastTabShortcutEnabled = true,
                ),
                awaitItem(),
            )

            optionFlow.value = LastOpenedTab
            assertEquals(AfterInactivitySettings.LastUsedTab, awaitItem())

            optionFlow.value = SpecificPage("https://example.com/", null)
            assertEquals(
                AfterInactivitySettings.SpecificPage("https://example.com/", 300L),
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenNewTabPageCommandContainsTimeoutThenItPersistsOnlyThatFieldWithoutOptionPixels() = coroutineTestRule.testScope.runTest {
        whenever(optionDataStore.hasOptionSelected()).thenReturn(true)

        testee.setDestination(AfterInactivityReturnDestination.NewTabPage(selectedTimeoutSeconds = 60L))

        verify(optionDataStore).setShowOnAppLaunchOption(NewTabPage)
        assertEquals(60L, settingsDataStore.userSelectedIdleThresholdSeconds)
        verify(ntpAfterIdleManager).onIdleTimeoutSelected(60L)
        verify(pixel).fire(
            SETTINGS_AFTER_INACTIVITY_TIMEOUT_CHANGED,
            mapOf("selectedSeconds" to "60"),
        )
        verify(pixel, never()).fire(ShowOnAppLaunchPixelName.LAUNCH_OPTION_NEW_TAB_PAGE, type = Count)
        verify(pixel, never()).fire(ShowOnAppLaunchPixelName.LAUNCH_OPTION_NEW_TAB_PAGE_DAILY, type = Daily())
    }

    @Test
    fun whenNewTabPageCommandContainsShortcutThenItPersistsOnlyThatFieldAndItsPixels() = coroutineTestRule.testScope.runTest {
        whenever(optionDataStore.hasOptionSelected()).thenReturn(true)

        testee.setDestination(AfterInactivityReturnDestination.NewTabPage(returnToLastTabShortcutEnabled = false))

        verify(ntpAfterIdleManager).setReturnToLastTabEnabled(false)
        verify(ntpAfterIdleManager, never()).onIdleTimeoutSelected(any())
        assertNull(settingsDataStore.userSelectedIdleThresholdSeconds)
        verify(pixel).fire(ShowOnAppLaunchPixelName.LAST_TAB_SHORTCUT_SETTING_DISABLED, type = Count)
        verify(pixel).fire(ShowOnAppLaunchPixelName.LAST_TAB_SHORTCUT_SETTING_DISABLED_DAILY, type = Daily())
        verify(pixel, never()).fire(ShowOnAppLaunchPixelName.LAUNCH_OPTION_NEW_TAB_PAGE, type = Count)
    }

    @Test
    fun whenSpecificPageCommandContainsTimeoutThenItPersistsTimeoutAndNotifiesIdleManager() = coroutineTestRule.testScope.runTest {
        whenever(optionDataStore.hasOptionSelected()).thenReturn(true)

        testee.setDestination(AfterInactivityReturnDestination.SpecificPage("example.com", selectedTimeoutSeconds = 600L))

        verify(optionDataStore).setShowOnAppLaunchOption(SpecificPage("https://example.com/"))
        assertEquals(600L, settingsDataStore.userSelectedIdleThresholdSeconds)
        verify(ntpAfterIdleManager).onIdleTimeoutSelected(600L)
        verify(pixel).fire(
            SETTINGS_AFTER_INACTIVITY_TIMEOUT_CHANGED,
            mapOf("selectedSeconds" to "600"),
        )
    }

    @Test
    fun whenSpecificPageUrlChangesWithoutChangingDiscriminatorThenItDoesNotFireOptionPixels() = coroutineTestRule.testScope.runTest {
        optionFlow.value = SpecificPage("https://old.example/", null)
        whenever(optionDataStore.hasOptionSelected()).thenReturn(true)

        testee.setDestination(AfterInactivityReturnDestination.SpecificPage("new.example"))

        verify(optionDataStore).setShowOnAppLaunchOption(SpecificPage("https://new.example/"))
        verify(pixel, never()).fire(ShowOnAppLaunchPixelName.LAUNCH_OPTION_SPECIFIC_PAGE, type = Count)
        verify(pixel, never()).fire(ShowOnAppLaunchPixelName.LAUNCH_OPTION_SPECIFIC_PAGE_DAILY, type = Daily())
    }

    @Test
    fun whenSpecificPageUrlIsUnchangedThenItDoesNotFireOptionPixels() = coroutineTestRule.testScope.runTest {
        optionFlow.value = SpecificPage("https://example.com/", null)
        whenever(optionDataStore.hasOptionSelected()).thenReturn(true)

        testee.setDestination(AfterInactivityReturnDestination.SpecificPage("example.com"))

        verify(optionDataStore).setShowOnAppLaunchOption(SpecificPage("https://example.com/"))
        verify(pixel, never()).fire(ShowOnAppLaunchPixelName.LAUNCH_OPTION_SPECIFIC_PAGE, type = Count)
        verify(pixel, never()).fire(ShowOnAppLaunchPixelName.LAUNCH_OPTION_SPECIFIC_PAGE_DAILY, type = Daily())
    }

    @Test
    fun whenExplicitDestinationDiscriminatorChangesThenItFiresOnlyTheDestinationPixelPair() = coroutineTestRule.testScope.runTest {
        whenever(optionDataStore.hasOptionSelected()).thenReturn(true)

        testee.setDestination(AfterInactivityReturnDestination.LastUsedTab)

        verify(optionDataStore).setShowOnAppLaunchOption(LastOpenedTab)
        verify(pixel).fire(ShowOnAppLaunchPixelName.LAUNCH_OPTION_LAST_OPENED_TAB, type = Count)
        verify(pixel).fire(ShowOnAppLaunchPixelName.LAUNCH_OPTION_LAST_OPENED_TAB_DAILY, type = Daily())
    }

    @Test
    fun whenImplicitNewTabPageIsPersistedThenItDoesNotFireDestinationPixels() = coroutineTestRule.testScope.runTest {
        whenever(optionDataStore.hasOptionSelected()).thenReturn(false)

        testee.setDestination(AfterInactivityReturnDestination.NewTabPage())

        verify(optionDataStore).setShowOnAppLaunchOption(NewTabPage)
        verify(pixel, never()).fire(ShowOnAppLaunchPixelName.LAUNCH_OPTION_NEW_TAB_PAGE, type = Count)
        verify(pixel, never()).fire(ShowOnAppLaunchPixelName.LAUNCH_OPTION_NEW_TAB_PAGE_DAILY, type = Daily())
    }

    private fun setRemoteDefault(seconds: Long) {
        browserConfigFeature.showNTPAfterIdleReturn().setRawStoredState(
            Toggle.State(enable = true, settings = """{"defaultIdleThresholdSeconds":$seconds}"""),
        )
    }

    @Test
    fun whenAvailabilityChangesThenImplicitDestinationMatchesFreshCanonicalReads() = coroutineTestRule.testScope.runTest {
        val store = realOptionStore()
        val provider = realStoreProvider(store)
        browserConfigFeature.ntpAsDefaultAfterIdleReturn().setRawStoredState(Toggle.State(enable = true))
        browserConfigFeature.showNTPAfterIdleReturn().setRawStoredState(Toggle.State(enable = false))

        provider.settings.test {
            assertEquals(AfterInactivitySettings.LastUsedTab, awaitItem())
            setRemoteDefault(600L)
            coroutineTestRule.testScope.testScheduler.runCurrent()
            assertEquals(NewTabPage, store.optionFlow.first())
            assertEquals(AfterInactivitySettings.NewTabPage(600L, true), expectMostRecentItem())

            browserConfigFeature.showNTPAfterIdleReturn().setRawStoredState(Toggle.State(enable = false))
            coroutineTestRule.testScope.testScheduler.runCurrent()
            assertEquals(LastOpenedTab, store.optionFlow.first())
            assertEquals(AfterInactivitySettings.LastUsedTab, expectMostRecentItem())
            assertEquals(false, store.hasOptionSelected())
        }
    }

    private fun realOptionStore(): ShowOnAppLaunchOptionPrefsDataStore {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = coroutineTestRule.testScope.backgroundScope,
            produceFile = { temporaryFolder.newFile("settings.preferences_pb") },
        )
        return ShowOnAppLaunchOptionPrefsDataStore(dataStore, browserConfigFeature)
    }

    private fun realStoreProvider(store: ShowOnAppLaunchOptionDataStore) = AfterInactivitySettingsDataProviderImpl(
        store,
        settingsDataStore,
        browserConfigFeature,
        RealIdleThresholdResolver(browserConfigFeature),
        object : UrlConverter {
            override fun convertUrl(url: String?) = requireNotNull(url)
        },
        ntpAfterIdleManager,
        pixel,
    )
}
