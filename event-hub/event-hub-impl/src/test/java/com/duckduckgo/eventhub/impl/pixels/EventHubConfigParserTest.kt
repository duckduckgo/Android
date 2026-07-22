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
        assertEquals(1, pixel.trigger.period.days)
        assertEquals(86400L, pixel.trigger.period.periodSeconds)
    }

    @Test
    fun `counter parameter with map buckets parsed correctly`() {
        val telemetry = EventHubConfigParser.parseTelemetry(settingsJson)
        val param = telemetry[0].parameters["count"]!!

        assertTrue(param is CounterParameterConfig)
        val counter = param as CounterParameterConfig
        assertEquals("test", counter.source)
        assertEquals(7, counter.buckets.size)

        val first = counter.buckets["0"]!!
        assertEquals(0, first.gte)
        assertEquals(1, first.lt)

        val last = counter.buckets["40+"]!!
        assertEquals(40, last.gte)
        assertNull(last.lt)
    }

    @Test
    fun `experiments parameter with matchExperiments and enrollmentBuckets parsed correctly`() {
        val json = """
            {
                "telemetry": {
                    "webTelemetry_adwalls_week": {
                        "state": "enabled",
                        "trigger": { "period": { "days": 7 } },
                        "parameters": {
                            "blocklistExperiments": {
                                "template": "experiments",
                                "matchExperiments": "^tdsNextExperiment",
                                "enrollmentBuckets": {
                                    "0":   {"gte": 0,      "lt": 86400},
                                    "1-2": {"gte": 86400,  "lt": 259200},
                                    "3+":  {"gte": 259200}
                                }
                            }
                        }
                    }
                }
            }
        """.trimIndent()

        val param = EventHubConfigParser.parseTelemetry(json)[0].parameters["blocklistExperiments"]!!
        assertTrue(param is ExperimentsParameterConfig)
        val experiments = param as ExperimentsParameterConfig
        assertEquals("^tdsNextExperiment", experiments.matchExperiments)
        assertEquals(3, experiments.enrollmentBuckets!!.size)
        assertEquals(259200L, experiments.enrollmentBuckets!!["3+"]!!.gte)
        assertNull(experiments.enrollmentBuckets!!["3+"]!!.lt)
    }

    @Test
    fun `experiments parameter without optional keys parses to match-all with no buckets`() {
        val json = """
            {
                "telemetry": {
                    "webTelemetry_experiments": {
                        "state": "enabled",
                        "trigger": { "period": { "days": 1 } },
                        "parameters": {
                            "experiments": { "template": "experiments" }
                        }
                    }
                }
            }
        """.trimIndent()

        val param = EventHubConfigParser.parseTelemetry(json)[0].parameters["experiments"]!!
        assertTrue(param is ExperimentsParameterConfig)
        val experiments = param as ExperimentsParameterConfig
        assertNull(experiments.matchExperiments)
        assertNull(experiments.enrollmentBuckets)
    }

    @Test
    fun `experiments parameter round-trips through serialize and parse`() {
        val json = """
            {
                "telemetry": {
                    "webTelemetry_experiments": {
                        "state": "enabled",
                        "trigger": { "period": { "days": 7 } },
                        "parameters": {
                            "exp": {
                                "template": "experiments",
                                "matchExperiments": "^tdsNextExperiment",
                                "enrollmentBuckets": { "0": {"gte": 0, "lt": 86400}, "1+": {"gte": 86400} }
                            }
                        }
                    }
                }
            }
        """.trimIndent()

        val original = EventHubConfigParser.parseTelemetry(json)[0]
        val serialized = EventHubConfigParser.serializePixelConfig(original)!!
        val restored = EventHubConfigParser.parseSinglePixelConfig(original.name, serialized)!!

        val originalExp = original.parameters["exp"] as ExperimentsParameterConfig
        val restoredExp = restored.parameters["exp"] as ExperimentsParameterConfig
        assertEquals(originalExp.matchExperiments, restoredExp.matchExperiments)
        assertEquals(originalExp.enrollmentBuckets, restoredExp.enrollmentBuckets)
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
        assertEquals(30L, telemetry[0].trigger.period.periodSeconds)
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
        assertEquals(original.trigger.period.periodSeconds, restored.trigger.period.periodSeconds)
        assertEquals(original.parameters.size, restored.parameters.size)
        val originalCounter = original.parameters["count"] as CounterParameterConfig
        val restoredCounter = restored.parameters["count"] as CounterParameterConfig
        assertEquals(originalCounter.source, restoredCounter.source)
        assertEquals(originalCounter.buckets.size, restoredCounter.buckets.size)
    }

    @Test
    fun `serializePixelConfig returns non-null for valid config`() {
        val config = TelemetryPixelConfig(
            name = "test",
            state = "enabled",
            trigger = TelemetryTriggerConfig(period = TelemetryPeriodConfig(days = 1)),
            parameters = mapOf(
                "c" to CounterParameterConfig(
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
        assertEquals(5400L, telemetry[0].trigger.period.periodSeconds) // 1h30m = 5400s
    }
}
