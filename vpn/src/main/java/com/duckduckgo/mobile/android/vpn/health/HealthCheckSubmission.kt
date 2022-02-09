/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.health

data class HealthCheckSubmission(
    val userReport: UserHealthSubmission,
    val systemReport: SystemHealthData
)

internal fun RawMetricsSubmission.isInBadHealth(): Boolean {
    return metrics.count { it.value.isBadState == true } > 0
}

internal fun RawMetricsSubmission.containsCriticalMetric(): Boolean {
    return metrics.any { it.value.isCritical }
}

internal fun RawMetricsSubmission.badHealthReasons(): List<String> {
    val badHealthReasons = mutableListOf<String>()
    metrics
        .filter { it.value.isBadState == true }
        .forEach {
            badHealthReasons.add(it.key)
        }
    return badHealthReasons
}

data class UserHealthSubmission(
    val state: String,
    val notes: String? = null
)
