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

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class ExperimentParamBuilderTest {

    private val periodEndMillis = 10_000_000_000L

    private fun etDate(millis: Long): String =
        ZonedDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.of("America/New_York")).toString()

    private fun enrollmentBuckets() = linkedMapOf(
        "0" to EnrollmentBucketConfig(gte = 0, lt = 86_400),
        "1-2" to EnrollmentBucketConfig(gte = 86_400, lt = 259_200),
        "3+" to EnrollmentBucketConfig(gte = 259_200, lt = null),
    )

    @Test
    fun `no matching experiments produces empty object`() {
        val result = ExperimentParamBuilder.build(
            periodState = ExperimentPeriodState(baseline = emptyMap(), changed = emptySet()),
            config = ExperimentsParameterConfig(matchExperiments = null, enrollmentBuckets = null),
            periodEndMillis = periodEndMillis,
        )
        assertEquals("{}", result)
    }

    @Test
    fun `stable enrollment without buckets emits cohort only`() {
        val result = ExperimentParamBuilder.build(
            periodState = ExperimentPeriodState(
                baseline = mapOf("tdsNextExperiment008" to ExperimentCohort("treatment", etDate(periodEndMillis - 86_400_000L))),
                changed = emptySet(),
            ),
            config = ExperimentsParameterConfig(matchExperiments = null, enrollmentBuckets = null),
            periodEndMillis = periodEndMillis,
        )
        assertEquals("""{"tdsNextExperiment008":{"cohort":"treatment"}}""", result)
    }

    // Timezone/precision guard: enrolment is stored as an America/New_York ZonedDateTime string, and
    // other pixel code truncates it to ET day. Enrolment duration MUST be computed from the full
    // parsed instant, not a day-truncated value — otherwise sub-day buckets would be wrong.
    private fun hourBuckets() = linkedMapOf(
        "0-1h" to EnrollmentBucketConfig(gte = 0, lt = 3_600),
        "1-3h" to EnrollmentBucketConfig(gte = 3_600, lt = 10_800),
        "3h+" to EnrollmentBucketConfig(gte = 10_800, lt = null),
    )

    @Test
    fun `sub-day enrollment duration is computed from the full instant not the ET day`() {
        // Enrolled 2 hours before period end -> 7200s -> "1-3h". A day-truncated instant would not.
        val enrolledMillis = periodEndMillis - (2L * 3_600 * 1_000)
        val result = ExperimentParamBuilder.build(
            periodState = ExperimentPeriodState(
                baseline = mapOf("exp" to ExperimentCohort("treatment", etDate(enrolledMillis))),
                changed = emptySet(),
            ),
            config = ExperimentsParameterConfig(matchExperiments = null, enrollmentBuckets = hourBuckets()),
            periodEndMillis = periodEndMillis,
        )
        assertEquals("""{"exp":{"cohort":"treatment","enrollmentBucket":"1-3h"}}""", result)
    }

    @Test
    fun `enrollment just under an hour lands in the first sub-day bucket`() {
        // Enrolled 59 minutes before period end -> 3540s -> "0-1h" (proves minute-level precision).
        val enrolledMillis = periodEndMillis - (59L * 60 * 1_000)
        val result = ExperimentParamBuilder.build(
            periodState = ExperimentPeriodState(
                baseline = mapOf("exp" to ExperimentCohort("control", etDate(enrolledMillis))),
                changed = emptySet(),
            ),
            config = ExperimentsParameterConfig(matchExperiments = null, enrollmentBuckets = hourBuckets()),
            periodEndMillis = periodEndMillis,
        )
        assertEquals("""{"exp":{"cohort":"control","enrollmentBucket":"0-1h"}}""", result)
    }

    @Test
    fun `stable enrollment with buckets emits enrollmentBucket anchored to period end`() {
        // Enrolled 2 days before period end -> 172800s -> bucket "1-2".
        val enrolledMillis = periodEndMillis - (2L * 86_400 * 1000)
        val result = ExperimentParamBuilder.build(
            periodState = ExperimentPeriodState(
                baseline = mapOf("tdsNextExperiment008" to ExperimentCohort("control", etDate(enrolledMillis))),
                changed = emptySet(),
            ),
            config = ExperimentsParameterConfig(matchExperiments = null, enrollmentBuckets = enrollmentBuckets()),
            periodEndMillis = periodEndMillis,
        )
        assertEquals("""{"tdsNextExperiment008":{"cohort":"control","enrollmentBucket":"1-2"}}""", result)
    }

    @Test
    fun `partial enrollment emits enrollmentChanged only`() {
        val result = ExperimentParamBuilder.build(
            periodState = ExperimentPeriodState(
                baseline = mapOf("tdsNextExperiment008" to ExperimentCohort("treatment", etDate(periodEndMillis))),
                changed = setOf("tdsNextExperiment008"),
            ),
            config = ExperimentsParameterConfig(matchExperiments = null, enrollmentBuckets = enrollmentBuckets()),
            periodEndMillis = periodEndMillis,
        )
        assertEquals("""{"tdsNextExperiment008":{"enrollmentChanged":true}}""", result)
    }

    @Test
    fun `experiment joined mid-period (not in baseline) is partial`() {
        val result = ExperimentParamBuilder.build(
            periodState = ExperimentPeriodState(baseline = emptyMap(), changed = setOf("cssExperiment01")),
            config = ExperimentsParameterConfig(matchExperiments = null, enrollmentBuckets = null),
            periodEndMillis = periodEndMillis,
        )
        assertEquals("""{"cssExperiment01":{"enrollmentChanged":true}}""", result)
    }

    @Test
    fun `matchExperiments filters non-matching experiments`() {
        val result = ExperimentParamBuilder.build(
            periodState = ExperimentPeriodState(
                baseline = mapOf(
                    "tdsNextExperiment008" to ExperimentCohort("treatment", null),
                    "cssExperiment01" to ExperimentCohort("control", null),
                ),
                changed = emptySet(),
            ),
            config = ExperimentsParameterConfig(matchExperiments = "^tdsNextExperiment", enrollmentBuckets = null),
            periodEndMillis = periodEndMillis,
        )
        assertEquals("""{"tdsNextExperiment008":{"cohort":"treatment"}}""", result)
    }

    @Test
    fun `multiple experiments serialized deterministically in sorted order`() {
        val result = ExperimentParamBuilder.build(
            periodState = ExperimentPeriodState(
                baseline = mapOf(
                    "expB" to ExperimentCohort("treatment", null),
                    "expA" to ExperimentCohort("control", null),
                ),
                changed = setOf("expC"),
            ),
            config = ExperimentsParameterConfig(matchExperiments = null, enrollmentBuckets = null),
            periodEndMillis = periodEndMillis,
        )
        assertEquals(
            """{"expA":{"cohort":"control"},"expB":{"cohort":"treatment"},"expC":{"enrollmentChanged":true}}""",
            result,
        )
    }

    @Test
    fun `stable enrollment with buckets but missing enrollment date omits bucket`() {
        val result = ExperimentParamBuilder.build(
            periodState = ExperimentPeriodState(
                baseline = mapOf("tdsNextExperiment008" to ExperimentCohort("treatment", null)),
                changed = emptySet(),
            ),
            config = ExperimentsParameterConfig(matchExperiments = null, enrollmentBuckets = enrollmentBuckets()),
            periodEndMillis = periodEndMillis,
        )
        assertEquals("""{"tdsNextExperiment008":{"cohort":"treatment"}}""", result)
    }
}
