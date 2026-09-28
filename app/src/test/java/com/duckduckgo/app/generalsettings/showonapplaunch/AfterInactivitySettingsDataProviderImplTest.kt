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
import com.duckduckgo.app.generalsettings.showonapplaunch.model.ShowOnAppLaunchOption
import com.duckduckgo.app.generalsettings.showonapplaunch.model.ShowOnAppLaunchOption.LastOpenedTab
import com.duckduckgo.app.generalsettings.showonapplaunch.model.ShowOnAppLaunchOption.NewTabPage
import com.duckduckgo.app.generalsettings.showonapplaunch.model.ShowOnAppLaunchOption.SpecificPage
import com.duckduckgo.app.generalsettings.showonapplaunch.store.ShowOnAppLaunchOptionDataStore
import com.duckduckgo.browser.feature.toggles.AndroidBrowserConfigFeature
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.newtabpage.api.NtpAfterIdleManager
import com.duckduckgo.settings.api.AfterInactivityReturnDestination
import com.duckduckgo.settings.api.AfterInactivitySettings
import kotlinx.coroutines.flow.MutableStateFlow
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

    private val optionFlow = MutableStateFlow<ShowOnAppLaunchOption>(NewTabPage)
    private val shortcutEnabled = MutableStateFlow(true)
    private val optionDataStore: ShowOnAppLaunchOptionDataStore = mock {
        on { optionFlow }.thenReturn(optionFlow)
    }
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
    fun whenNewTabPageCommandContainsTimeoutThenItPersistsOnlyThatField() = runTest {
        optionFlow.value = NewTabPage

        testee.setDestination(AfterInactivityReturnDestination.NewTabPage(selectedTimeoutSeconds = 60L))

        verify(optionDataStore).setShowOnAppLaunchOption(NewTabPage)
        assertEquals(60L, settingsDataStore.userSelectedIdleThresholdSeconds)
        verify(ntpAfterIdleManager).onIdleTimeoutSelected(60L)
    }

    @Test
    fun whenNewTabPageCommandContainsShortcutThenItPersistsOnlyThatField() = runTest {
        optionFlow.value = NewTabPage

        testee.setDestination(AfterInactivityReturnDestination.NewTabPage(returnToLastTabShortcutEnabled = false))

        verify(ntpAfterIdleManager).setReturnToLastTabEnabled(false)
        verify(ntpAfterIdleManager, never()).onIdleTimeoutSelected(any())
        assertNull(settingsDataStore.userSelectedIdleThresholdSeconds)
    }

    @Test
    fun whenSpecificPageCommandContainsTimeoutThenItPersistsTimeoutAndNotifiesIdleManager() = runTest {
        optionFlow.value = SpecificPage("https://example.com/", null)

        testee.setDestination(AfterInactivityReturnDestination.SpecificPage("example.com", selectedTimeoutSeconds = 600L))

        verify(optionDataStore).setShowOnAppLaunchOption(SpecificPage("example.com"))
        assertEquals(600L, settingsDataStore.userSelectedIdleThresholdSeconds)
        verify(ntpAfterIdleManager).onIdleTimeoutSelected(600L)
    }

    private fun setRemoteDefault(seconds: Long) {
        browserConfigFeature.showNTPAfterIdleReturn().setRawStoredState(
            Toggle.State(enable = true, settings = """{"defaultIdleThresholdSeconds":$seconds}"""),
        )
    }
}
