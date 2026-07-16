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

package com.duckduckgo.eventhub.impl.pixels

import com.duckduckgo.feature.toggles.api.FeatureTogglesInventory
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class RealExperimentCohortResolverTest {

    private val inventory: FeatureTogglesInventory = mock()
    private val resolver = RealExperimentCohortResolver(inventory)

    @Test
    fun `resolves active experiments to name to cohort map`() = runTest {
        val toggles = listOf(
            fakeToggle("tdsNextExperiment007", "blockList", "treatment"),
            fakeToggle("cssExp1", "contentScopeExperiments", "control"),
        )
        whenever(inventory.getAllActiveExperimentToggles()).thenReturn(toggles)

        val result = resolver.activeExperimentCohorts()

        assertEquals(mapOf("tdsNextExperiment007" to "treatment", "cssExp1" to "control"), result)
    }

    @Test
    fun `toggles without an assigned cohort are omitted`() = runTest {
        val toggles = listOf(
            fakeToggle("tdsNextExperiment007", "blockList", cohort = null),
            fakeToggle("cssExp1", "contentScopeExperiments", "control"),
        )
        whenever(inventory.getAllActiveExperimentToggles()).thenReturn(toggles)

        assertEquals(mapOf("cssExp1" to "control"), resolver.activeExperimentCohorts())
    }

    @Test
    fun `no active experiments resolves to empty map`() = runTest {
        whenever(inventory.getAllActiveExperimentToggles()).thenReturn(emptyList())

        assertEquals(emptyMap<String, String>(), resolver.activeExperimentCohorts())
    }

    private suspend fun fakeToggle(
        name: String,
        parentName: String,
        cohort: String?,
    ): Toggle = mock<Toggle>().apply {
        whenever(featureName()).thenReturn(Toggle.FeatureName(parentName = parentName, name = name))
        whenever(getCohort()).thenReturn(
            cohort?.let { Toggle.State.Cohort(name = it, weight = 1) },
        )
    }
}
