/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.app.browser.defaultbrowsing.prompts.store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.browser.defaultbrowsing.prompts.store.DefaultBrowserPromptsDataStore.Stage
import com.duckduckgo.common.test.CoroutineTestRule
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val DATA_STORE_NAME: String = "default_browser_prompts_test_data_store"

@RunWith(AndroidJUnit4::class)
class DefaultBrowserPromptsPrefsDataStoreImplTest {

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private lateinit var testDataStoreFile: File
    private lateinit var testDataStore: DataStore<Preferences>

    @Before
    fun setUp() {
        testDataStoreFile = File.createTempFile(DATA_STORE_NAME, ".preferences_pb")
        testDataStore = PreferenceDataStoreFactory.create(
            scope = coroutinesTestRule.testScope,
            produceFile = { testDataStoreFile },
        )
    }

    @After
    fun tearDown() {
        testDataStoreFile.delete()
    }

    @Test
    fun whenExperimentInitializedThenDefaultValueIsNotEnrolled() = runTest {
        val testee = DefaultBrowserPromptsPrefsDataStoreImpl(testDataStore, coroutinesTestRule.testDispatcherProvider)

        assertEquals(testee.stage.first(), Stage.NOT_ENROLLED)
    }

    @Test
    fun whenExperimentStageIsUpdatedThenValueIsPropagated() = runTest {
        val testee = DefaultBrowserPromptsPrefsDataStoreImpl(testDataStore, coroutinesTestRule.testDispatcherProvider)
        val expectedUpdates = listOf(
            Stage.NOT_ENROLLED,
            Stage.STAGE_1,
        )
        val actualUpdates = mutableListOf<Stage>()
        coroutinesTestRule.testScope.launch {
            testee.stage.toList(actualUpdates)
        }

        testee.storeExperimentStage(Stage.STAGE_1)

        assertEquals(expectedUpdates, actualUpdates)
    }

    @Test
    fun whenExperimentInitializedThenShowPopupMenuItemIsDisabled() = runTest {
        val testee = DefaultBrowserPromptsPrefsDataStoreImpl(testDataStore, coroutinesTestRule.testDispatcherProvider)

        assertFalse(testee.showSetAsDefaultPopupMenuItem.first())
    }

    @Test
    fun whenShowPopupMenuItemIsUpdatedThenValueIsPropagated() = runTest {
        val testee = DefaultBrowserPromptsPrefsDataStoreImpl(testDataStore, coroutinesTestRule.testDispatcherProvider)
        val expectedUpdates = listOf(
            false, // initial value
            true,
        )
        val actualUpdates = mutableListOf<Boolean>()
        coroutinesTestRule.testScope.launch {
            testee.showSetAsDefaultPopupMenuItem.toList(actualUpdates)
        }

        testee.storeShowSetAsDefaultPopupMenuItemState(show = true)

        assertEquals(expectedUpdates, actualUpdates)
    }

    @Test
    fun whenExperimentInitializedThenHighlightPopupMenuIconIsDisabled() = runTest {
        val testee = DefaultBrowserPromptsPrefsDataStoreImpl(testDataStore, coroutinesTestRule.testDispatcherProvider)

        assertFalse(testee.highlightPopupMenu.first())
    }

    @Test
    fun whenHighlightPopupMenuIconIsUpdatedThenValueIsPropagated() = runTest {
        val testee = DefaultBrowserPromptsPrefsDataStoreImpl(testDataStore, coroutinesTestRule.testDispatcherProvider)
        val expectedUpdates = listOf(
            false, // initial value
            true,
        )
        val actualUpdates = mutableListOf<Boolean>()
        coroutinesTestRule.testScope.launch {
            testee.highlightPopupMenu.toList(actualUpdates)
        }

        testee.storeHighlightPopupMenuState(highlight = true)

        assertEquals(expectedUpdates, actualUpdates)
    }
}
