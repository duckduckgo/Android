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
import com.duckduckgo.app.generalsettings.showonapplaunch.model.ShowOnAppLaunchOption.LastOpenedTab
import com.duckduckgo.app.generalsettings.showonapplaunch.model.ShowOnAppLaunchOption.NewTabPage
import com.duckduckgo.app.generalsettings.showonapplaunch.store.FakeShowOnAppLaunchOptionDataStore
import com.duckduckgo.app.pixels.AppPixelName.SETTINGS_AFTER_INACTIVITY_TIMEOUT_CHANGED
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ShowOnAppLaunchViewModelTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    private lateinit var testee: ShowOnAppLaunchViewModel
    private lateinit var fakeDataStore: FakeShowOnAppLaunchOptionDataStore
    private val dispatcherProvider: DispatcherProvider = coroutineTestRule.testDispatcherProvider
    private val fakeBrowserConfigFeature = FakeFeatureToggleFactory.create(AndroidBrowserConfigFeature::class.java)
    private val settingsDataStore: SettingsDataStore = mock()
    private val pixel: Pixel = mock()

    @Before
    fun setup() {
        whenever(settingsDataStore.userSelectedIdleThresholdSeconds).thenReturn(null)
        fakeDataStore = FakeShowOnAppLaunchOptionDataStore(LastOpenedTab)
        testee = ShowOnAppLaunchViewModel(
            dispatcherProvider,
            fakeDataStore,
            FakeUrlConverter(),
            fakeBrowserConfigFeature,
            settingsDataStore,
            pixel,
        )
    }

    @Test
    fun whenViewModelInitializedThenInitialStateIsCorrect() = runTest {
        testee.viewState.test {
            val initialState = awaitItem()
            assertEquals(LastOpenedTab, initialState.selectedOption)
            assertEquals("https://duckduckgo.com", initialState.specificPageUrl)
        }
    }

    @Test
    fun whenShowOnAppLaunchOptionChangedThenStateIsUpdated() = runTest {
        testee.onShowOnAppLaunchOptionChanged(NewTabPage)
        testee.viewState.test {
            val updatedState = awaitItem()
            assertEquals(NewTabPage, updatedState.selectedOption)
        }
    }

    @Test
    fun whenSpecificPageUrlSetThenStateIsUpdated() = runTest {
        val newUrl = "https://example.com"

        testee.setSpecificPageUrl(newUrl)
        testee.viewState.test {
            val updatedState = awaitItem()
            assertEquals(newUrl, updatedState.specificPageUrl)
        }
    }

    @Test
    fun whenMultipleOptionsChangedThenStateIsUpdatedCorrectly() = runTest {
        testee.onShowOnAppLaunchOptionChanged(NewTabPage)
        testee.onShowOnAppLaunchOptionChanged(LastOpenedTab)
        testee.viewState.test {
            val updatedState = awaitItem()
            assertEquals(LastOpenedTab, updatedState.selectedOption)
        }
    }

    @Test
    fun whenShowNTPAfterIdleReturnDisabledThenViewStateFalse() = runTest {
        fakeBrowserConfigFeature.showNTPAfterIdleReturn().setRawStoredState(Toggle.State(false))
        testee = ShowOnAppLaunchViewModel(
            dispatcherProvider, fakeDataStore, FakeUrlConverter(), fakeBrowserConfigFeature, settingsDataStore, pixel,
        )

        testee.viewState.test {
            val state = awaitItem()
            assertFalse(state.showNTPAfterIdleReturn)
        }
    }

    @Test
    fun whenShowNTPAfterIdleReturnEnabledThenViewStateTrue() = runTest {
        fakeBrowserConfigFeature.showNTPAfterIdleReturn().setRawStoredState(Toggle.State(true))
        testee = ShowOnAppLaunchViewModel(
            dispatcherProvider, fakeDataStore, FakeUrlConverter(), fakeBrowserConfigFeature, settingsDataStore, pixel,
        )

        testee.viewState.test {
            val state = awaitItem()
            assertTrue(state.showNTPAfterIdleReturn)
        }
    }

    // --- selectedIdleThresholdSeconds resolution ---

    @Test
    fun whenNoSettingsAndNoUserPrefThenSelectedIsDefaultFiveMinutes() = runTest {
        fakeBrowserConfigFeature.showNTPAfterIdleReturn().setRawStoredState(Toggle.State(true))
        testee = ShowOnAppLaunchViewModel(
            dispatcherProvider, fakeDataStore, FakeUrlConverter(), fakeBrowserConfigFeature, settingsDataStore, pixel,
        )

        testee.viewState.test {
            val state = awaitItem()
            assertEquals(300L, state.selectedIdleThresholdSeconds)
        }
    }

    @Test
    fun whenRCDefaultInOptionsThenSelectedIsRCDefault() = runTest {
        fakeBrowserConfigFeature.showNTPAfterIdleReturn().setRawStoredState(
            Toggle.State(true, settings = """{"defaultIdleThresholdSeconds":60,"idleThresholdOptions":[1,60,300]}"""),
        )
        testee = ShowOnAppLaunchViewModel(
            dispatcherProvider, fakeDataStore, FakeUrlConverter(), fakeBrowserConfigFeature, settingsDataStore, pixel,
        )

        testee.viewState.test {
            val state = awaitItem()
            assertEquals(60L, state.selectedIdleThresholdSeconds)
        }
    }

    @Test
    fun whenRCDefaultNotInOptionsThenSelectedIsFiveMinuteFallback() = runTest {
        fakeBrowserConfigFeature.showNTPAfterIdleReturn().setRawStoredState(
            Toggle.State(true, settings = """{"defaultIdleThresholdSeconds":120,"idleThresholdOptions":[1,60,300,900]}"""),
        )
        testee = ShowOnAppLaunchViewModel(
            dispatcherProvider, fakeDataStore, FakeUrlConverter(), fakeBrowserConfigFeature, settingsDataStore, pixel,
        )

        testee.viewState.test {
            val state = awaitItem()
            assertEquals(300L, state.selectedIdleThresholdSeconds)
        }
    }

    @Test
    fun whenUserPreferenceSetThenSelectedIsUserPreference() = runTest {
        whenever(settingsDataStore.userSelectedIdleThresholdSeconds).thenReturn(1L)
        fakeBrowserConfigFeature.showNTPAfterIdleReturn().setRawStoredState(
            Toggle.State(true, settings = """{"defaultIdleThresholdSeconds":300,"idleThresholdOptions":[1,60,300]}"""),
        )
        testee = ShowOnAppLaunchViewModel(
            dispatcherProvider, fakeDataStore, FakeUrlConverter(), fakeBrowserConfigFeature, settingsDataStore, pixel,
        )

        testee.viewState.test {
            val state = awaitItem()
            assertEquals(1L, state.selectedIdleThresholdSeconds)
        }
    }

    // --- idleThresholdOptions ---

    @Test
    fun whenOptionsInRCThenExposedInViewState() = runTest {
        fakeBrowserConfigFeature.showNTPAfterIdleReturn().setRawStoredState(
            Toggle.State(true, settings = """{"defaultIdleThresholdSeconds":300,"idleThresholdOptions":[1,60,300,900]}"""),
        )
        testee = ShowOnAppLaunchViewModel(
            dispatcherProvider, fakeDataStore, FakeUrlConverter(), fakeBrowserConfigFeature, settingsDataStore, pixel,
        )

        testee.viewState.test {
            val state = awaitItem()
            assertEquals(listOf(1L, 60L, 300L, 900L), state.idleThresholdOptions)
        }
    }

    @Test
    fun whenNoOptionsInRCThenDefaultOptionsExposed() = runTest {
        fakeBrowserConfigFeature.showNTPAfterIdleReturn().setRawStoredState(
            Toggle.State(true, settings = """{"defaultIdleThresholdSeconds":300}"""),
        )
        testee = ShowOnAppLaunchViewModel(
            dispatcherProvider, fakeDataStore, FakeUrlConverter(), fakeBrowserConfigFeature, settingsDataStore, pixel,
        )

        testee.viewState.test {
            val state = awaitItem()
            assertEquals(DEFAULT_IDLE_THRESHOLD_OPTIONS, state.idleThresholdOptions)
        }
    }

    @Test
    fun whenSettingsHaveInvalidJsonThenDefaultsUsed() = runTest {
        fakeBrowserConfigFeature.showNTPAfterIdleReturn().setRawStoredState(
            Toggle.State(true, settings = """invalid"""),
        )
        testee = ShowOnAppLaunchViewModel(
            dispatcherProvider, fakeDataStore, FakeUrlConverter(), fakeBrowserConfigFeature, settingsDataStore, pixel,
        )

        testee.viewState.test {
            val state = awaitItem()
            assertEquals(300L, state.selectedIdleThresholdSeconds)
            assertEquals(DEFAULT_IDLE_THRESHOLD_OPTIONS, state.idleThresholdOptions)
        }
    }

    // --- onTimeoutSelected ---

    @Test
    fun whenTimeoutSelectedThenSavesPreferenceAndFiresPixel() = runTest {
        testee.onTimeoutSelected(60L)
        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()

        verify(settingsDataStore).userSelectedIdleThresholdSeconds = 60L
        verify(pixel).fire(SETTINGS_AFTER_INACTIVITY_TIMEOUT_CHANGED, mapOf("selectedSeconds" to "60"))
    }

    @Test
    fun whenTimeoutSelectedThenViewStateUpdated() = runTest {
        testee.onTimeoutSelected(1L)
        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()

        testee.viewState.test {
            val state = awaitItem()
            assertEquals(1L, state.selectedIdleThresholdSeconds)
        }
    }

    // --- onTimeoutRowClicked command ---

    @Test
    fun whenTimeoutRowClickedThenEmitsShowTimeoutDialogCommand() = runTest {
        fakeBrowserConfigFeature.showNTPAfterIdleReturn().setRawStoredState(
            Toggle.State(true, settings = """{"defaultIdleThresholdSeconds":300,"idleThresholdOptions":[1,60,300]}"""),
        )
        testee = ShowOnAppLaunchViewModel(
            dispatcherProvider, fakeDataStore, FakeUrlConverter(), fakeBrowserConfigFeature, settingsDataStore, pixel,
        )

        testee.commands.test {
            testee.onTimeoutRowClicked()
            val command = awaitItem()
            assertTrue(command is ShowTimeoutDialog)
            val dialog = command as ShowTimeoutDialog
            assertEquals(listOf(1L, 60L, 300L), dialog.options)
            assertEquals(300L, dialog.currentSelection)
        }
    }

    private class FakeUrlConverter : UrlConverter {

        override fun convertUrl(url: String?): String {
            return url ?: "https://duckduckgo.com"
        }
    }
}
