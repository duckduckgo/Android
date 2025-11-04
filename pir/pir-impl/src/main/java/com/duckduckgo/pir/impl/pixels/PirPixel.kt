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

package com.duckduckgo.pir.impl.pixels

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
        type = Count,
    ),

    PIR_INTERNAL_MANUAL_SCAN_COMPLETED(
        baseName = "pir_internal_manual-scan_completed",
        type = Count,
    ),

    PIR_INTERNAL_SCHEDULED_SCAN_SCHEDULED(
        baseName = "pir_internal_scheduled-scan_scheduled",
        type = Count,
    ),

    PIR_INTERNAL_SCHEDULED_SCAN_STARTED(
        baseName = "pir_internal_scheduled-scan_started",
        type = Count,
    ),

    PIR_INTERNAL_SCHEDULED_SCAN_COMPLETED(
        baseName = "pir_internal_scheduled-scan_completed",
        type = Count,
    ),

    PIR_INTERNAL_SCAN_STATS(
        baseName = "pir_internal_scan-stats",
        type = Count,
    ),

    PIR_INTERNAL_OPT_OUT_STATS(
        baseName = "pir_internal_opt-out-stats",
        type = Count,
    ),

    PIR_INTERNAL_BROKER_SCAN_STARTED(
        baseName = "pir_internal_broker_scan_started",
        type = Count,
    ),

    PIR_INTERNAL_BROKER_SCAN_COMPLETED(
        baseName = "pir_internal_broker_scan_completed",
        type = Count,
    ),

    PIR_INTERNAL_BROKER_OPT_OUT_STARTED(
        baseName = "pir_internal_opt-out_started",
        type = Count,
    ),

    PIR_INTERNAL_BROKER_OPT_OUT_COMPLETED(
        baseName = "pir_internal_opt-out_completed",
        type = Count,
    ),

    PIR_INTERNAL_CPU_USAGE(
        baseName = "pir_internal_cpu_usage",
        type = Count,
    ),

    PIR_EMAIL_CONFIRMATION_LINK_RECEIVED(
        baseName = "pir_email-confirmation-link_client-received",
        type = Count,
    ),
    PIR_EMAIL_CONFIRMATION_LINK_BE_ERROR(
        baseName = "pir_email-confirmation-link_backend-status_error",
        type = Count,
    ),
    PIR_OPTOUT_STAGE_PENDING_EMAIL_CONFIRMATION(
        baseName = "pir_optout_stage_submit-awaiting-email-confirmation",
        type = Count,
    ),
    PIR_EMAIL_CONFIRMATION_ATTEMPT_START(
        baseName = "pir_email-confirmation_attempt-start",
        type = Count,
    ),
    PIR_EMAIL_CONFIRMATION_ATTEMPT_SUCCESS(
        baseName = "pir_email-confirmation_attempt-success",
        type = Count,
    ),
    PIR_EMAIL_CONFIRMATION_ATTEMPT_FAILED(
        baseName = "pir_email-confirmation_attempt-failure",
        type = Count,
    ),
    PIR_EMAIL_CONFIRMATION_MAX_RETRIES_EXCEEDED(
        baseName = "pir_email-confirmation_max-retries-exceeded",
        type = Count,
    ),
    PIR_EMAIL_CONFIRMATION_JOB_SUCCESS(
        baseName = "pir_email-confirmation_job-success",
        type = Count,
    ),
    PIR_EMAIL_CONFIRMATION_RUN_STARTED(
        baseName = "pir_email-confirmation_started",
        type = Count,
    ),
    PIR_EMAIL_CONFIRMATION_RUN_COMPLETED(
        baseName = "pir_email-confirmation_completed",
        type = Count,
    ),

    PIR_INTERNAL_SECURE_STORAGE_UNAVAILABLE(
        baseName = "pir_internal_secure-storage_unavailable",
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
