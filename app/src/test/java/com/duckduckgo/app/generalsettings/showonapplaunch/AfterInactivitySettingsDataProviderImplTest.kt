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

import app.cash.turbine.test
import com.duckduckgo.app.FakeSettingsDataStore
import com.duckduckgo.app.generalsettings.showonapplaunch.model.ShowOnAppLaunchOption.LastOpenedTab
import com.duckduckgo.app.generalsettings.showonapplaunch.model.ShowOnAppLaunchOption.NewTabPage
import com.duckduckgo.app.generalsettings.showonapplaunch.model.ShowOnAppLaunchOption.SpecificPage
import com.duckduckgo.app.generalsettings.showonapplaunch.store.FakeShowOnAppLaunchOptionDataStore
import com.duckduckgo.browser.feature.toggles.AndroidBrowserConfigFeature
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
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
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class AfterInactivitySettingsDataProviderImplTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    private val optionDataStore = FakeShowOnAppLaunchOptionDataStore(NewTabPage)
    private val shortcutEnabled = MutableStateFlow(true)
    private val settingsDataStore = FakeSettingsDataStore()
    private val ntpAfterIdleManager: NtpAfterIdleManager = mock {
        on { returnToLastTabEnabled }.thenReturn(shortcutEnabled)
    }
    private val urlConverter = object : UrlConverter {
        override fun convertUrl(url: String?) = requireNotNull(url)
    }
    private val browserConfigFeature = FakeFeatureToggleFactory.create(AndroidBrowserConfigFeature::class.java)
    private val testee = AfterInactivitySettingsDataProviderImpl(
        optionDataStore,
        settingsDataStore,
        browserConfigFeature,
        RealIdleThresholdResolver(browserConfigFeature),
        urlConverter,
        ntpAfterIdleManager,
    )

    @Test
    fun whenNoTimeoutWasSelectedThenSettingsFollowRemoteDefaultUpdates() = runTest {
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

            testee.setDestination(AfterInactivityReturnDestination.NewTabPage(selectedTimeoutSeconds = 60L))
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
    fun whenCanonicalOptionChangesThenSettingsExposeOnlyThatOptionsFields() = runTest {
        testee.settings.test {
            assertEquals(
                AfterInactivitySettings.NewTabPage(
                    effectiveTimeoutSeconds = 300L,
                    returnToLastTabShortcutEnabled = true,
                ),
                awaitItem(),
            )

            optionDataStore.setShowOnAppLaunchOption(LastOpenedTab)
            assertEquals(AfterInactivitySettings.LastUsedTab, awaitItem())

            optionDataStore.setShowOnAppLaunchOption(SpecificPage("https://example.com/", null))
            assertEquals(
                AfterInactivitySettings.SpecificPage("https://example.com/", 300L),
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenNewTabPageCommandContainsTimeoutThenOptionIsUnchangedButTimeoutPersists() = runTest {
        optionDataStore.setShowOnAppLaunchOption(NewTabPage)
        val callCountBeforeDestination = optionDataStore.setShowOnAppLaunchOptionCallCount

        testee.setDestination(AfterInactivityReturnDestination.NewTabPage(selectedTimeoutSeconds = 60L))

        assertEquals(callCountBeforeDestination, optionDataStore.setShowOnAppLaunchOptionCallCount)
        assertEquals(60L, settingsDataStore.userSelectedIdleThresholdSeconds)
        verify(ntpAfterIdleManager).onIdleTimeoutSelected(60L)
    }

    @Test
    fun whenNewTabPageCommandContainsShortcutThenItPersistsOnlyThatField() = runTest {
        optionDataStore.setShowOnAppLaunchOption(NewTabPage)

        testee.setDestination(AfterInactivityReturnDestination.NewTabPage(returnToLastTabShortcutEnabled = false))

        verify(ntpAfterIdleManager).setReturnToLastTabEnabled(false)
        verify(ntpAfterIdleManager, never()).onIdleTimeoutSelected(any())
        assertNull(settingsDataStore.userSelectedIdleThresholdSeconds)
    }

    @Test
    fun whenSpecificPageCommandContainsTimeoutThenItPersistsTimeoutAndNotifiesIdleManager() = runTest {
        optionDataStore.setShowOnAppLaunchOption(SpecificPage("https://example.com/", null))

        testee.setDestination(AfterInactivityReturnDestination.SpecificPage("example.com", selectedTimeoutSeconds = 600L))

        assertEquals(SpecificPage("example.com"), optionDataStore.optionFlow.first())
        assertEquals(600L, settingsDataStore.userSelectedIdleThresholdSeconds)
        verify(ntpAfterIdleManager).onIdleTimeoutSelected(600L)
    }

    @Test
    fun whenTimeoutOnlyChangeOnSpecificPageWithSameUrlThenResolvedUrlAndTabIdAreKeptAndTimeoutPersists() = runTest {
        val url = "https://example.com/"
        val resolvedUrl = "https://www.example.com/"
        optionDataStore.setShowOnAppLaunchOption(SpecificPage(url, resolvedUrl))
        optionDataStore.setShowOnAppLaunchTabId("tab-1")

        testee.setDestination(AfterInactivityReturnDestination.SpecificPage(url = url, selectedTimeoutSeconds = 600L))

        assertEquals(SpecificPage(url, resolvedUrl), optionDataStore.optionFlow.first())
        assertEquals("tab-1", optionDataStore.showOnAppLaunchTabId)
        assertEquals(600L, settingsDataStore.userSelectedIdleThresholdSeconds)
        verify(ntpAfterIdleManager).onIdleTimeoutSelected(600L)
    }

    @Test
    fun whenSameUrlSpecificPageDestinationIsSetThenResolvedUrlAndTabIdAreKept() = runTest {
        val url = "https://example.com/"
        val resolvedUrl = "https://www.example.com/"
        optionDataStore.setShowOnAppLaunchOption(SpecificPage(url, resolvedUrl))
        optionDataStore.setShowOnAppLaunchTabId("tab-1")
        val callCountBeforeDestination = optionDataStore.setShowOnAppLaunchOptionCallCount

        testee.setDestination(AfterInactivityReturnDestination.SpecificPage(url = url))

        assertEquals(callCountBeforeDestination, optionDataStore.setShowOnAppLaunchOptionCallCount)
        assertEquals(SpecificPage(url, resolvedUrl), optionDataStore.optionFlow.first())
        assertEquals("tab-1", optionDataStore.showOnAppLaunchTabId)
    }

    @Test
    fun whenNewTabPageIsReselectedThenOptionIsNotRewritten() = runTest {
        optionDataStore.setShowOnAppLaunchOption(NewTabPage)
        val callCountBeforeDestination = optionDataStore.setShowOnAppLaunchOptionCallCount

        testee.setDestination(AfterInactivityReturnDestination.NewTabPage())

        assertEquals(callCountBeforeDestination, optionDataStore.setShowOnAppLaunchOptionCallCount)
    }

    @Test
    fun whenShortcutToggledThenOptionIsNotRewritten() = runTest {
        optionDataStore.setShowOnAppLaunchOption(NewTabPage)
        val callCountBeforeDestination = optionDataStore.setShowOnAppLaunchOptionCallCount

        testee.setDestination(AfterInactivityReturnDestination.NewTabPage(returnToLastTabShortcutEnabled = true))

        assertEquals(callCountBeforeDestination, optionDataStore.setShowOnAppLaunchOptionCallCount)
    }

    @Test
    fun whenEditedSpecificUrlIsSubmittedThenNewUrlIsPersistedAndResolvedStateCleared() = runTest {
        val oldUrl = "https://example.com/"
        val newUrl = "https://new.example.com/"
        optionDataStore.setShowOnAppLaunchOption(SpecificPage(oldUrl, "https://www.example.com/"))
        optionDataStore.setShowOnAppLaunchTabId("tab-1")

        testee.setDestination(AfterInactivityReturnDestination.SpecificPage(url = newUrl))

        assertEquals(SpecificPage(newUrl), optionDataStore.optionFlow.first())
        assertNull(optionDataStore.showOnAppLaunchTabId)
    }

    @Test
    fun whenOptionTypeChangesThenItIsPersisted() = runTest {
        optionDataStore.setShowOnAppLaunchOption(NewTabPage)

        testee.setDestination(AfterInactivityReturnDestination.LastUsedTab)

        assertEquals(LastOpenedTab, optionDataStore.optionFlow.first())
    }

    private fun setRemoteDefault(seconds: Long) {
        browserConfigFeature.showNTPAfterIdleReturn().setRawStoredState(
            Toggle.State(enable = true, settings = """{"defaultIdleThresholdSeconds":$seconds}"""),
        )
    }
}
