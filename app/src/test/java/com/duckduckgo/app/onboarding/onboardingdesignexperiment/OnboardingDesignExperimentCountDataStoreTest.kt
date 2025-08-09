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

package com.duckduckgo.app.onboarding.onboardingdesignexperiment

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.onboardingdesignexperiment.SharedPreferencesOnboardingDesignExperimentCountDataStore
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class OnboardingDesignExperimentCountDataStoreTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext

    private lateinit var testDataStore: DataStore<Preferences>
    private lateinit var testee: SharedPreferencesOnboardingDesignExperimentCountDataStore

    @Before
    fun setup() {
        testDataStore = PreferenceDataStoreFactory.create(
            scope = coroutineRule.testScope,
            produceFile = { context.preferencesDataStoreFile("onboarding_experiment_visit_count_test") },
        )
        testee = SharedPreferencesOnboardingDesignExperimentCountDataStore(coroutineRule.testDispatcherProvider, testDataStore)
    }

    @Test
    fun whenGetSiteVisitCountForNewPixelDefinition_returnsZero() = runTest {
        val count = testee.getSiteVisitCount()

        assertEquals(0, count)
    }

    @Test
    fun whenIncreaseSiteVisitCountForPixelDefinition_returnIncrementedValue() = runTest {
        val result = testee.increaseSiteVisitCount()

        assertEquals(1, result)
    }

    @Test
    fun whenIncreaseSiteVisitCountMultipleTimes_returnsCorrectValues() = runTest {
        val firstIncrease = testee.increaseSiteVisitCount()
        val secondIncrease = testee.increaseSiteVisitCount()
        val thirdIncrease = testee.increaseSiteVisitCount()

        assertEquals(1, firstIncrease)
        assertEquals(2, secondIncrease)
        assertEquals(3, thirdIncrease)
    }

    @Test
    fun whenGettingSiteVisitCount_afterMultipleIncreases_returnsCorrectCount() = runTest {
        testee.increaseSiteVisitCount()
        testee.increaseSiteVisitCount()

        val count = testee.getSiteVisitCount()
        assertEquals(2, count)
    }

    @Test
    fun whenGetSerpVisitCountForNewPixelDefinition_returnsZero() = runTest {
        val count = testee.getSerpVisitCount()

        assertEquals(0, count)
    }

    @Test
    fun whenIncreaseSerpVisitCountForPixelDefinition_returnIncrementedValue() = runTest {
        val result = testee.increaseSerpVisitCount()

        assertEquals(1, result)
    }

    @Test
    fun whenIncreaseSerpVisitCountMultipleTimes_returnsCorrectValues() = runTest {
        val firstIncrease = testee.increaseSerpVisitCount()
        val secondIncrease = testee.increaseSerpVisitCount()
        val thirdIncrease = testee.increaseSerpVisitCount()

        assertEquals(1, firstIncrease)
        assertEquals(2, secondIncrease)
        assertEquals(3, thirdIncrease)
    }

    @Test
    fun whenGettingSerpVisitCount_afterMultipleIncreases_returnsCorrectCount() = runTest {
        testee.increaseSerpVisitCount()
        testee.increaseSerpVisitCount()

        val count = testee.getSerpVisitCount()
        assertEquals(2, count)
    }
}
