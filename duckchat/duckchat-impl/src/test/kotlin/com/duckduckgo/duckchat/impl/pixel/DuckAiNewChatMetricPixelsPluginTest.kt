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

package com.duckduckgo.duckchat.impl.pixel

import android.annotation.SuppressLint
import com.duckduckgo.feature.toggles.api.ConversionWindow
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.FeatureTogglesInventory
import com.duckduckgo.feature.toggles.api.MetricType
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.DefaultFeatureValue
import com.duckduckgo.feature.toggles.api.Toggle.State
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

@SuppressLint("DenyListedApi")
class DuckAiNewChatMetricPixelsPluginTest {

    interface TestToggles {
        @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
        fun self(): Toggle

        @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
        fun someExperiment(): Toggle
    }

    private val toggles = FakeFeatureToggleFactory.create(TestToggles::class.java)

    private val inventory = object : FeatureTogglesInventory {
        override suspend fun getAll(): List<Toggle> = listOf(toggles.someExperiment())
        override suspend fun getAllTogglesForParent(name: String): List<Toggle> = emptyList()
        override suspend fun getAllActiveExperimentToggles(): List<Toggle> =
            getAll().filter { it.getCohort() != null && it.isEnabled() }
    }

    private val plugin = DuckAiNewChatMetricPixelsPlugin(inventory)

    @Test
    fun `when experiment enrolled then only value 1 is defined`() = runTest {
        enrollExperiment()

        val metrics = plugin.getMetrics()

        assertEquals(listOf("1"), metrics.map { it.value })
        assertTrue(metrics.all { it.metric == "duck_ai_new_chat" && it.type == MetricType.COUNT_WHEN_IN_WINDOW })
    }

    @Test
    fun `when experiment enrolled then value 1 uses days 0 and 1 plus the aggregate windows`() = runTest {
        enrollExperiment()

        val windows = plugin.getMetrics().single().conversionWindow

        val expected = listOf(
            ConversionWindow(lowerWindow = 0, upperWindow = 0),
            ConversionWindow(lowerWindow = 1, upperWindow = 1),
            ConversionWindow(lowerWindow = 5, upperWindow = 7),
            ConversionWindow(lowerWindow = 8, upperWindow = 14),
        )
        assertEquals(expected, windows)
    }

    @Test
    fun `when no experiment enrolled then no metrics are defined`() = runTest {
        assertTrue(plugin.getMetrics().isEmpty())
    }

    private fun enrollExperiment() {
        val today = ZonedDateTime.now(ZoneId.of("America/New_York")).toString()
        val cohort = State.Cohort(name = "control", weight = 1, enrollmentDateET = today)
        toggles.someExperiment().setRawStoredState(
            State(
                remoteEnableState = true,
                enable = true,
                cohorts = listOf(cohort),
                assignedCohort = cohort,
            ),
        )
    }
}
