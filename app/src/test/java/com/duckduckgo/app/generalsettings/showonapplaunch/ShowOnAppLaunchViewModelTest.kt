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

package com.duckduckgo.app.generalsettings.showonapplaunch

import app.cash.turbine.test
import com.duckduckgo.app.generalsettings.showonapplaunch.ShowOnAppLaunchViewModel.Command.ShowTimeoutDialog
import com.duckduckgo.app.pixels.AppPixelName.SETTINGS_AFTER_INACTIVITY_TIMEOUT_CHANGED
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Count
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Daily
import com.duckduckgo.browser.feature.toggles.AndroidBrowserConfigFeature
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.settings.api.AfterInactivityReturnDestination
import com.duckduckgo.settings.api.AfterInactivitySettings
import com.duckduckgo.settings.api.AfterInactivitySettingsDataProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class ShowOnAppLaunchViewModelTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    private lateinit var testee: ShowOnAppLaunchViewModel
    private val dispatcherProvider: DispatcherProvider = coroutineTestRule.testDispatcherProvider
    private val fakeBrowserConfigFeature = FakeFeatureToggleFactory.create(
        AndroidBrowserConfigFeature::class.java,
        ioDispatcher = coroutineTestRule.testDispatcher,
    )
    private val providerSettings = MutableStateFlow<AfterInactivitySettings>(AfterInactivitySettings.LastUsedTab)
    private val fakeProvider = FakeAfterInactivitySettingsDataProvider(providerSettings)
    private val pixel: Pixel = mock()

    @Before
    fun setup() {
        testee = ShowOnAppLaunchViewModel(
            dispatcherProvider,
            fakeProvider,
            fakeBrowserConfigFeature,
            pixel,
        )
    }

    @Test
    fun whenViewModelInitializedThenInitialStateIsCorrect() = runTest {
        testee.viewState.test {
            val initialState = awaitItem()
            assertEquals(AfterInactivitySettings.LastUsedTab, initialState.selectedOption)
            assertEquals("https://duckduckgo.com/", initialState.specificPageUrl)
        }
    }

    @Test
    fun whenShowOnAppLaunchOptionChangedThenStateIsUpdated() = runTest {
        testee.onShowOnAppLaunchOptionChanged(AfterInactivityReturnDestination.NewTabPage())

        testee.viewState.test {
            val updatedState = awaitItem()
            assertEquals(AfterInactivitySettings.NewTabPage(300L, true), updatedState.selectedOption)
        }
    }

    @Test
    fun whenShowOnAppLaunchOptionChangedThenProviderReceivesDestination() = runTest {
        testee.onShowOnAppLaunchOptionChanged(AfterInactivityReturnDestination.NewTabPage())

        assertEquals(listOf(AfterInactivityReturnDestination.NewTabPage()), fakeProvider.destinations)
    }

    @Test
    fun whenLaunchOptionChangedToLastOpenedTabThenPixelsFired() = runTest {
        testee.onShowOnAppLaunchOptionChanged(AfterInactivityReturnDestination.LastUsedTab)

        verify(pixel).fire(ShowOnAppLaunchPixelName.LAUNCH_OPTION_LAST_OPENED_TAB, type = Count)
        verify(pixel).fire(ShowOnAppLaunchPixelName.LAUNCH_OPTION_LAST_OPENED_TAB_DAILY, type = Daily())
    }

    @Test
    fun whenLaunchOptionChangedToNewTabPageThenPixelsFired() = runTest {
        testee.onShowOnAppLaunchOptionChanged(AfterInactivityReturnDestination.NewTabPage())

        verify(pixel).fire(ShowOnAppLaunchPixelName.LAUNCH_OPTION_NEW_TAB_PAGE, type = Count)
        verify(pixel).fire(ShowOnAppLaunchPixelName.LAUNCH_OPTION_NEW_TAB_PAGE_DAILY, type = Daily())
    }

    @Test
    fun whenLaunchOptionChangedToSpecificPageThenPixelsFired() = runTest {
        testee.onShowOnAppLaunchOptionChanged(AfterInactivityReturnDestination.SpecificPage(url = "https://example.com"))

        verify(pixel).fire(ShowOnAppLaunchPixelName.LAUNCH_OPTION_SPECIFIC_PAGE, type = Count)
        verify(pixel).fire(ShowOnAppLaunchPixelName.LAUNCH_OPTION_SPECIFIC_PAGE_DAILY, type = Daily())
    }

    @Test
    fun whenSpecificPageUrlSetWhileSpecificPageSelectedThenStateIsUpdated() = runTest {
        val newUrl = "https://example.com"

        testee.viewState.test {
            awaitItem()
            providerSettings.value = specificPage(url = "https://old.example/")
            awaitItem()

            testee.setSpecificPageUrl(newUrl)

            val updatedState = awaitItem()
            assertEquals(newUrl, updatedState.specificPageUrl)
        }
    }

    @Test
    fun whenSpecificPageUrlSetWhileAnotherOptionSelectedThenProviderNotCalled() = runTest {
        providerSettings.value = AfterInactivitySettings.LastUsedTab

        testee.setSpecificPageUrl("https://example.com")

        assertTrue(fakeProvider.destinations.isEmpty())
    }

    @Test
    fun whenMultipleOptionsChangedThenStateIsUpdatedCorrectly() = runTest {
        testee.onShowOnAppLaunchOptionChanged(AfterInactivityReturnDestination.NewTabPage())
        testee.onShowOnAppLaunchOptionChanged(AfterInactivityReturnDestination.LastUsedTab)

        testee.viewState.test {
            val updatedState = awaitItem()
            assertEquals(AfterInactivitySettings.LastUsedTab, updatedState.selectedOption)
        }
    }

    @Test
    fun whenShowNTPAfterIdleReturnDisabledThenViewStateFalse() = runTest {
        fakeBrowserConfigFeature.showNTPAfterIdleReturn().setRawStoredState(Toggle.State(false))
        testee = ShowOnAppLaunchViewModel(dispatcherProvider, fakeProvider, fakeBrowserConfigFeature, pixel)

        testee.viewState.test {
            val state = awaitItem()
            assertFalse(state.showNTPAfterIdleReturn)
        }
    }

    @Test
    fun whenShowNTPAfterIdleReturnEnabledThenViewStateTrue() = runTest {
        fakeBrowserConfigFeature.showNTPAfterIdleReturn().setRawStoredState(Toggle.State(true))
        testee = ShowOnAppLaunchViewModel(dispatcherProvider, fakeProvider, fakeBrowserConfigFeature, pixel)

        testee.viewState.test {
            val state = awaitItem()
            assertTrue(state.showNTPAfterIdleReturn)
        }
    }

    @Test
    fun whenNoSettingsObservedYetThenSelectedIsDefaultFiveMinutes() = runTest {
        fakeBrowserConfigFeature.showNTPAfterIdleReturn().setRawStoredState(Toggle.State(true))
        testee = ShowOnAppLaunchViewModel(dispatcherProvider, fakeProvider, fakeBrowserConfigFeature, pixel)

        testee.viewState.test {
            val state = awaitItem()
            assertEquals(300L, state.selectedIdleThresholdSeconds)
        }
    }

    @Test
    fun whenNewTabPageSettingsObservedThenSelectedIsEffectiveTimeout() = runTest {
        fakeBrowserConfigFeature.showNTPAfterIdleReturn().setRawStoredState(Toggle.State(true))
        testee = ShowOnAppLaunchViewModel(dispatcherProvider, fakeProvider, fakeBrowserConfigFeature, pixel)
        providerSettings.value = newTabPage(effectiveTimeoutSeconds = 60L)

        testee.viewState.test {
            val state = awaitItem()
            assertEquals(60L, state.selectedIdleThresholdSeconds)
        }
    }

    @Test
    fun whenLastUsedTabSelectedAfterATimeoutWasObservedThenDefaultTimeoutIsUsed() = runTest {
        // The timeout row is hidden for LastUsedTab, so its value is never shown to the user and
        // does not need to be remembered across option switches.
        fakeBrowserConfigFeature.showNTPAfterIdleReturn().setRawStoredState(Toggle.State(true))
        testee = ShowOnAppLaunchViewModel(dispatcherProvider, fakeProvider, fakeBrowserConfigFeature, pixel)
        providerSettings.value = newTabPage(effectiveTimeoutSeconds = 60L)

        providerSettings.value = AfterInactivitySettings.LastUsedTab

        testee.viewState.test {
            val state = awaitItem()
            assertEquals(FirstScreenHandlerImpl.DEFAULT_IDLE_THRESHOLD_SECONDS, state.selectedIdleThresholdSeconds)
        }
    }

    @Test
    fun whenLastUsedTabSelectedThenTimeoutRowIsHidden() = runTest {
        fakeBrowserConfigFeature.showNTPAfterIdleReturn().setRawStoredState(Toggle.State(true))
        testee = ShowOnAppLaunchViewModel(dispatcherProvider, fakeProvider, fakeBrowserConfigFeature, pixel)
        providerSettings.value = AfterInactivitySettings.LastUsedTab

        testee.viewState.test {
            val state = awaitItem()
            assertFalse(state.showAfterInactivityTimeout)
        }
    }

    @Test
    fun whenNewTabPageSelectedThenTimeoutRowIsShown() = runTest {
        fakeBrowserConfigFeature.showNTPAfterIdleReturn().setRawStoredState(Toggle.State(true))
        testee = ShowOnAppLaunchViewModel(dispatcherProvider, fakeProvider, fakeBrowserConfigFeature, pixel)
        providerSettings.value = newTabPage()

        testee.viewState.test {
            val state = awaitItem()
            assertTrue(state.showAfterInactivityTimeout)
        }
    }

    @Test
    fun whenSpecificPageSelectedThenTimeoutRowIsShown() = runTest {
        fakeBrowserConfigFeature.showNTPAfterIdleReturn().setRawStoredState(Toggle.State(true))
        testee = ShowOnAppLaunchViewModel(dispatcherProvider, fakeProvider, fakeBrowserConfigFeature, pixel)
        providerSettings.value = specificPage()

        testee.viewState.test {
            val state = awaitItem()
            assertTrue(state.showAfterInactivityTimeout)
        }
    }

    @Test
    fun whenShowNTPAfterIdleReturnDisabledThenTimeoutRowIsHiddenRegardlessOfOption() = runTest {
        fakeBrowserConfigFeature.showNTPAfterIdleReturn().setRawStoredState(Toggle.State(false))
        testee = ShowOnAppLaunchViewModel(dispatcherProvider, fakeProvider, fakeBrowserConfigFeature, pixel)
        providerSettings.value = newTabPage()

        testee.viewState.test {
            val state = awaitItem()
            assertFalse(state.showAfterInactivityTimeout)
        }
    }

    @Test
    fun whenTimeoutSelectedForNewTabPageThenProviderReceivesNewTabPageDestination() = runTest {
        testee.viewState.test {
            awaitItem()
            providerSettings.value = newTabPage()
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        testee.onTimeoutSelected(60L)

        assertEquals(
            listOf(AfterInactivityReturnDestination.NewTabPage(selectedTimeoutSeconds = 60L)),
            fakeProvider.destinations,
        )
    }

    @Test
    fun whenTimeoutSelectedForSpecificPageThenProviderReceivesSpecificPageDestinationRetainingUrl() = runTest {
        testee.viewState.test {
            awaitItem()
            providerSettings.value = specificPage(url = "https://kept.example/")
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        testee.onTimeoutSelected(60L)

        assertEquals(
            listOf(AfterInactivityReturnDestination.SpecificPage(url = "https://kept.example/", selectedTimeoutSeconds = 60L)),
            fakeProvider.destinations,
        )
    }

    @Test
    fun whenTimeoutSelectedWhileLastUsedTabSelectedThenProviderNotCalled() = runTest {
        providerSettings.value = AfterInactivitySettings.LastUsedTab

        testee.onTimeoutSelected(60L)

        assertTrue(fakeProvider.destinations.isEmpty())
    }

    @Test
    fun whenTimeoutSelectedThenSavesPreferenceAndFiresPixel() = runTest {
        testee.viewState.test {
            awaitItem()
            providerSettings.value = newTabPage()
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        testee.onTimeoutSelected(60L)

        verify(pixel).fire(SETTINGS_AFTER_INACTIVITY_TIMEOUT_CHANGED, mapOf("selectedSeconds" to "60"))
    }

    @Test
    fun whenTimeoutSelectedThenViewStateUpdated() = runTest {
        testee.viewState.test {
            awaitItem()
            providerSettings.value = newTabPage()
            awaitItem()

            testee.onTimeoutSelected(0L)

            val state = awaitItem()
            assertEquals(0L, state.selectedIdleThresholdSeconds)
        }
    }

    @Test
    fun whenReturnToLastTabToggledOffWhileNewTabPageSelectedThenProviderReceivesDestination() = runTest {
        testee.viewState.test {
            awaitItem()
            providerSettings.value = newTabPage()
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        testee.onReturnToLastTabToggled(false)

        assertEquals(
            listOf(AfterInactivityReturnDestination.NewTabPage(returnToLastTabShortcutEnabled = false)),
            fakeProvider.destinations,
        )
    }

    @Test
    fun whenReturnToLastTabToggledOnWhileNewTabPageSelectedThenProviderReceivesDestination() = runTest {
        testee.viewState.test {
            awaitItem()
            providerSettings.value = newTabPage()
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        testee.onReturnToLastTabToggled(true)

        assertEquals(
            listOf(AfterInactivityReturnDestination.NewTabPage(returnToLastTabShortcutEnabled = true)),
            fakeProvider.destinations,
        )
    }

    @Test
    fun whenReturnToLastTabToggledWhileNotNewTabPageSelectedThenProviderNotCalled() = runTest {
        providerSettings.value = AfterInactivitySettings.LastUsedTab

        testee.onReturnToLastTabToggled(true)

        assertTrue(fakeProvider.destinations.isEmpty())
    }

    @Test
    fun whenReturnToLastTabToggledOnThenSettingPersistedAndEnabledPixelsFired() = runTest {
        testee.viewState.test {
            awaitItem()
            providerSettings.value = newTabPage()
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        testee.onReturnToLastTabToggled(true)

        verify(pixel).fire(ShowOnAppLaunchPixelName.LAST_TAB_SHORTCUT_SETTING_ENABLED, type = Count)
        verify(pixel).fire(ShowOnAppLaunchPixelName.LAST_TAB_SHORTCUT_SETTING_ENABLED_DAILY, type = Daily())
    }

    @Test
    fun whenReturnToLastTabToggledOffThenSettingPersistedAndDisabledPixelsFired() = runTest {
        testee.viewState.test {
            awaitItem()
            providerSettings.value = newTabPage()
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        testee.onReturnToLastTabToggled(false)

        verify(pixel).fire(ShowOnAppLaunchPixelName.LAST_TAB_SHORTCUT_SETTING_DISABLED, type = Count)
        verify(pixel).fire(ShowOnAppLaunchPixelName.LAST_TAB_SHORTCUT_SETTING_DISABLED_DAILY, type = Daily())
    }

    @Test
    fun whenViewStateCreatedThenDefaultOptionsExposed() = runTest {
        fakeBrowserConfigFeature.showNTPAfterIdleReturn().setRawStoredState(Toggle.State(true))
        testee = ShowOnAppLaunchViewModel(dispatcherProvider, fakeProvider, fakeBrowserConfigFeature, pixel)

        testee.viewState.test {
            val state = awaitItem()
            assertEquals(FirstScreenHandlerImpl.DEFAULT_IDLE_THRESHOLD_OPTIONS, state.idleThresholdOptions)
        }
    }

    @Test
    fun whenTimeoutRowClickedThenEmitsShowTimeoutDialogCommand() = runTest {
        fakeBrowserConfigFeature.showNTPAfterIdleReturn().setRawStoredState(Toggle.State(true))
        testee = ShowOnAppLaunchViewModel(dispatcherProvider, fakeProvider, fakeBrowserConfigFeature, pixel)
        providerSettings.value = newTabPage(effectiveTimeoutSeconds = 300L)

        testee.commands.test {
            testee.onTimeoutRowClicked()
            val command = awaitItem()
            assertTrue(command is ShowTimeoutDialog)
            val dialog = command as ShowTimeoutDialog
            assertEquals(FirstScreenHandlerImpl.DEFAULT_IDLE_THRESHOLD_OPTIONS, dialog.options)
            assertEquals(300L, dialog.currentSelection)
        }
    }

    private fun newTabPage(
        effectiveTimeoutSeconds: Long = 300L,
        returnToLastTabShortcutEnabled: Boolean = true,
    ) = AfterInactivitySettings.NewTabPage(
        effectiveTimeoutSeconds = effectiveTimeoutSeconds,
        returnToLastTabShortcutEnabled = returnToLastTabShortcutEnabled,
    )

    private fun specificPage(
        url: String = "https://duckduckgo.com/",
        effectiveTimeoutSeconds: Long = 300L,
    ) = AfterInactivitySettings.SpecificPage(
        url = url,
        effectiveTimeoutSeconds = effectiveTimeoutSeconds,
    )

    private class FakeAfterInactivitySettingsDataProvider(
        override val settings: MutableStateFlow<AfterInactivitySettings>,
    ) : AfterInactivitySettingsDataProvider {
        val destinations = mutableListOf<AfterInactivityReturnDestination>()
        private var effectiveTimeoutSeconds = 300L
        private var shortcutEnabled = true

        override suspend fun setDestination(destination: AfterInactivityReturnDestination) {
            destinations += destination
            settings.value = when (destination) {
                AfterInactivityReturnDestination.LastUsedTab -> AfterInactivitySettings.LastUsedTab
                is AfterInactivityReturnDestination.NewTabPage -> {
                    effectiveTimeoutSeconds = destination.selectedTimeoutSeconds ?: effectiveTimeoutSeconds
                    shortcutEnabled = destination.returnToLastTabShortcutEnabled ?: shortcutEnabled
                    AfterInactivitySettings.NewTabPage(effectiveTimeoutSeconds, shortcutEnabled)
                }
                is AfterInactivityReturnDestination.SpecificPage -> {
                    effectiveTimeoutSeconds = destination.selectedTimeoutSeconds ?: effectiveTimeoutSeconds
                    AfterInactivitySettings.SpecificPage(destination.url, effectiveTimeoutSeconds)
                }
            }
        }
    }
}
