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

package com.duckduckgo.daxprompts.impl.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DaxPromptsDataStoreTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private lateinit var testDataStore: DataStore<Preferences>
    private lateinit var testee: SharedPreferencesDaxPromptsDataStore

    @Before
    fun setup() {
        testDataStore = PreferenceDataStoreFactory.create(
            scope = coroutineRule.testScope,
            produceFile = { context.preferencesDataStoreFile("dax_prompts_store") },
        )

        testee = SharedPreferencesDaxPromptsDataStore(testDataStore)
    }

    @Test
    fun whenNoValueSetThenGetDaxPromptsShowBrowserComparisonReturnsDefaultTrue() = runTest {
        val result = testee.getDaxPromptsShowBrowserComparison()

        assertEquals(true, result)
    }

    @Test
    fun whenValueSetToFalseThenGetDaxPromptsShowBrowserComparisonReturnsFalse() = runTest {
        testee.setDaxPromptsShowBrowserComparison(false)

        val result = testee.getDaxPromptsShowBrowserComparison()
        assertEquals(false, result)
    }

    @Test
    fun whenValueSetToTrueThenGetDaxPromptsShowBrowserComparisonReturnsTrue() = runTest {
        testee.setDaxPromptsShowBrowserComparison(false)

        testee.setDaxPromptsShowBrowserComparison(true)

        val result = testee.getDaxPromptsShowBrowserComparison()
        assertEquals(true, result)
    }

    @Test
    fun whenValueManuallySetInDataStoreThenGetDaxPromptsShowBrowserComparisonReturnsCorrectValue() = runTest {
        val key = booleanPreferencesKey(name = "DAX_PROMPTS_SHOW_BROWSER_COMPARISON")
        testDataStore.edit { preferences ->
            preferences[key] = false
        }

        val result = testee.getDaxPromptsShowBrowserComparison()
        assertEquals(false, result)
    }
}
