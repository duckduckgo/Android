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
                                "buckets": [
                                    {"minInclusive": 0,  "maxExclusive": 1,  "name": "0"},
                                    {"minInclusive": 1,  "maxExclusive": 3,  "name": "1-2"},
                                    {"minInclusive": 3,  "maxExclusive": 6,  "name": "3-5"},
                                    {"minInclusive": 6,  "maxExclusive": 11, "name": "6-10"},
                                    {"minInclusive": 11, "maxExclusive": 21, "name": "11-20"},
                                    {"minInclusive": 21, "maxExclusive": 40, "name": "21-39"},
                                    {"minInclusive": 40, "name": "40+"}
                                ]
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
    fun `counter parameter with object buckets parsed correctly`() {
        val config = EventHubConfigParser.parse(fullConfig)
        val param = config.telemetry[0].parameters["count"]!!

        assertTrue(param.isCounter)
        assertEquals("adwall", param.source)
        assertEquals(7, param.buckets.size)

        val first = param.buckets[0]
        assertEquals(0, first.minInclusive)
        assertEquals(1, first.maxExclusive)
        assertEquals("0", first.name)

        val last = param.buckets[6]
        assertEquals(40, last.minInclusive)
        assertNull(last.maxExclusive)
        assertEquals("40+", last.name)
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
                                    "buckets": [{"minInclusive": 0, "name": "0+"}]
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
                                    "buckets": [{"minInclusive": 0, "name": "0+"}]
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
    fun `bucket missing minInclusive is skipped`() {
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
                                    "buckets": [{"name": "bad"}]
                                }
                            }
                        }
                    }
                }
            }
        """.trimIndent()
        assertTrue(EventHubConfigParser.parse(json).telemetry.isEmpty())
    }
}
