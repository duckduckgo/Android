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

package com.duckduckgo.eventhub.impl.metrics

import com.duckduckgo.feature.toggles.api.ConversionWindow
import com.duckduckgo.feature.toggles.api.Toggle.FeatureName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MetricDefinitionsCacheTest {

    private val parsed = mutableListOf<String>()
    private val cache = MetricDefinitionsCache { settings ->
        parsed += settings
        listOf(MetricDefinition(name = settings, event = "event", windowsByThreshold = mapOf(1 to listOf(ConversionWindow(0, 7)))))
    }

    @Test
    fun `unchanged settings are parsed once`() {
        repeat(3) { cache.of(EXPERIMENT_1, SETTINGS_A) }

        assertEquals(listOf(SETTINGS_A), parsed)
    }

    @Test
    fun `equal settings in a new string are not parsed again`() {
        cache.of(EXPERIMENT_1, SETTINGS_A)
        cache.of(EXPERIMENT_1, String(SETTINGS_A.toCharArray()))

        assertEquals(listOf(SETTINGS_A), parsed)
    }

    @Test
    fun `changed settings are parsed again`() {
        cache.of(EXPERIMENT_1, SETTINGS_A)
        val definitions = cache.of(EXPERIMENT_1, SETTINGS_B)

        assertEquals(listOf(SETTINGS_A, SETTINGS_B), parsed)
        assertEquals(listOf(SETTINGS_B), definitions.map { it.name })
    }

    @Test
    fun `experiments are cached separately`() {
        cache.of(EXPERIMENT_1, SETTINGS_A)
        cache.of(EXPERIMENT_2, SETTINGS_B)
        cache.of(EXPERIMENT_1, SETTINGS_A)

        assertEquals(listOf(SETTINGS_A, SETTINGS_B), parsed)
    }

    @Test
    fun `no settings means no definitions, and settings that return are parsed again`() {
        cache.of(EXPERIMENT_1, SETTINGS_A)

        assertTrue(cache.of(EXPERIMENT_1, null).isEmpty())
        cache.of(EXPERIMENT_1, SETTINGS_A)

        assertEquals(listOf(SETTINGS_A, SETTINGS_A), parsed)
    }

    private companion object {
        val EXPERIMENT_1 = FeatureName(parentName = "contentScopeExperiments", name = "contentScopeExperiment1")
        val EXPERIMENT_2 = FeatureName(parentName = "contentScopeExperiments", name = "contentScopeExperiment2")
        const val SETTINGS_A = """{"metrics":{"a":{}}}"""
        const val SETTINGS_B = """{"metrics":{"b":{}}}"""
    }
}
