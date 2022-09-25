/*
 * Copyright (c) 2022 DuckDuckGo
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

interface AppHealthCallback {
    /**
     * @return `true` if the event is consumed, `false` otherwise. When the event is consumed no
     * other callback plugin will be notified
     */
    suspend fun onAppHealthUpdate(appHealthData: AppHealthData): Boolean
}

data class AppHealthData(
    val alerts: List<String>,
    val systemHealth: SystemHealthData
)

data class SystemHealthData(
    val isBadHealth: Boolean,
    val rawMetrics: List<RawMetricsSubmission>
)

data class RawMetricsSubmission(
    val name: String,
    val metrics: Map<String, Metric> = emptyMap(),
    val redacted: Boolean = false,
    val informational: Boolean = false,
)

/**
 * [value] metric value
 * [isBadState] `true` if currently in bad health, `false` or `null` otherwise
 * [isCritical] if metric in [isBadState] the state of the VPN would be critical
 */
data class Metric(
    val value: String,
    val isBadState: Boolean? = null,
    val isCritical: Boolean = false,
) {

    override fun toString(): String {
        if (isBadState == null) return value
        return String.format("%s, isBadState=%s", value, isBadState)
    }
}
