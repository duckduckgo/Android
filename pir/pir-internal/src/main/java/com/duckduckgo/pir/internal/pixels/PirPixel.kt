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

package com.duckduckgo.pir.internal.pixels

import com.duckduckgo.app.statistics.pixels.Pixel.PixelType
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Count
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Daily
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Unique

enum class PirPixel(
    val baseName: String,
    private val types: Set<PixelType>,
    private val withSuffix: Boolean = true,
) {
    PIR_INTERNAL_MANUAL_SCAN_STARTED(
        baseName = "pir_internal_manual-scan_started",
        types = setOf(Count, Unique()),
    ),

    PIR_INTERNAL_MANUAL_SCAN_COMPLETED(
        baseName = "pir_internal_manual-scan_completed",
        types = setOf(Count, Unique()),
    ),

    PIR_INTERNAL_MANUAL_SCAN_BROKER_STARTED(
        baseName = "pir_internal_manual-scan_broker_started",
        type = Count,
    ),

    PIR_INTERNAL_MANUAL_SCAN_BROKER_COMPLETED(
        baseName = "pir_internal_manual-scan_broker_completed",
        type = Count,
    ),

    PIR_INTERNAL_SCHEDULED_SCAN_SCHEDULED(
        baseName = "pir_internal_scheduled-scan_scheduled",
        types = setOf(Count, Unique()),
    ),

    PIR_INTERNAL_SCHEDULED_SCAN_STARTED(
        baseName = "pir_internal_scheduled-scan_started",
        types = setOf(Count, Unique()),
    ),

    PIR_INTERNAL_SCHEDULED_SCAN_COMPLETED(
        baseName = "pir_internal_scheduled-scan_completed",
        types = setOf(Count, Unique()),
    ),

    PIR_INTERNAL_SCHEDULED_SCAN_BROKER_STARTED(
        baseName = "pir_internal_scheduled-scan_broker_started",
        type = Count,
    ),

    PIR_INTERNAL_SCHEDULED_SCAN_BROKER_COMPLETED(
        baseName = "pir_internal_scheduled-scan_broker_completed",
        type = Count,
    ),

    PIR_INTERNAL_OPT_OUT_RECORD_STARTED(
        baseName = "pir_internal_optout_record_started",
        type = Count,
    ),

    PIR_INTERNAL_OPT_OUT_RECORD_COMPLETED(
        baseName = "pir_internal_optout_record_completed",
        type = Count,
    ),

    PIR_INTERNAL_CPU_USAGE(
        baseName = "pir_internal_cpu_usage",
        type = Count,
    ), ;

    constructor(
        baseName: String,
        type: PixelType,
        withSuffix: Boolean = true,
    ) : this(baseName, setOf(type), withSuffix)

    fun getPixelNames(): Map<PixelType, String> =
        types.associateWith { type -> if (withSuffix) "${baseName}_${type.pixelNameSuffix}" else baseName }
}

internal val PixelType.pixelNameSuffix: String
    get() = when (this) {
        is Count -> "c"
        is Daily -> "d"
        is Unique -> "u"
    }
