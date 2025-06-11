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

package com.duckduckgo.widget.experiment.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.widget.experiment.store.SharedPreferencesWidgetSearchCountDataStore
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetSearchCountDataStoreTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private lateinit var testDataStore: DataStore<Preferences>
    private lateinit var testee: SharedPreferencesWidgetSearchCountDataStore

    @Before
    fun setup() {
        testDataStore = PreferenceDataStoreFactory.create(
            scope = coroutineRule.testScope,
            produceFile = { context.preferencesDataStoreFile("widget_search_count_store_test") },
        )
        testee = SharedPreferencesWidgetSearchCountDataStore(testDataStore)
    }

    @Test
    fun whenNoValueSetThenGetWidgetSearchCountReturnsDefaultZero() = runTest {
        val result = testee.getWidgetSearchCount()
        assertEquals(0, result)
    }

    @Test
    fun whenIncrementWidgetSearchCountCalledOnceThenGetWidgetSearchCountReturnsOne() = runTest {
        testee.incrementWidgetSearchCount()
        val result = testee.getWidgetSearchCount()
        assertEquals(1, result)
    }

    @Test
    fun whenIncrementWidgetSearchCountCalledMultipleTimesThenGetWidgetSearchCountReturnsCorrectCount() = runTest {
        testee.incrementWidgetSearchCount()
        testee.incrementWidgetSearchCount()
        testee.incrementWidgetSearchCount()
        val result = testee.getWidgetSearchCount()
        assertEquals(3, result)
    }

    @Test
    fun whenIncrementWidgetSearchCountAndGetWidgetSearchCountCalledIntermittentlyThenReturnsCorrectCounts() = runTest {
        assertEquals(0, testee.getWidgetSearchCount())

        testee.incrementWidgetSearchCount()
        assertEquals(1, testee.getWidgetSearchCount())

        testee.incrementWidgetSearchCount()
        testee.incrementWidgetSearchCount()
        assertEquals(3, testee.getWidgetSearchCount())
    }
}
