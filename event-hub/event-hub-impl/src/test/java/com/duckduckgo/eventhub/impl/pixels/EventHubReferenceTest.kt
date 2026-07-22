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

import com.duckduckgo.common.test.FileUtilities
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Consumes the shared privacy reference tests for EventHub pixel building
 * (reference_tests/eventhub/pixel_building_tests.json) and drives the pure
 * [RealEventHubPixelManager.buildPixelOutput] builder for each vector.
 *
 * Enrolment timestamps are provided as unix seconds (timezone-universal) and converted to the
 * America/New_York ZonedDateTime string the builder consumes, preserving the exact instant.
 */
@RunWith(Parameterized::class)
class EventHubReferenceTest(
    private val testName: String,
    private val test: JSONObject,
) {

    @Test
    fun referenceTest() {
        val config = EventHubConfigParser.parseSinglePixelConfig(PIXEL_NAME, test.getJSONObject("config").toString())
        requireNotNull(config) { "$testName: config failed to parse" }

        val periodStartMillis = test.getLong("periodStartUnixSeconds") * 1000
        val periodEndMillis = periodStartMillis + config.trigger.period.periodSeconds * 1000

        val pixelState = PixelState(
            pixelName = PIXEL_NAME,
            periodStartMillis = periodStartMillis,
            periodEndMillis = periodEndMillis,
            config = config,
            params = parseParameterState(),
            experiments = parseEnrolledExperiments(),
        )

        val output = RealEventHubPixelManager.buildPixelOutput(pixelState)

        assertEquals("$testName: expectFires", test.getBoolean("expectFires"), output.fires)
        if (!output.fires) return

        val expected = test.getJSONObject("expectParameters")
        assertEquals("$testName: parameter names", expected.keys().asSequence().toSet(), output.params.keys)

        for (name in expected.keys().asSequence()) {
            val actual = output.params.getValue(name)
            if (config.parameters[name] is ExperimentsParameterConfig) {
                // Dimensional value is a JSON object; compare structurally (key order not significant).
                assertTrue(
                    "$testName: experiments param '$name' mismatch, was $actual",
                    JSONObject(actual).similar(expected.getJSONObject(name)),
                )
            } else {
                // Counter bucket names and attributionPeriod compare as strings.
                assertEquals("$testName: param '$name' mismatch", expected.getString(name), actual)
            }
        }
    }

    private fun parseParameterState(): Map<String, ParamState> {
        val paramStates = mutableMapOf<String, ParamState>()
        val parameterState = test.optJSONObject("parameterState") ?: return paramStates
        for (name in parameterState.keys().asSequence()) {
            val entry = parameterState.getJSONObject(name)
            // Only counter state is applicable on Android (data template is not supported here).
            if (entry.has("count")) {
                paramStates[name] = ParamState(entry.getInt("count"))
            }
        }
        return paramStates
    }

    private fun parseEnrolledExperiments(): ExperimentPeriodState {
        val baseline = mutableMapOf<String, ExperimentCohort>()
        val changed = mutableSetOf<String>()
        val enrolled = test.optJSONArray("enrolledExperiments")
        if (enrolled != null) {
            for (i in 0 until enrolled.length()) {
                val entry = enrolled.getJSONObject(i)
                val name = entry.getString("name")
                if (entry.optBoolean("enrollmentChanged", false)) {
                    changed.add(name)
                } else {
                    val enrollmentDateEt = ZonedDateTime.ofInstant(
                        Instant.ofEpochSecond(entry.getLong("enrollmentUnixSeconds")),
                        ZoneId.of("America/New_York"),
                    ).toString()
                    baseline[name] = ExperimentCohort(cohort = entry.getString("cohort"), enrollmentDateET = enrollmentDateEt)
                }
            }
        }
        return ExperimentPeriodState(baseline = baseline, changed = changed)
    }

    companion object {
        private const val PIXEL_NAME = "referenceTest"
        private const val RESOURCE = "reference_tests/eventhub/pixel_building_tests.json"
        private const val PLATFORM = "android-browser"

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun testCases(): List<Array<Any>> {
            val root = JSONObject(
                FileUtilities.loadText(EventHubReferenceTest::class.java.classLoader!!, RESOURCE),
            )
            val cases = mutableListOf<Array<Any>>()
            for (setName in root.keys().asSequence()) {
                if (setName == "\$schema") continue
                val tests = root.getJSONObject(setName).getJSONArray("tests")
                for (i in 0 until tests.length()) {
                    val test = tests.getJSONObject(i)
                    if (isExcepted(test)) continue
                    cases.add(arrayOf("$setName: ${test.getString("name")}", test))
                }
            }
            return cases
        }

        private fun isExcepted(test: JSONObject): Boolean {
            val except = test.optJSONArray("exceptPlatforms") ?: return false
            return (0 until except.length()).any { except.getString(it) == PLATFORM }
        }
    }
}
