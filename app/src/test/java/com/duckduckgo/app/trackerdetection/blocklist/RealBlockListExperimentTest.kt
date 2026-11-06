/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.app.trackerdetection.blocklist

import android.annotation.SuppressLint
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeToggleStore
import com.duckduckgo.feature.toggles.api.FeatureToggles
import com.duckduckgo.feature.toggles.api.FeatureTogglesInventory
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.impl.RealFeatureTogglesInventory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@SuppressLint("DenyListedApi")
class RealBlockListExperimentTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val feature = FeatureToggles.Builder(FakeToggleStore(), featureName = "blockList").build()
        .create(TestBlockListFeature::class.java)
    private val featureInventory = CountingInventory(
        listOf(feature.nonMatchingFeatureName(), feature.tdsNextExperimentTest(), feature.tdsNextExperimentAnotherTest()),
    )
    private val inventory = RealFeatureTogglesInventory(setOf(featureInventory), coroutineRule.testDispatcherProvider)
    private val experiment = RealBlockListExperiment(inventory, BlockListPixelsPlugin(inventory))

    @Test
    fun `activeExperiment is the first enabled tds experiment`() = runTest {
        feature.nonMatchingFeatureName().setRawStoredState(Toggle.State(enable = true))
        feature.tdsNextExperimentAnotherTest().setRawStoredState(Toggle.State(enable = true))

        assertEquals(feature.tdsNextExperimentAnotherTest().featureName(), experiment.activeExperiment()?.featureName())
    }

    @Test
    fun `activeExperiment is null when no tds experiment is enabled`() = runTest {
        feature.nonMatchingFeatureName().setRawStoredState(Toggle.State(enable = true))

        assertNull(experiment.activeExperiment())
    }

    @Test
    fun `activeExperiment follows enabled state changes without walking the toggles again`() = runTest {
        feature.tdsNextExperimentTest().setRawStoredState(Toggle.State(enable = true))
        assertEquals(feature.tdsNextExperimentTest().featureName(), experiment.activeExperiment()?.featureName())

        feature.tdsNextExperimentTest().setRawStoredState(Toggle.State(enable = false))
        feature.tdsNextExperimentAnotherTest().setRawStoredState(Toggle.State(enable = true))
        assertEquals(feature.tdsNextExperimentAnotherTest().featureName(), experiment.activeExperiment()?.featureName())

        assertEquals(1, featureInventory.getAllCalls)
    }

    private class CountingInventory(private val toggles: List<Toggle>) : FeatureTogglesInventory {
        var getAllCalls = 0

        override suspend fun getAll(): List<Toggle> {
            getAllCalls++
            return toggles
        }
    }
}
