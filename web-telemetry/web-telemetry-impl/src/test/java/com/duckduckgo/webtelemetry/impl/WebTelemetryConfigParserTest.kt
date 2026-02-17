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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebTelemetryConfigParserTest {

    private val fullConfig = """
        {
            "state": "enabled",
            "settings": {
                "telemetryTypes": {
                    "adwall": {
                        "state": "enabled",
                        "template": "counter",
                        "targets": [
                            { "pixel": "webTelemetry.adwall.day", "param": "adwall_count" },
                            { "pixel": "webTelemetry.adwall.week", "param": "adwall_count" }
                        ]
                    },
                    "trackerBlocked": {
                        "state": "enabled",
                        "template": "counter",
                        "targets": [
                            { "pixel": "webTelemetry.adwall.day", "param": "tracker_count" }
                        ]
                    }
                },
                "pixels": {
                    "webTelemetry.adwall.day": {
                        "period": "day",
                        "jitter": 0.25,
                        "parameters": {
                            "adwall_count": {
                                "type": "counter",
                                "buckets": ["0-1", "2-3", "4-5", "6-10", "11-20", "21-39", "40+"]
                            },
                            "tracker_count": {
                                "type": "counter",
                                "buckets": ["0", "1-5", "6-20", "21+"]
                            }
                        }
                    },
                    "webTelemetry.adwall.week": {
                        "period": "week",
                        "jitter": 0.25,
                        "parameters": {
                            "adwall_count": {
                                "type": "counter",
                                "buckets": ["0-5", "6-20", "21-50", "51+"]
                            }
                        }
                    }
                }
            }
        }
    """.trimIndent()

    @Test
    fun `full config parses telemetry types correctly`() {
        val config = WebTelemetryConfigParser.parse(fullConfig)
        assertTrue(config.featureEnabled)
        assertEquals(2, config.telemetryTypes.size)

        val adwall = config.telemetryTypes.find { it.name == "adwall" }!!
        assertTrue(adwall.isEnabled)
        assertEquals("counter", adwall.template)

        val counter = CounterTelemetryType.from(adwall)!!
        assertEquals(2, counter.targets.size)
        assertEquals("webTelemetry.adwall.day", counter.targets[0].pixel)
        assertEquals("adwall_count", counter.targets[0].param)

        val tracker = config.telemetryTypes.find { it.name == "trackerBlocked" }!!
        val trackerCounter = CounterTelemetryType.from(tracker)!!
        assertEquals(1, trackerCounter.targets.size)
    }

    @Test
    fun `full config parses pixels correctly`() {
        val config = WebTelemetryConfigParser.parse(fullConfig)
        assertEquals(2, config.pixels.size)

        val dailyPixel = config.pixels.find { it.name == "webTelemetry.adwall.day" }!!
        assertEquals("day", dailyPixel.period)
        assertEquals(0.25, dailyPixel.jitter, 0.001)
        assertEquals(2, dailyPixel.parameters.size)

        val adwallParam = dailyPixel.parameters["adwall_count"]!!
        assertEquals("counter", adwallParam.type)
        assertEquals(7, adwallParam.buckets.size)

        val trackerParam = dailyPixel.parameters["tracker_count"]!!
        assertEquals(4, trackerParam.buckets.size)

        val weeklyPixel = config.pixels.find { it.name == "webTelemetry.adwall.week" }!!
        assertEquals("week", weeklyPixel.period)
        assertEquals(1, weeklyPixel.parameters.size)
    }

    @Test
    fun `disabled feature returns no active types or pixels`() {
        val json = """{"state": "disabled", "settings": {"telemetryTypes": {}, "pixels": {}}}"""
        assertTrue(WebTelemetryConfigParser.parseActiveTelemetryTypes(json).isEmpty())
        assertTrue(WebTelemetryConfigParser.parseActivePixels(json).isEmpty())
    }

    @Test
    fun `empty json returns empty config`() {
        val config = WebTelemetryConfigParser.parse("{}")
        assertFalse(config.featureEnabled)
        assertTrue(config.telemetryTypes.isEmpty())
        assertTrue(config.pixels.isEmpty())
    }

    @Test
    fun `invalid json returns empty config`() {
        val config = WebTelemetryConfigParser.parse("not json")
        assertFalse(config.featureEnabled)
    }

    @Test
    fun `disabled telemetry type excluded from active types`() {
        val json = """
            {
                "state": "enabled",
                "settings": {
                    "telemetryTypes": {
                        "adwall": {
                            "state": "disabled",
                            "template": "counter",
                            "targets": [{"pixel": "p", "param": "c"}]
                        }
                    },
                    "pixels": {}
                }
            }
        """.trimIndent()
        assertTrue(WebTelemetryConfigParser.parseActiveTelemetryTypes(json).isEmpty())
    }

    @Test
    fun `telemetry type missing targets still parses but counter view returns null`() {
        val json = """
            {
                "state": "enabled",
                "settings": {
                    "telemetryTypes": {
                        "adwall": {
                            "state": "enabled",
                            "template": "counter"
                        }
                    },
                    "pixels": {}
                }
            }
        """.trimIndent()
        val config = WebTelemetryConfigParser.parse(json)
        assertEquals(1, config.telemetryTypes.size)
        assertNull(CounterTelemetryType.from(config.telemetryTypes[0]))
    }

    @Test
    fun `pixel missing period is skipped`() {
        val json = """
            {
                "state": "enabled",
                "settings": {
                    "telemetryTypes": {},
                    "pixels": {
                        "test.pixel": {
                            "jitter": 0.25,
                            "parameters": {
                                "count": {"type": "counter", "buckets": ["0-1"]}
                            }
                        }
                    }
                }
            }
        """.trimIndent()
        val config = WebTelemetryConfigParser.parse(json)
        assertTrue(config.pixels.isEmpty())
    }

    @Test
    fun `pixel missing parameters is skipped`() {
        val json = """
            {
                "state": "enabled",
                "settings": {
                    "telemetryTypes": {},
                    "pixels": {
                        "test.pixel": {
                            "period": "day",
                            "jitter": 0.25
                        }
                    }
                }
            }
        """.trimIndent()
        val config = WebTelemetryConfigParser.parse(json)
        assertTrue(config.pixels.isEmpty())
    }

    @Test
    fun `jitter defaults to 0_25 when omitted`() {
        val json = """
            {
                "state": "enabled",
                "settings": {
                    "telemetryTypes": {},
                    "pixels": {
                        "test.pixel": {
                            "period": "day",
                            "parameters": {
                                "count": {"type": "counter", "buckets": ["0+"]}
                            }
                        }
                    }
                }
            }
        """.trimIndent()
        val config = WebTelemetryConfigParser.parse(json)
        assertEquals(0.25, config.pixels[0].jitter, 0.001)
    }
}
