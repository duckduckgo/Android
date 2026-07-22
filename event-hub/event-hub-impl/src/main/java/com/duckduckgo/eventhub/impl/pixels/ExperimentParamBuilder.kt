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

import org.json.JSONObject
import java.time.ZonedDateTime

/**
 * Builds the value of an `experiments` pixel parameter from the period's enrolment state.
 *
 * Output is a compact JSON object keyed by experiment name. For each matching experiment:
 *  - stable enrolment  -> {"cohort": <name>} (plus "enrollmentBucket" when configured)
 *  - partial enrolment -> {"enrollmentChanged": true}
 *
 * Enrolment age is anchored to [periodEndMillis] (never the pixel-fire time), consistent with the
 * rest of the period semantics. Returns "{}" when no matching experiments were present.
 */
object ExperimentParamBuilder {

    fun build(
        periodState: ExperimentPeriodState,
        config: ExperimentsParameterConfig,
        periodEndMillis: Long,
    ): String {
        val regex = config.matchExperiments?.let { runCatching { Regex(it) }.getOrNull() }
        fun matches(name: String): Boolean = config.matchExperiments == null || regex?.containsMatchIn(name) == true

        // Union of experiments enrolled at period start and any that changed (e.g. joined) during it.
        // Sorted so the serialized value is deterministic (org.json.JSONObject does not preserve order).
        val names = (periodState.baseline.keys + periodState.changed)
            .filter { matches(it) }
            .sorted()

        val entries = names.mapNotNull { name ->
            val body = when {
                name in periodState.changed -> "\"enrollmentChanged\":true"
                else -> {
                    val cohort = periodState.baseline[name] ?: return@mapNotNull null
                    buildString {
                        append("\"cohort\":").append(JSONObject.quote(cohort.cohort))
                        config.enrollmentBuckets?.let { buckets ->
                            enrollmentBucket(cohort.enrollmentDateET, periodEndMillis, buckets)?.let {
                                append(",\"enrollmentBucket\":").append(JSONObject.quote(it))
                            }
                        }
                    }
                }
            }
            "${JSONObject.quote(name)}:{$body}"
        }
        return entries.joinToString(separator = ",", prefix = "{", postfix = "}")
    }

    private fun enrollmentBucket(
        enrollmentDateET: String?,
        periodEndMillis: Long,
        buckets: Map<String, EnrollmentBucketConfig>,
    ): String? {
        val enrollmentMillis = enrollmentDateET?.let { parseEnrollmentMillis(it) } ?: return null
        val elapsedSeconds = (periodEndMillis - enrollmentMillis) / 1000
        if (elapsedSeconds < 0) return null
        for ((bucketName, bucket) in buckets) {
            if (elapsedSeconds < bucket.gte) continue
            if (bucket.lt != null && elapsedSeconds >= bucket.lt) continue
            return bucketName
        }
        return null
    }

    private fun parseEnrollmentMillis(enrollmentDateET: String): Long? =
        runCatching { ZonedDateTime.parse(enrollmentDateET).toInstant().toEpochMilli() }.getOrNull()
}
