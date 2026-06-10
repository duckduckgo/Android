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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.generalsettings.showonapplaunch.model.ShowOnAppLaunchOption
import com.duckduckgo.app.generalsettings.showonapplaunch.store.FakeShowOnAppLaunchOptionDataStore
import com.duckduckgo.app.generalsettings.showonapplaunch.store.ShowOnAppLaunchOptionDataStore
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.DispatcherProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShowOnAppLaunchReporterPluginTest {

    @get:Rule val coroutineTestRule = CoroutineTestRule()

    private val dispatcherProvider: DispatcherProvider = coroutineTestRule.testDispatcherProvider
    private lateinit var testee: ShowOnAppLaunchStateReporterPlugin
    private lateinit var fakeDataStore: ShowOnAppLaunchOptionDataStore

    @Before
    fun setup() {
        fakeDataStore = FakeShowOnAppLaunchOptionDataStore(ShowOnAppLaunchOption.LastOpenedTab)

        testee = ShowOnAppLaunchStateReporterPlugin(dispatcherProvider, fakeDataStore)
    }

    @Test
    fun whenOptionIsSetToLastOpenedPageThenShouldReturnDailyPixelValue() = runTest {
        fakeDataStore.setShowOnAppLaunchOption(ShowOnAppLaunchOption.LastOpenedTab)
        val result = testee.featureStateParams()
        assertEquals("last_opened_tab", result[PixelParameter.LAUNCH_SCREEN])
    }

    @Test
    fun whenOptionIsSetToNewTabPageThenShouldReturnDailyPixelValue() = runTest {
        fakeDataStore.setShowOnAppLaunchOption(ShowOnAppLaunchOption.NewTabPage)
        val result = testee.featureStateParams()
        assertEquals("new_tab_page", result[PixelParameter.LAUNCH_SCREEN])
    }

    @Test
    fun whenOptionIsSetToSpecificPageThenShouldReturnDailyPixelValue() = runTest {
        val specificPage = ShowOnAppLaunchOption.SpecificPage("example.com")
        fakeDataStore.setShowOnAppLaunchOption(specificPage)
        val result = testee.featureStateParams()
        assertEquals("specific_page", result[PixelParameter.LAUNCH_SCREEN])
    }
}
