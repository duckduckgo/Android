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
import com.duckduckgo.app.generalsettings.showonapplaunch.model.ShowOnAppLaunchOption.LastOpenedTab
import com.duckduckgo.app.generalsettings.showonapplaunch.model.ShowOnAppLaunchOption.NewTabPage
import com.duckduckgo.app.generalsettings.showonapplaunch.model.ShowOnAppLaunchOption.SpecificPage
import com.duckduckgo.app.generalsettings.showonapplaunch.store.FakeShowOnAppLaunchOptionDataStore
import com.duckduckgo.app.pixels.AppPixelName.SETTINGS_GENERAL_APP_LAUNCH_LAST_OPENED_TAB_SELECTED
import com.duckduckgo.app.pixels.AppPixelName.SETTINGS_GENERAL_APP_LAUNCH_NEW_TAB_PAGE_SELECTED
import com.duckduckgo.app.pixels.AppPixelName.SETTINGS_GENERAL_APP_LAUNCH_SPECIFIC_PAGE_SELECTED
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.fakes.FakePixel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ShowOnAppLaunchViewModelTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    private lateinit var testee: ShowOnAppLaunchViewModel
    private lateinit var fakeDataStore: FakeShowOnAppLaunchOptionDataStore
    private lateinit var fakePixel: FakePixel
    private val dispatcherProvider: DispatcherProvider = coroutineTestRule.testDispatcherProvider

    @Before
    fun setup() {
        fakeDataStore = FakeShowOnAppLaunchOptionDataStore(LastOpenedTab)
        fakePixel = FakePixel()
        testee = ShowOnAppLaunchViewModel(dispatcherProvider, fakeDataStore, FakeUrlConverter(), fakePixel)
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
    fun whenOptionChangedToLastOpenedPageThenLastOpenedPageIsFired() = runTest {
        testee.onShowOnAppLaunchOptionChanged(NewTabPage)
        testee.onShowOnAppLaunchOptionChanged(LastOpenedTab)
        assertEquals(2, fakePixel.firedPixels.size)
        assertEquals(SETTINGS_GENERAL_APP_LAUNCH_LAST_OPENED_TAB_SELECTED.pixelName, fakePixel.firedPixels.last())
    }

    @Test
    fun whenOptionChangedToNewTabPageThenNewTabPagePixelIsFired() = runTest {
        testee.onShowOnAppLaunchOptionChanged(NewTabPage)
        assertEquals(1, fakePixel.firedPixels.size)
        assertEquals(SETTINGS_GENERAL_APP_LAUNCH_NEW_TAB_PAGE_SELECTED.pixelName, fakePixel.firedPixels[0])
    }

    @Test
    fun whenOptionChangedToSpecificPageThenSpecificPixelIsFired() = runTest {
        testee.onShowOnAppLaunchOptionChanged(SpecificPage("https://example.com"))
        assertEquals(1, fakePixel.firedPixels.size)
        assertEquals(SETTINGS_GENERAL_APP_LAUNCH_SPECIFIC_PAGE_SELECTED.pixelName, fakePixel.firedPixels[0])
    }

    private class FakeUrlConverter : UrlConverter {

        override fun convertUrl(url: String?): String {
            return url ?: "https://duckduckgo.com"
        }
    }
}
