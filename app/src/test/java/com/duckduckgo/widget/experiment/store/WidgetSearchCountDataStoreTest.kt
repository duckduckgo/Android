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

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.widget.experiment.store.SharedPreferencesWidgetSearchCountDataStore
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.PixelDefinition
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class WidgetSearchCountDataStoreTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext

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
    fun whenGetMetricForNewPixelDefinition_returnsZero() = runTest {
        val pixelDefinition = PixelDefinition("test_pixel", emptyMap())

        val count = testee.getMetricForPixelDefinition(pixelDefinition)

        assertEquals(0, count)
    }

    @Test
    fun whenIncreaseMetricForPixelDefinition_returnIncrementedValue() = runTest {
        val pixelDefinition = PixelDefinition("test_pixel", emptyMap())

        val result = testee.increaseMetricForPixelDefinition(pixelDefinition)

        assertEquals(1, result)
    }

    @Test
    fun whenIncreaseMetricMultipleTimes_returnsCorrectValues() = runTest {
        val pixelDefinition = PixelDefinition("test_pixel", emptyMap())

        val firstIncrease = testee.increaseMetricForPixelDefinition(pixelDefinition)
        val secondIncrease = testee.increaseMetricForPixelDefinition(pixelDefinition)
        val thirdIncrease = testee.increaseMetricForPixelDefinition(pixelDefinition)

        assertEquals(1, firstIncrease)
        assertEquals(2, secondIncrease)
        assertEquals(3, thirdIncrease)
    }

    @Test
    fun whenGettingMetric_afterMultipleIncreases_returnsCorrectCount() = runTest {
        val pixelDefinition = PixelDefinition("test_pixel", emptyMap())

        testee.increaseMetricForPixelDefinition(pixelDefinition)
        testee.increaseMetricForPixelDefinition(pixelDefinition)

        val count = testee.getMetricForPixelDefinition(pixelDefinition)
        assertEquals(2, count)
    }

    @Test
    fun whenIncreaseDifferentPixelDefinitions_eachMaintainsOwnCount() = runTest {
        val pixelDefinition1 = PixelDefinition("pixel_one", emptyMap())
        val pixelDefinition2 = PixelDefinition("pixel_two", emptyMap())

        testee.increaseMetricForPixelDefinition(pixelDefinition1)
        testee.increaseMetricForPixelDefinition(pixelDefinition1)
        testee.increaseMetricForPixelDefinition(pixelDefinition2)

        assertEquals(2, testee.getMetricForPixelDefinition(pixelDefinition1))
        assertEquals(1, testee.getMetricForPixelDefinition(pixelDefinition2))
    }

    @Test
    fun whenUsingPixelDefinitionsWithParams_countIsStoredAndRetrieved() = runTest {
        val params = mapOf("key1" to "value1", "key2" to "value2")
        val pixelDefinition = PixelDefinition("pixel_with_params", params)

        testee.increaseMetricForPixelDefinition(pixelDefinition)

        assertEquals(1, testee.getMetricForPixelDefinition(pixelDefinition))
    }

    @Test
    fun whenIdenticalPixelDefinitionsUsed_shouldShareCounter() = runTest {
        val params = mapOf("key" to "value")
        val firstDefinition = PixelDefinition("shared_pixel", params)
        val secondDefinition = PixelDefinition("shared_pixel", params)

        testee.increaseMetricForPixelDefinition(firstDefinition)

        // Second definition with same values should share the counter
        val count = testee.getMetricForPixelDefinition(secondDefinition)
        assertEquals(1, count)
    }
}
