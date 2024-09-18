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

package com.duckduckgo.app.generalsettings.showonapplaunch.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.duckduckgo.app.generalsettings.showonapplaunch.model.ShowOnAppLaunchOption.LastOpenedTab
import com.duckduckgo.app.generalsettings.showonapplaunch.model.ShowOnAppLaunchOption.NewTabPage
import com.duckduckgo.app.generalsettings.showonapplaunch.model.ShowOnAppLaunchOption.SpecificPage
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShowOnAppLaunchPrefsDataStoreTest {

    @get:Rule val coroutineRule = CoroutineTestRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val dataStoreFile = context.preferencesDataStoreFile("show_on_app_launch")

    private val testDataStore: DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = coroutineRule.testScope,
            produceFile = { dataStoreFile },
        )

    private val testee: ShowOnAppLaunchOptionDataStore =
        ShowOnAppLaunchOptionPrefsDataStore(testDataStore)

    @After
    fun after() {
        dataStoreFile.delete()
    }

    @Test
    fun whenOptionIsNullThenShouldReturnLastOpenedPage() = runTest {
        assertEquals(LastOpenedTab, testee.optionFlow.first())
    }

    @Test
    fun whenOptionIsSetToLastOpenedPageThenShouldReturnLastOpenedPage() = runTest {
        testee.setShowOnAppLaunchOption(LastOpenedTab)
        assertEquals(LastOpenedTab, testee.optionFlow.first())
    }

    @Test
    fun whenOptionIsSetToNewTabPageThenShouldReturnNewTabPage() = runTest {
        testee.setShowOnAppLaunchOption(NewTabPage)
        assertEquals(NewTabPage, testee.optionFlow.first())
    }

    @Test
    fun whenOptionIsSetToSpecificPageThenShouldReturnSpecificPage() = runTest {
        val specificPage = SpecificPage("example.com")

        testee.setShowOnAppLaunchOption(specificPage)
        assertEquals(specificPage, testee.optionFlow.first())
    }

    @Test
    fun whenSpecificPageIsNullThenShouldReturnDefaultUrl() = runTest {
        assertEquals("https://duckduckgo.com/", testee.specificPageUrlFlow.first())
    }

    @Test
    fun whenSpecificPageUrlIsSetThenShouldReturnSpecificPageUrl() = runTest {
        testee.setSpecificPageUrl("example.com")
        assertEquals("example.com", testee.specificPageUrlFlow.first())
    }

    @Test
    fun whenOptionIsChangedThenNewOptionEmitted() = runTest {
        testee.optionFlow.test {
            val defaultOption = awaitItem()

            assertEquals(LastOpenedTab, defaultOption)

            testee.setShowOnAppLaunchOption(NewTabPage)

            assertEquals(NewTabPage, awaitItem())

            testee.setShowOnAppLaunchOption(SpecificPage("example.com"))

            assertEquals(SpecificPage("example.com"), awaitItem())
        }
    }
}
