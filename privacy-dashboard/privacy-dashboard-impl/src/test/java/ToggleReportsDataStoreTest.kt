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

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.privacy.dashboard.impl.SharedPreferencesToggleReportsDataStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class ToggleReportsDataStoreTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val testDataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = coroutineRule.testScope,
        produceFile = { context.preferencesDataStoreFile("toggle_reports") },
    )

    private lateinit var testee: SharedPreferencesToggleReportsDataStore

    @Before
    fun setUp() {
        testee = SharedPreferencesToggleReportsDataStore(testDataStore, coroutineRule.testScope)
    }

    @Test
    fun whenDatabaseIsEmptyThenReturnsDefaultJson() = runTest {
        assertEquals("{}", testee.getToggleReportsRemoteConfigJson())
    }

    @Test
    fun whenJsonIsSetThenReturnsCorrectValue() = runTest {
        val testJson = """{"test": "value"}"""
        testee.setToggleReportsRemoteConfigJson(testJson)
        assertEquals(testJson, testee.getToggleReportsRemoteConfigJson())
    }

    @Test
    fun whenDismissLogicAndPromptLimitLogicDisabledThenCanPrompt() = runTest {
        testee.storeDismissLogicEnabled(false)
        testee.storePromptLimitLogicEnabled(false)
        assertTrue(testee.canPrompt())
    }

    @Test
    fun whenDismissIntervalNotPassedThenCanPromptReturnsFalse() = runTest {
        testee.storeDismissInterval(1000000) // Set a very long interval
        testee.storeDismissLogicEnabled(true)
        testee.storePromptLimitLogicEnabled(false)
        testee.insertTogglePromptDismiss()
        assertFalse(testee.canPrompt())
    }

    @Test
    fun whenDismissIntervalPassedThenCanPromptReturnsTrue() = runTest {
        testee.storeDismissInterval(0)
        testee.storeDismissLogicEnabled(true)
        testee.storePromptLimitLogicEnabled(false)
        testee.insertTogglePromptDismiss()
        assertTrue(testee.canPrompt())
    }

    @Test
    fun whenPromptIntervalPassedThenCanPromptReturnsTrue() = runTest {
        testee.storePromptInterval(0)
        testee.storePromptLimitLogicEnabled(true)
        testee.storeDismissLogicEnabled(false)
        testee.insertTogglePromptSend()
        assertTrue(testee.canPrompt())
    }

    @Test
    fun whenMaxPromptCountIsReachedThenCanPromptReturnsFalse() = runTest {
        testee.storeMaxPromptCount(3)
        testee.storePromptLimitLogicEnabled(true)
        testee.storeDismissLogicEnabled(false)
        testee.insertTogglePromptSend()
        assertTrue(testee.canPrompt())
        testee.insertTogglePromptSend()
        assertTrue(testee.canPrompt())
        testee.insertTogglePromptSend()
        assertFalse(testee.canPrompt())
    }

    @Test
    fun whenAllConditionsAreFavorableThenCanPromptReturnsTrue() = runTest {
        testee.storePromptLimitLogicEnabled(true)
        testee.storeDismissLogicEnabled(true)
        testee.storeMaxPromptCount(5)
        testee.storeDismissInterval(0)
        testee.storePromptInterval(0)
        assertTrue(testee.canPrompt())
    }
}
