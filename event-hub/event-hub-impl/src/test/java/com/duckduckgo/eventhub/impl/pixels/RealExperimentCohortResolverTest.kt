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
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.ZonedDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class RealExperimentCohortResolverTest {

    private val inventory: FeatureTogglesInventory = mock()
    private val resolver = RealExperimentCohortResolver(inventory)

    @Test
    fun `resolves active experiments to name cohort and enrollment millis`() = runTest {
        val enrollmentDate = "2026-01-26T00:00:00-05:00[America/New_York]"
        val toggles = listOf(
            fakeToggle("tdsNextExperiment007", "blockList", "treatment", enrollmentDate),
            fakeToggle("cssExp1", "contentScopeExperiments", "control", null),
        )
        whenever(inventory.getAllActiveExperimentToggles()).thenReturn(toggles)

        val result = resolver.activeExperiments(matchExperiments = null)

        assertEquals(2, result.size)
        assertEquals(ResolvedExperiment("cssExp1", "control", null), result[0])
        assertEquals(
            ResolvedExperiment("tdsNextExperiment007", "treatment", ZonedDateTime.parse(enrollmentDate).toInstant().toEpochMilli()),
            result[1],
        )
    }

    @Test
    fun `matchExperiments filters by name prefix`() = runTest {
        val toggles = listOf(
            fakeToggle("tdsNextExperiment007", "blockList", "treatment", null),
            fakeToggle("cssExp1", "contentScopeExperiments", "control", null),
        )
        whenever(inventory.getAllActiveExperimentToggles()).thenReturn(toggles)

        val result = resolver.activeExperiments(matchExperiments = listOf("tdsNextExperiment"))

        assertEquals(1, result.size)
        assertEquals("tdsNextExperiment007", result[0].name)
    }

    @Test
    fun `toggles without an assigned cohort are omitted`() = runTest {
        val toggles = listOf(fakeToggle("tdsNextExperiment007", "blockList", cohort = null, enrollmentDateET = null))
        whenever(inventory.getAllActiveExperimentToggles()).thenReturn(toggles)

        assertEquals(0, resolver.activeExperiments(matchExperiments = null).size)
    }

    @Test
    fun `unparseable enrollment date resolves to null millis`() = runTest {
        val toggles = listOf(fakeToggle("tdsNextExperiment007", "blockList", "treatment", "not-a-date"))
        whenever(inventory.getAllActiveExperimentToggles()).thenReturn(toggles)

        assertNull(resolver.activeExperiments(matchExperiments = null)[0].enrollmentDateMillis)
    }

    private suspend fun fakeToggle(
        name: String,
        parentName: String,
        cohort: String?,
        enrollmentDateET: String?,
    ): Toggle = mock<Toggle>().apply {
        whenever(featureName()).thenReturn(Toggle.FeatureName(parentName = parentName, name = name))
        whenever(getCohort()).thenReturn(
            cohort?.let { Toggle.State.Cohort(name = it, weight = 1, enrollmentDateET = enrollmentDateET) },
        )
    }
}
