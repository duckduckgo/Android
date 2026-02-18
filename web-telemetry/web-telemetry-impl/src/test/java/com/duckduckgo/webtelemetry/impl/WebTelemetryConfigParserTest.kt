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
import org.junit.Assert.assertTrue
import org.junit.Test

class EventHubConfigParserTest {

    private val fullConfig = """
        {
            "state": "enabled",
            "settings": {
                "telemetry": {
                    "webTelemetry.adwalls.day": {
                        "state": "enabled",
                        "trigger": {
                            "period": { "days": 1, "maxStaggerMins": 180 }
                        },
                        "parameters": {
                            "adwallCount": {
                                "template": "counter",
                                "source": "adwall",
                                "buckets": ["0-1", "2-3", "4-5", "6-10", "11-20", "21-39", "40+"]
                            }
                        }
                    },
                    "webTelemetry.adwalls.week": {
                        "state": "enabled",
                        "trigger": {
                            "period": { "days": 7 }
                        },
                        "parameters": {
                            "adwallCount": {
                                "template": "counter",
                                "source": "adwall",
                                "buckets": ["0-5", "6-20", "21-50", "51+"]
                            }
                        }
                    }
                }
            }
        }
    """.trimIndent()

    @Test
    fun `full config parses telemetry pixels correctly`() {
        val config = EventHubConfigParser.parse(fullConfig)
        assertTrue(config.featureEnabled)
        assertEquals(2, config.telemetry.size)
    }

    @Test
    fun `daily pixel parsed with correct trigger and parameters`() {
        val config = EventHubConfigParser.parse(fullConfig)
        val daily = config.telemetry.find { it.name == "webTelemetry.adwalls.day" }!!

        assertTrue(daily.isEnabled)
        assertEquals(1, daily.trigger.period.days)
        assertEquals(180, daily.trigger.period.maxStaggerMins)
        assertEquals(86400L, daily.trigger.period.periodSeconds)
        assertEquals(1, daily.parameters.size)

        val param = daily.parameters["adwallCount"]!!
        assertTrue(param.isCounter)
        assertEquals("adwall", param.source)
        assertEquals(7, param.buckets.size)
    }

    @Test
    fun `weekly pixel parsed with hours period`() {
        val json = """
            {
                "state": "enabled",
                "settings": {
                    "telemetry": {
                        "hourly.pixel": {
                            "state": "enabled",
                            "trigger": { "period": { "hours": 6 } },
                            "parameters": {
                                "count": { "template": "counter", "source": "evt", "buckets": ["0+"] }
                            }
                        }
                    }
                }
            }
        """.trimIndent()
        val config = EventHubConfigParser.parse(json)
        val pixel = config.telemetry[0]
        assertEquals(6, pixel.trigger.period.hours)
        assertEquals(21600L, pixel.trigger.period.periodSeconds)
    }

    @Test
    fun `disabled feature returns empty`() {
        val json = """{"state": "disabled", "settings": {"telemetry": {}}}"""
        val config = EventHubConfigParser.parse(json)
        assertFalse(config.featureEnabled)
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
                            "parameters": { "c": { "template": "counter", "source": "e", "buckets": ["0+"] } }
                        }
                    }
                }
            }
        """.trimIndent()
        assertTrue(EventHubConfigParser.parse(json).telemetry.isEmpty())
    }

    @Test
    fun `pixel with zero period is skipped`() {
        val json = """
            {
                "state": "enabled",
                "settings": {
                    "telemetry": {
                        "test": {
                            "state": "enabled",
                            "trigger": { "period": { "days": 0 } },
                            "parameters": { "c": { "template": "counter", "source": "e", "buckets": ["0+"] } }
                        }
                    }
                }
            }
        """.trimIndent()
        assertTrue(EventHubConfigParser.parse(json).telemetry.isEmpty())
    }

    @Test
    fun `parameter missing template is skipped`() {
        val json = """
            {
                "state": "enabled",
                "settings": {
                    "telemetry": {
                        "test": {
                            "state": "enabled",
                            "trigger": { "period": { "days": 1 } },
                            "parameters": { "c": { "source": "e", "buckets": ["0+"] } }
                        }
                    }
                }
            }
        """.trimIndent()
        assertTrue(EventHubConfigParser.parse(json).telemetry.isEmpty())
    }

    @Test
    fun `parameter missing source is skipped`() {
        val json = """
            {
                "state": "enabled",
                "settings": {
                    "telemetry": {
                        "test": {
                            "state": "enabled",
                            "trigger": { "period": { "days": 1 } },
                            "parameters": { "c": { "template": "counter", "buckets": ["0+"] } }
                        }
                    }
                }
            }
        """.trimIndent()
        assertTrue(EventHubConfigParser.parse(json).telemetry.isEmpty())
    }

    @Test
    fun `unknown template is skipped`() {
        val json = """
            {
                "state": "enabled",
                "settings": {
                    "telemetry": {
                        "test": {
                            "state": "enabled",
                            "trigger": { "period": { "days": 1 } },
                            "parameters": { "c": { "template": "gauge", "source": "e" } }
                        }
                    }
                }
            }
        """.trimIndent()
        assertTrue(EventHubConfigParser.parse(json).telemetry.isEmpty())
    }
}
