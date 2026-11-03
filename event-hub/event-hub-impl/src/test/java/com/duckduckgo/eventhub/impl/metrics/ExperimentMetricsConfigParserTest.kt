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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExperimentMetricsConfigParserTest {

    @Test
    fun `valid metric is parsed`() {
        val metrics = ExperimentMetricsConfigParser.parse(
            settings(""""adwallSeen": { "event": "adwall", "conversions": [ { "windows": [[0, 7]], "thresholds": [1] } ] }"""),
        )

        assertEquals(
            listOf(MetricDefinition("adwallSeen", "adwall", mapOf(1 to listOf(ConversionWindow(0, 7))))),
            metrics,
        )
    }

    @Test
    fun `windows are grouped by threshold across conversion groups`() {
        val metrics = ExperimentMetricsConfigParser.parse(
            settings(
                """
                "searchLike": {
                    "event": "searchPerformed",
                    "conversions": [
                        { "windows": [[0, 0], [1, 1]], "thresholds": [1] },
                        { "windows": [[0, 7]], "thresholds": [2, 3] }
                    ]
                }
                """,
            ),
        )

        assertEquals(
            mapOf(
                1 to listOf(ConversionWindow(0, 0), ConversionWindow(1, 1)),
                2 to listOf(ConversionWindow(0, 7)),
                3 to listOf(ConversionWindow(0, 7)),
            ),
            metrics.single().windowsByThreshold,
        )
    }

    @Test
    fun `a window and threshold pair repeated across groups is kept once`() {
        val metrics = ExperimentMetricsConfigParser.parse(
            settings(
                """
                "captchaSeen": {
                    "event": "captcha",
                    "conversions": [
                        { "windows": [[0, 7]], "thresholds": [1] },
                        { "windows": [[0, 7]], "thresholds": [1, 3] }
                    ]
                }
                """,
            ),
        )

        assertEquals(
            mapOf(1 to listOf(ConversionWindow(0, 7)), 3 to listOf(ConversionWindow(0, 7))),
            metrics.single().windowsByThreshold,
        )
    }

    @Test
    fun `several metrics on one experiment are all parsed`() {
        val metrics = ExperimentMetricsConfigParser.parse(
            settings(
                """
                "first": { "event": "a", "conversions": [ { "windows": [[0, 7]], "thresholds": [1] } ] },
                "second": { "event": "b", "conversions": [ { "windows": [[0, 7]], "thresholds": [1] } ] }
                """,
            ),
        )

        assertEquals(listOf("first", "second"), metrics.map { it.name })
    }

    @Test
    fun `other experiment settings are ignored`() {
        val metrics = ExperimentMetricsConfigParser.parse(
            """
            {
                "controlUrl": "https://example.com/control.json",
                "treatmentUrl": "https://example.com/treatment.json",
                "metrics": { "m": { "event": "a", "conversions": [ { "windows": [[0, 7]], "thresholds": [1] } ] } }
            }
            """.trimIndent(),
        )

        assertEquals(listOf("m"), metrics.map { it.name })
    }

    @Test
    fun `whole-number threshold written as a decimal is accepted`() {
        val metrics = ExperimentMetricsConfigParser.parse(
            settings(""""m": { "event": "a", "conversions": [ { "windows": [[0, 7]], "thresholds": [2.0] } ] }"""),
        )

        assertEquals(setOf(2), metrics.single().windowsByThreshold.keys)
    }

    @Test
    fun `invalid metrics are skipped and valid ones next to them are kept`() {
        val invalid = listOf(
            """"zeroThreshold": { "event": "a", "conversions": [ { "windows": [[0, 7]], "thresholds": [0] } ] }""",
            """"fractionalThreshold": { "event": "a", "conversions": [ { "windows": [[0, 7]], "thresholds": [2.5] } ] }""",
            """"invertedWindow": { "event": "a", "conversions": [ { "windows": [[7, 0]], "thresholds": [1] } ] }""",
            """"negativeWindow": { "event": "a", "conversions": [ { "windows": [[-1, 7]], "thresholds": [1] } ] }""",
            """"shortWindow": { "event": "a", "conversions": [ { "windows": [[0]], "thresholds": [1] } ] }""",
            """"noWindows": { "event": "a", "conversions": [ { "windows": [], "thresholds": [1] } ] }""",
            """"noThresholds": { "event": "a", "conversions": [ { "windows": [[0, 7]] } ] }""",
            """"noEvent": { "conversions": [ { "windows": [[0, 7]], "thresholds": [1] } ] }""",
            """"emptyEvent": { "event": "", "conversions": [ { "windows": [[0, 7]], "thresholds": [1] } ] }""",
            """"noConversions": { "event": "a", "conversions": [] }""",
            """"bad.name": { "event": "a", "conversions": [ { "windows": [[0, 7]], "thresholds": [1] } ] }""",
            """"1startsWithDigit": { "event": "a", "conversions": [ { "windows": [[0, 7]], "thresholds": [1] } ] }""",
            """"notAnObject": 42""",
        )
        val valid = """"valid": { "event": "a", "conversions": [ { "windows": [[0, 7]], "thresholds": [1] } ] }"""

        invalid.forEach { entry ->
            val metrics = ExperimentMetricsConfigParser.parse(settings("$entry, $valid"))
            assertEquals(entry, listOf("valid"), metrics.map { it.name })
        }
    }

    @Test
    fun `built-in metric names are rejected`() {
        listOf("search", "app_use", "duck_ai_prompt_sent", "duck_ai_new_chat", "privacyToggleUsed").forEach { name ->
            val metrics = ExperimentMetricsConfigParser.parse(
                settings(""""$name": { "event": "a", "conversions": [ { "windows": [[0, 7]], "thresholds": [1] } ] }"""),
            )
            assertTrue(name, metrics.isEmpty())
        }
    }

    @Test
    fun `settings without metrics give no metrics`() {
        assertTrue(ExperimentMetricsConfigParser.parse("""{ "controlUrl": "https://example.com" }""").isEmpty())
        assertTrue(ExperimentMetricsConfigParser.parse("{}").isEmpty())
    }

    @Test
    fun `malformed settings give no metrics`() {
        assertTrue(ExperimentMetricsConfigParser.parse("not json").isEmpty())
        assertTrue(ExperimentMetricsConfigParser.parse("""{ "metrics": [1, 2] }""").isEmpty())
    }

    private fun settings(metricEntries: String) = """{ "metrics": { ${metricEntries.trimIndent()} } }"""
}
