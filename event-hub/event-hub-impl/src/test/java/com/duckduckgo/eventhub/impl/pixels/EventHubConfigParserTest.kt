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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EventHubConfigParserTest {

    private val settingsJson = """
        {
            "telemetry": {
                "webTelemetry_testPixel1": {
                    "state": "enabled",
                    "trigger": {
                        "period": { "days": 1 }
                    },
                    "parameters": {
                        "count": {
                            "template": "counter",
                            "source": "test",
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
    """.trimIndent()

    @Test
    fun `settings json parses pixel correctly`() {
        val telemetry = EventHubConfigParser.parseTelemetry(settingsJson)
        assertEquals(1, telemetry.size)

        val pixel = telemetry[0]
        assertTrue(pixel.isEnabled)
        assertEquals(1, pixel.period.days)
        assertEquals(86400L, pixel.period.periodSeconds)
    }

    @Test
    fun `counter parameter with map buckets parsed correctly`() {
        val telemetry = EventHubConfigParser.parseTelemetry(settingsJson)
        val param = telemetry[0].counter("count")

        assertEquals("test", param.source)
        assertEquals(7, param.buckets.size)

        val first = param.buckets["0"]!!
        assertEquals(0, first.gte)
        assertEquals(1, first.lt)

        val last = param.buckets["40+"]!!
        assertEquals(40, last.gte)
        assertNull(last.lt)
    }

    @Test
    fun `buckets keep the order they appear in the config`() {
        val json = """
            {
                "telemetry": {
                    "webTelemetry_period": {
                        "state": "enabled",
                        "trigger": { "period": { "days": 1 } },
                        "parameters": {
                            "c": {
                                "template": "counter", "source": "e",
                                "buckets": {
                                    "40+": {"gte": 40},
                                    "0":   {"gte": 0, "lt": 1},
                                    "3-5": {"gte": 3, "lt": 6},
                                    "1-2": {"gte": 1, "lt": 3},
                                    "6-39": {"gte": 6, "lt": 40}
                                }
                            }
                        }
                    }
                }
            }
        """.trimIndent()

        val buckets = EventHubConfigParser.parseTelemetry(json).single().counter("c").buckets

        assertEquals(listOf("40+", "0", "3-5", "1-2", "6-39"), buckets.keys.toList())
    }

    @Test
    fun `seconds period parses correctly`() {
        val json = """
            {
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
        """.trimIndent()
        val telemetry = EventHubConfigParser.parseTelemetry(json)
        assertEquals(30L, telemetry[0].period.periodSeconds)
    }

    @Test
    fun `empty json returns empty telemetry`() {
        val telemetry = EventHubConfigParser.parseTelemetry("{}")
        assertTrue(telemetry.isEmpty())
    }

    @Test
    fun `pixel missing state is skipped`() {
        val json = """
            {
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
        """.trimIndent()
        assertTrue(EventHubConfigParser.parseTelemetry(json).isEmpty())
    }

    @Test
    fun `bucket missing gte is skipped`() {
        val json = """
            {
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
        """.trimIndent()
        assertTrue(EventHubConfigParser.parseTelemetry(json).isEmpty())
    }

    @Test
    fun `malformed JSON returns empty`() {
        val telemetry = EventHubConfigParser.parseTelemetry("not valid json")
        assertTrue(telemetry.isEmpty())
    }

    @Test
    fun `missing telemetry key returns empty telemetry`() {
        val json = """{"other": {}}"""
        val telemetry = EventHubConfigParser.parseTelemetry(json)
        assertTrue(telemetry.isEmpty())
    }

    @Test
    fun `all-zero period returns no telemetry`() {
        val json = """
            {
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
        """.trimIndent()
        assertTrue(EventHubConfigParser.parseTelemetry(json).isEmpty())
    }

    @Test
    fun `negative period that sums to zero or below returns no telemetry`() {
        val json = """
            {
                "telemetry": {
                    "test": {
                        "state": "enabled",
                        "trigger": { "period": { "hours": 1, "minutes": -60 } },
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
        """.trimIndent()
        assertTrue(EventHubConfigParser.parseTelemetry(json).isEmpty())
    }

    @Test
    fun `negative period that sums below zero returns no telemetry`() {
        val json = """
            {
                "telemetry": {
                    "test": {
                        "state": "enabled",
                        "trigger": { "period": { "seconds": -10 } },
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
        """.trimIndent()
        assertTrue(EventHubConfigParser.parseTelemetry(json).isEmpty())
    }

    @Test
    fun `non-counter template is skipped`() {
        val json = """
            {
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
        """.trimIndent()
        assertTrue(EventHubConfigParser.parseTelemetry(json).isEmpty())
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
    fun `serializePixelConfig produces valid JSON that round-trips`() {
        val configs = EventHubConfigParser.parseTelemetry(settingsJson)
        val original = configs[0]

        val json = EventHubConfigParser.serializePixelConfig(original)
        assertNotNull(json)

        val restored = EventHubConfigParser.parseSinglePixelConfig(original.name, json!!)
        assertNotNull(restored)
        assertEquals(original.name, restored!!.name)
        assertEquals(original.state, restored.state)
        assertEquals(original.period.periodSeconds, restored.period.periodSeconds)
        assertEquals(original.parameters.size, restored.parameters.size)
        assertEquals(original.counter("count").source, restored.counter("count").source)
        assertEquals(original.counter("count").buckets.size, restored.counter("count").buckets.size)
    }

    @Test
    fun `serializePixelConfig returns non-null for valid config`() {
        val config = TelemetryPixelConfig(
            name = "test",
            state = "enabled",
            trigger = TelemetryTriggerConfig.Period(TelemetryPeriodConfig(days = 1)),
            parameters = mapOf(
                "c" to TelemetryParameterConfig.Counter(
                    source = "e",
                    buckets = linkedMapOf("0+" to BucketConfig(gte = 0, lt = null)),
                ),
            ),
        )
        assertNotNull(EventHubConfigParser.serializePixelConfig(config))
    }

    @Test
    fun `multi-unit period combines correctly`() {
        val json = """
            {
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
        """.trimIndent()
        val telemetry = EventHubConfigParser.parseTelemetry(json)
        assertEquals(1, telemetry.size)
        assertEquals(5400L, telemetry[0].period.periodSeconds) // 1h30m = 5400s
    }

    @Test
    fun `immediate_v2 entry is parsed alongside a period entry`() {
        val json = """
            {
                "telemetry": {
                    "webTelemetry_immediate": {
                        "state": "enabled",
                        "trigger": { "type": "immediate_v2", "source": "captcha" },
                        "parameters": {}
                    },
                    "webTelemetry_period": $periodPixelJson
                }
            }
        """.trimIndent()

        val telemetry = EventHubConfigParser.parseTelemetry(json)

        assertEquals(listOf("webTelemetry_immediate", "webTelemetry_period"), telemetry.map { it.name })
        assertEquals(TelemetryTriggerConfig.Immediate("captcha"), telemetry[0].trigger)
        assertTrue(telemetry[0].parameters.isEmpty())
    }

    @Test
    fun `immediate_v2 entry without a source is skipped`() {
        val json = """
            {
                "telemetry": {
                    "webTelemetry_noSource": {
                        "state": "enabled",
                        "trigger": { "type": "immediate_v2" },
                        "parameters": {}
                    },
                    "webTelemetry_emptySource": {
                        "state": "enabled",
                        "trigger": { "type": "immediate_v2", "source": "" },
                        "parameters": {}
                    },
                    "webTelemetry_period": $periodPixelJson
                }
            }
        """.trimIndent()

        val telemetry = EventHubConfigParser.parseTelemetry(json)

        assertEquals(listOf("webTelemetry_period"), telemetry.map { it.name })
    }

    @Test
    fun `data parameters are parsed with their key and optional source`() {
        val json = """
            {
                "telemetry": {
                    "webTelemetry_immediate": {
                        "state": "enabled",
                        "trigger": { "type": "immediate_v2", "source": "adwall" },
                        "parameters": { "reason": { "template": "data", "dataKey": "reason" } }
                    },
                    "webTelemetry_period": {
                        "state": "enabled",
                        "trigger": { "period": { "days": 1 } },
                        "parameters": {
                            "c": { "template": "counter", "source": "adwall", "buckets": {"0+": {"gte": 0}} },
                            "reason": { "template": "data", "source": "adwall", "dataKey": "reason" }
                        }
                    }
                }
            }
        """.trimIndent()

        val telemetry = EventHubConfigParser.parseTelemetry(json).associateBy { it.name }

        assertEquals(TelemetryParameterConfig.Data(dataKey = "reason"), telemetry.getValue("webTelemetry_immediate").parameters["reason"])
        assertEquals(
            TelemetryParameterConfig.Data(dataKey = "reason", source = "adwall"),
            telemetry.getValue("webTelemetry_period").parameters["reason"],
        )
    }

    @Test
    fun `data parameter without a dataKey is dropped`() {
        val json = """
            {
                "telemetry": {
                    "webTelemetry_immediate": {
                        "state": "enabled",
                        "trigger": { "type": "immediate_v2", "source": "adwall" },
                        "parameters": {
                            "missing": { "template": "data" },
                            "empty": { "template": "data", "dataKey": "" },
                            "reason": { "template": "data", "dataKey": "reason" }
                        }
                    }
                }
            }
        """.trimIndent()

        val telemetry = EventHubConfigParser.parseTelemetry(json)

        assertEquals(setOf("reason"), telemetry.single().parameters.keys)
    }

    @Test
    fun `period entry whose only parameter is data is skipped`() {
        val json = """
            {
                "telemetry": {
                    "webTelemetry_dataOnly": {
                        "state": "enabled",
                        "trigger": { "period": { "days": 1 } },
                        "parameters": { "reason": { "template": "data", "source": "adwall", "dataKey": "reason" } }
                    },
                    "webTelemetry_period": $periodPixelJson
                }
            }
        """.trimIndent()

        val telemetry = EventHubConfigParser.parseTelemetry(json)

        assertEquals(listOf("webTelemetry_period"), telemetry.map { it.name })
    }

    @Test
    fun `period config with a data parameter round-trips through serialization`() {
        val config = TelemetryPixelConfig(
            name = "test",
            state = "enabled",
            trigger = TelemetryTriggerConfig.Period(TelemetryPeriodConfig(days = 1)),
            parameters = mapOf(
                "c" to TelemetryParameterConfig.Counter(source = "e", buckets = linkedMapOf("0+" to BucketConfig(gte = 0, lt = null))),
                "reason" to TelemetryParameterConfig.Data(dataKey = "reason", source = "e"),
            ),
        )

        val restored = EventHubConfigParser.parseSinglePixelConfig("test", EventHubConfigParser.serializePixelConfig(config)!!)

        assertEquals(config, restored)
    }

    @Test
    fun `immediate, immediate_v3 and unknown trigger types are skipped`() {
        val json = """
            {
                "telemetry": {
                    "webTelemetry_immediate": {
                        "state": "enabled",
                        "trigger": { "type": "immediate", "source": "captcha", "period": { "days": 1 } },
                        "parameters": $counterParametersJson
                    },
                    "webTelemetry_immediateV3": {
                        "state": "enabled",
                        "trigger": { "type": "immediate_v3", "source": "captcha", "cooldown": { "days": 1 } },
                        "parameters": $counterParametersJson
                    },
                    "webTelemetry_unknown": {
                        "state": "enabled",
                        "trigger": { "type": "somethingNew", "period": { "days": 1 } },
                        "parameters": $counterParametersJson
                    },
                    "webTelemetry_period": $periodPixelJson
                }
            }
        """.trimIndent()

        val telemetry = EventHubConfigParser.parseTelemetry(json)

        assertEquals(listOf("webTelemetry_period"), telemetry.map { it.name })
    }

    @Test
    fun `explicit period trigger type is parsed`() {
        val json = """
            {
                "telemetry": {
                    "webTelemetry_period": {
                        "state": "enabled",
                        "trigger": { "type": "period", "period": { "days": 1 } },
                        "parameters": $counterParametersJson
                    }
                }
            }
        """.trimIndent()

        val telemetry = EventHubConfigParser.parseTelemetry(json)

        assertEquals(1, telemetry.size)
        assertEquals(86400L, telemetry[0].period.periodSeconds)
    }

    @Test
    fun `malformed entry does not affect sibling entries`() {
        val json = """
            {
                "telemetry": {
                    "webTelemetry_missingState": {
                        "trigger": { "period": { "days": 1 } },
                        "parameters": $counterParametersJson
                    },
                    "webTelemetry_badBucket": {
                        "state": "enabled",
                        "trigger": { "period": { "days": 1 } },
                        "parameters": {
                            "c": { "template": "counter", "source": "e", "buckets": {"bad": {"lt": 5}} }
                        }
                    },
                    "webTelemetry_notAnObject": 42,
                    "webTelemetry_period": $periodPixelJson
                }
            }
        """.trimIndent()

        val telemetry = EventHubConfigParser.parseTelemetry(json)

        assertEquals(listOf("webTelemetry_period"), telemetry.map { it.name })
    }

    @Test
    fun `null entry is skipped without affecting sibling entries`() {
        val json = """
            {
                "telemetry": {
                    "webTelemetry_null": null,
                    "webTelemetry_period": $periodPixelJson
                }
            }
        """.trimIndent()

        val telemetry = EventHubConfigParser.parseTelemetry(json)

        assertEquals(listOf("webTelemetry_period"), telemetry.map { it.name })
    }

    private val counterParametersJson = """
        {
            "c": {
                "template": "counter",
                "source": "e",
                "buckets": {"0+": {"gte": 0}}
            }
        }
    """.trimIndent()

    private val periodPixelJson = """
        {
            "state": "enabled",
            "trigger": { "period": { "days": 1 } },
            "parameters": $counterParametersJson
        }
    """.trimIndent()
}
