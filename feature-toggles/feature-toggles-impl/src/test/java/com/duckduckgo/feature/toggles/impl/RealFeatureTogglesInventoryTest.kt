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

package com.duckduckgo.feature.toggles.impl

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FeatureTogglesInventory
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.FeatureName
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RealFeatureTogglesInventoryTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val parentToggle = toggle(parent = null, name = "parent")
    private val subToggle = toggle(parent = "parent", name = "sub")
    private val otherToggle = toggle(parent = "other", name = "otherSub")

    private val countingInventory = CountingInventory(listOf(parentToggle, subToggle))
    private val duplicatingInventory = CountingInventory(listOf(subToggle, otherToggle))

    private val testee = RealFeatureTogglesInventory(
        setOf(countingInventory, duplicatingInventory),
        coroutineRule.testDispatcherProvider,
    )

    @Test
    fun whenGetAllThenReturnsTogglesFromAllInventoriesWithoutDuplicates() = runTest {
        assertEquals(listOf(parentToggle, subToggle, otherToggle), testee.getAll())
    }

    @Test
    fun whenGetAllCalledRepeatedlyThenInventoriesAreReadOnce() = runTest {
        testee.getAll()
        testee.getAll()
        testee.getAllTogglesForParent("parent")
        testee.getAllActiveExperimentToggles()

        assertEquals(1, countingInventory.calls)
        assertEquals(1, duplicatingInventory.calls)
    }

    @Test
    fun whenGetAllCalledConcurrentlyThenInventoriesAreReadOnce() = runTest {
        val results = List(10) { async { testee.getAll() } }.awaitAll()

        results.forEach { assertEquals(listOf(parentToggle, subToggle, otherToggle), it) }
        assertEquals(1, countingInventory.calls)
    }

    @Test
    fun whenGetAllTogglesForParentThenReturnsOnlyItsSubFeatures() = runTest {
        assertEquals(listOf(subToggle), testee.getAllTogglesForParent("parent"))
    }

    @Test
    fun whenGetAllActiveExperimentTogglesThenReadsToggleStateOnEveryCall() = runTest {
        whenever(subToggle.getCohort()).thenReturn(null)
        whenever(subToggle.isEnabled()).thenReturn(true)
        assertEquals(emptyList<Toggle>(), testee.getAllActiveExperimentToggles())

        val cohort: Toggle.State.Cohort = mock()
        whenever(subToggle.getCohort()).thenReturn(cohort)
        assertEquals(listOf(subToggle), testee.getAllActiveExperimentToggles())
    }

    private fun toggle(parent: String?, name: String): Toggle = mock<Toggle>().also {
        whenever(it.featureName()).thenReturn(FeatureName(parentName = parent, name = name))
    }

    private class CountingInventory(private val toggles: List<Toggle>) : FeatureTogglesInventory {
        var calls = 0
            private set

        override suspend fun getAll(): List<Toggle> {
            calls++
            return toggles
        }
    }
}
