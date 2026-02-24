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

package com.duckduckgo.eventhub.impl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EventHubConfigParserTest {

    private val fullConfig = """
        {
            "state": "enabled",
            "settings": {
                "telemetry": {
                    "webTelemetry_adwallDetection_day": {
                        "state": "enabled",
                        "trigger": {
                            "period": { "days": 1 }
                        },
                        "parameters": {
                            "count": {
                                "template": "counter",
                                "source": "adwall",
                                "buckets": {
                                    "0":     {"gte": 0,  "lt": 1},
                                    "1-2":   {"gte": 1,  "lt": 3},
                                    "3-5":   {"gte": 3,  "lt": 6},
                                    "6-10":  {"gte": 6,  "lt": 11},
                                    "11-20": {"gte": 11, "lt": 21},
                                    "21-39": {"gte": 21, "lt": 40},
                                    "40+":   {"gte": 40}
                                }
                            }
                        }
                    }
                }
            }
        }
    """.trimIndent()

    @Test
    fun `full config parses pixel correctly`() {
        val config = EventHubConfigParser.parse(fullConfig)
        assertTrue(config.featureEnabled)
        assertEquals(1, config.telemetry.size)

        val pixel = config.telemetry[0]
        assertTrue(pixel.isEnabled)
        assertEquals(1, pixel.trigger.period.days)
        assertEquals(86400L, pixel.trigger.period.periodSeconds)
    }

    @Test
    fun `counter parameter with map buckets parsed correctly`() {
        val config = EventHubConfigParser.parse(fullConfig)
        val param = config.telemetry[0].parameters["count"]!!

        assertTrue(param.isCounter)
        assertEquals("adwall", param.source)
        assertEquals(7, param.buckets.size)

        val first = param.buckets["0"]!!
        assertEquals(0, first.gte)
        assertEquals(1, first.lt)

        val last = param.buckets["40+"]!!
        assertEquals(40, last.gte)
        assertNull(last.lt)
    }

    @Test
    fun `seconds period parses correctly`() {
        val json = """
            {
                "state": "enabled",
                "settings": {
                    "telemetry": {
                        "test": {
                            "state": "enabled",
                            "trigger": { "period": { "seconds": 30 } },
                            "parameters": {
                                "c": {
                                    "template": "counter",
                                    "source": "e",
                                    "buckets": {"0+": {"gte": 0}}
                                }
                            }
                        }
                    }
                }
            }
        """.trimIndent()
        val config = EventHubConfigParser.parse(json)
        assertEquals(30L, config.telemetry[0].trigger.period.periodSeconds)
    }

    @Test
    fun `disabled feature returns empty`() {
        val json = """{"state": "disabled", "settings": {"telemetry": {}}}"""
        assertFalse(EventHubConfigParser.parse(json).featureEnabled)
    }

    @Test
    fun `empty json returns empty config`() {
        val config = EventHubConfigParser.parse("{}")
        assertFalse(config.featureEnabled)
        assertTrue(config.telemetry.isEmpty())
    }

    @Test
    fun `pixel missing state is skipped`() {
        val json = """
            {
                "state": "enabled",
                "settings": {
                    "telemetry": {
                        "test": {
                            "trigger": { "period": { "days": 1 } },
                            "parameters": {
                                "c": {
                                    "template": "counter", "source": "e",
                                    "buckets": {"0+": {"gte": 0}}
                                }
                            }
                        }
                    }
                }
            }
        """.trimIndent()
        assertTrue(EventHubConfigParser.parse(json).telemetry.isEmpty())
    }

    @Test
    fun `bucket missing gte is skipped`() {
        val json = """
            {
                "state": "enabled",
                "settings": {
                    "telemetry": {
                        "test": {
                            "state": "enabled",
                            "trigger": { "period": { "days": 1 } },
                            "parameters": {
                                "c": {
                                    "template": "counter", "source": "e",
                                    "buckets": {"bad": {"lt": 5}}
                                }
                            }
                        }
                    }
                }
            }
        """.trimIndent()
        assertTrue(EventHubConfigParser.parse(json).telemetry.isEmpty())
    }

    @Test
    fun `malformed JSON returns EMPTY`() {
        val config = EventHubConfigParser.parse("not valid json")
        assertFalse(config.featureEnabled)
        assertTrue(config.telemetry.isEmpty())
    }

    @Test
    fun `missing settings key returns empty telemetry`() {
        val json = """{"state": "enabled"}"""
        val config = EventHubConfigParser.parse(json)
        assertTrue(config.featureEnabled)
        assertTrue(config.telemetry.isEmpty())
    }

    @Test
    fun `missing telemetry key returns empty telemetry`() {
        val json = """{"state": "enabled", "settings": {}}"""
        val config = EventHubConfigParser.parse(json)
        assertTrue(config.featureEnabled)
        assertTrue(config.telemetry.isEmpty())
    }

    @Test
    fun `all-zero period returns no telemetry`() {
        val json = """
            {
                "state": "enabled",
                "settings": {
                    "telemetry": {
                        "test": {
                            "state": "enabled",
                            "trigger": { "period": { "seconds": 0, "minutes": 0, "hours": 0, "days": 0 } },
                            "parameters": {
                                "c": {
                                    "template": "counter",
                                    "source": "e",
                                    "buckets": {"0+": {"gte": 0}}
                                }
                            }
                        }
                    }
                }
            }
        """.trimIndent()
        assertTrue(EventHubConfigParser.parse(json).telemetry.isEmpty())
    }

    @Test
    fun `non-counter template is skipped`() {
        val json = """
            {
                "state": "enabled",
                "settings": {
                    "telemetry": {
                        "test": {
                            "state": "enabled",
                            "trigger": { "period": { "days": 1 } },
                            "parameters": {
                                "c": {
                                    "template": "unknown_template",
                                    "source": "e"
                                }
                            }
                        }
                    }
                }
            }
        """.trimIndent()
        assertTrue(EventHubConfigParser.parse(json).telemetry.isEmpty())
    }

    @Test
    fun `parseSinglePixelConfig with malformed JSON returns null`() {
        assertNull(EventHubConfigParser.parseSinglePixelConfig("test", "not json"))
    }

    @Test
    fun `parseSinglePixelConfig with empty object returns null`() {
        assertNull(EventHubConfigParser.parseSinglePixelConfig("test", "{}"))
    }

    @Test
    fun `multi-unit period combines correctly`() {
        val json = """
            {
                "state": "enabled",
                "settings": {
                    "telemetry": {
                        "test": {
                            "state": "enabled",
                            "trigger": { "period": { "hours": 1, "minutes": 30 } },
                            "parameters": {
                                "c": {
                                    "template": "counter",
                                    "source": "e",
                                    "buckets": {"0+": {"gte": 0}}
                                }
                            }
                        }
                    }
                }
            }
        """.trimIndent()
        val config = EventHubConfigParser.parse(json)
        assertEquals(1, config.telemetry.size)
        assertEquals(5400L, config.telemetry[0].trigger.period.periodSeconds) // 1h30m = 5400s
    }
}
