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

package com.duckduckgo.webtelemetry.impl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebTelemetryConfigParserTest {

    @Test
    fun `empty json returns empty list`() {
        assertTrue(WebTelemetryConfigParser.parseActiveTelemetryTypes("{}").isEmpty())
    }

    @Test
    fun `disabled feature returns empty list`() {
        val json = """
            {
                "state": "disabled",
                "settings": {
                    "telemetryTypes": {
                        "adwall": {
                            "state": "enabled",
                            "template": "counter",
                            "buckets": ["0-1", "2-3", "4+"],
                            "period": "day",
                            "pixel": "webTelemetry.adwallDetection.day"
                        }
                    }
                }
            }
        """.trimIndent()
        assertTrue(WebTelemetryConfigParser.parseActiveTelemetryTypes(json).isEmpty())
    }

    @Test
    fun `enabled feature with enabled counter type parsed correctly`() {
        val json = """
            {
                "state": "enabled",
                "settings": {
                    "telemetryTypes": {
                        "adwall": {
                            "state": "enabled",
                            "template": "counter",
                            "buckets": ["0-1", "2-3", "4-5", "6-10", "11-20", "21-39", "40+"],
                            "period": "day",
                            "pixel": "webTelemetry.adwallDetection.day"
                        }
                    }
                }
            }
        """.trimIndent()

        val result = WebTelemetryConfigParser.parseActiveTelemetryTypes(json)
        assertEquals(1, result.size)

        val config = result[0]
        assertEquals("adwall", config.name)
        assertEquals("enabled", config.state)
        assertEquals("counter", config.template)
        assertEquals("day", config.period)
        assertEquals("webTelemetry.adwallDetection.day", config.pixel)
        assertEquals(7, config.buckets.size)
        assertEquals("0-1", config.buckets[0])
        assertEquals("40+", config.buckets[6])
        assertTrue(config.isEnabled)
        assertTrue(config.isCounter)
    }

    @Test
    fun `disabled telemetry type is excluded from active types`() {
        val json = """
            {
                "state": "enabled",
                "settings": {
                    "telemetryTypes": {
                        "adwall": {
                            "state": "disabled",
                            "template": "counter",
                            "buckets": ["0-1"],
                            "period": "day",
                            "pixel": "test.pixel"
                        }
                    }
                }
            }
        """.trimIndent()

        assertTrue(WebTelemetryConfigParser.parseActiveTelemetryTypes(json).isEmpty())
    }

    @Test
    fun `multiple telemetry types parsed, only enabled returned`() {
        val json = """
            {
                "state": "enabled",
                "settings": {
                    "telemetryTypes": {
                        "adwall": {
                            "state": "enabled",
                            "template": "counter",
                            "buckets": ["0-1", "2+"],
                            "period": "day",
                            "pixel": "pixel.adwall"
                        },
                        "tracker": {
                            "state": "disabled",
                            "template": "counter",
                            "buckets": ["0-1"],
                            "period": "week",
                            "pixel": "pixel.tracker"
                        },
                        "fingerprint": {
                            "state": "enabled",
                            "template": "counter",
                            "buckets": ["0", "1+"],
                            "period": "week",
                            "pixel": "pixel.fingerprint"
                        }
                    }
                }
            }
        """.trimIndent()

        val result = WebTelemetryConfigParser.parseActiveTelemetryTypes(json)
        assertEquals(2, result.size)
        assertEquals("adwall", result[0].name)
        assertEquals("fingerprint", result[1].name)
    }

    @Test
    fun `missing settings returns empty list`() {
        val json = """{"state": "enabled"}"""
        assertTrue(WebTelemetryConfigParser.parseActiveTelemetryTypes(json).isEmpty())
    }

    @Test
    fun `missing template skips entry`() {
        val json = """
            {
                "state": "enabled",
                "settings": {
                    "telemetryTypes": {
                        "adwall": {
                            "state": "enabled",
                            "buckets": ["0-1"],
                            "period": "day",
                            "pixel": "test.pixel"
                        }
                    }
                }
            }
        """.trimIndent()

        assertTrue(WebTelemetryConfigParser.parseActiveTelemetryTypes(json).isEmpty())
    }

    @Test
    fun `missing pixel skips entry`() {
        val json = """
            {
                "state": "enabled",
                "settings": {
                    "telemetryTypes": {
                        "adwall": {
                            "state": "enabled",
                            "template": "counter",
                            "buckets": ["0-1"],
                            "period": "day"
                        }
                    }
                }
            }
        """.trimIndent()

        assertTrue(WebTelemetryConfigParser.parseActiveTelemetryTypes(json).isEmpty())
    }

    @Test
    fun `invalid json returns empty list`() {
        assertTrue(WebTelemetryConfigParser.parseActiveTelemetryTypes("not json").isEmpty())
    }

    @Test
    fun `week period parsed correctly`() {
        val json = """
            {
                "state": "enabled",
                "settings": {
                    "telemetryTypes": {
                        "weekly": {
                            "state": "enabled",
                            "template": "counter",
                            "buckets": ["0-5", "6+"],
                            "period": "week",
                            "pixel": "pixel.weekly"
                        }
                    }
                }
            }
        """.trimIndent()

        val result = WebTelemetryConfigParser.parseActiveTelemetryTypes(json)
        assertEquals(1, result.size)
        assertEquals("week", result[0].period)
    }
}
