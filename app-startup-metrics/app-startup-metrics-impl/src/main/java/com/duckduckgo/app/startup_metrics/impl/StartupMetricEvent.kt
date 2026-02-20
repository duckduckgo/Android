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

package com.duckduckgo.app.startup_metrics.impl

import com.duckduckgo.app.startup_metrics.impl.metrics.MeasurementMethod

/**
 * Represents a single app startup measurement captured during production usage.
 *
 * This event is created when the app reaches an interactive state and contains timing,
 * device specifications, and classification metadata.
 *
 * @property startupType Classification of startup (COLD/WARM/HOT/UNKNOWN)
 * @property ttidDurationMs Time from process start to first frame in milliseconds (API 35+ only)
 * @property ttfdDurationMs Time from process start to fully drawn state in milliseconds
 * @property deviceRamBucket Device memory capacity bucket for classification
 * @property cpuArchitecture CPU architecture of the device
 * @property measurementMethod How measurement was captured (API_35_NATIVE or LEGACY_MANUAL)
 * @property apiLevel Android API level of device (minimum 26)
 *
 * @see StartupType
 * @see MeasurementMethod
 */
data class StartupMetricEvent(
    val startupType: StartupType,
    val ttidDurationMs: Long?,
    val ttfdDurationMs: Long,
    val deviceRamBucket: String?,
    val cpuArchitecture: String?,
    val measurementMethod: MeasurementMethod,
    val apiLevel: Int,
)
