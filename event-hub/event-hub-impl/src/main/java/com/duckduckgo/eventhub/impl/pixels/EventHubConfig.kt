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

data class TelemetryPixelConfig(
    val name: String,
    val state: String,
    val trigger: TelemetryTriggerConfig,
    val parameters: Map<String, TelemetryParameterConfig>,
) {
    val isEnabled: Boolean get() = state == "enabled"
}

data class TelemetryTriggerConfig(
    val period: TelemetryPeriodConfig,
)

data class TelemetryPeriodConfig(
    val seconds: Int = 0,
    val minutes: Int = 0,
    val hours: Int = 0,
    val days: Int = 0,
) {
    val periodSeconds: Long
        get() = seconds.toLong() + minutes.toLong() * 60 + hours.toLong() * 3600 + days.toLong() * 86400
}

const val TEMPLATE_COUNTER = "counter"
const val TEMPLATE_EXPERIMENTS = "experiments"

/**
 * A single parameter within a telemetry pixel. Each template has its own strongly-typed config.
 */
sealed interface TelemetryParameterConfig {
    val template: String
}

/**
 * Counts web events whose type matches [source], reported as a bucket name at fire time.
 */
data class CounterParameterConfig(
    val source: String,
    val buckets: Map<String, BucketConfig>,
) : TelemetryParameterConfig {
    override val template: String = TEMPLATE_COUNTER
}

/**
 * Conveys the experiment cohorts the user was enrolled in during the period.
 *
 * [matchExperiments] optional regex (already compiled to a client-ready pattern by remote config);
 *   when null, all active experiments match.
 * [enrollmentBuckets] optional map of bucket name -> enrollment-age window in seconds; when null,
 *   no enrollment-timing information is included.
 */
data class ExperimentsParameterConfig(
    val matchExperiments: String?,
    val enrollmentBuckets: Map<String, EnrollmentBucketConfig>?,
) : TelemetryParameterConfig {
    override val template: String = TEMPLATE_EXPERIMENTS
}

data class BucketConfig(
    val gte: Int,
    val lt: Int?,
)

/**
 * Enrollment-age bucket window, in seconds (compiled form). Selected bucket is the first where
 * gte <= elapsedSeconds < lt (or gte <= elapsedSeconds when lt is null).
 */
data class EnrollmentBucketConfig(
    val gte: Long,
    val lt: Long?,
)

data class ParamState(val value: Int, val stopCounting: Boolean = false)

/**
 * The enrollment baseline + observed changes for a single running period.
 *
 * [baseline] active matching experiments at period start: experiment name -> cohort snapshot.
 * [changed] experiment names whose enrollment state changed (join/leave/cohort-change) during the
 *   period. Membership here means the period is a partial enrollment for that experiment.
 */
data class ExperimentPeriodState(
    val baseline: Map<String, ExperimentCohort>,
    val changed: Set<String>,
)

data class ExperimentCohort(
    val cohort: String,
    val enrollmentDateET: String?,
)

data class PixelState(
    val pixelName: String,
    val periodStartMillis: Long,
    val periodEndMillis: Long,
    val config: TelemetryPixelConfig,
    val params: Map<String, ParamState>,
    val experiments: ExperimentPeriodState? = null,
)

/**
 * The result of building a pixel for a completed period.
 *
 * [fires] whether the pixel should fire — true iff at least one measurement parameter (counter/data)
 *   produced output. Dimensional parameters (experiments) never cause or suppress a fire.
 * [params] the parameters to send when firing (including the derived attributionPeriod); empty when
 *   [fires] is false.
 */
data class PixelOutput(
    val fires: Boolean,
    val params: Map<String, String>,
)
