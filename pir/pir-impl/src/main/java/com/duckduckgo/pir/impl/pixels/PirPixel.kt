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
    PIR_FOREGROUND_RUN_STARTED(
        baseName = "m_dbp_foreground-run_started",
        type = Count,
    ),

    PIR_FOREGROUND_RUN_COMPLETED(
        baseName = "m_dbp_foreground-run_completed",
        type = Count,
    ),

    PIR_FOREGROUND_RUN_START_FAILED(
        baseName = "m_dbp_foreground-run_start-failed",
        type = Count,
    ),

    PIR_FOREGROUND_RUN_LOW_MEMORY(
        baseName = "m_dbp_foreground-run_low-memory",
        type = Count,
    ),

    PIR_SCHEDULED_RUN_SCHEDULED(
        baseName = "m_dbp_scheduled-run_scheduled",
        type = Count,
    ),

    PIR_SCHEDULED_RUN_STARTED(
        baseName = "m_dbp_scheduled-run_started",
        type = Count,
    ),

    PIR_SCHEDULED_RUN_COMPLETED(
        baseName = "m_dbp_scheduled-run_completed",
        type = Count,
    ),

    PIR_CPU_USAGE(
        baseName = "m_dbp_cpu-usage",
        type = Count,
    ),

    PIR_EMAIL_CONFIRMATION_LINK_RECEIVED(
        baseName = "m_dbp_email-confirmation-link_client-received",
        type = Count,
    ),
    PIR_EMAIL_CONFIRMATION_LINK_BE_ERROR(
        baseName = "m_dbp_email-confirmation-link_backend-status_error",
        type = Count,
    ),
    PIR_EMAIL_CONFIRMATION_ATTEMPT_START(
        baseName = "m_dbp_email-confirmation_attempt-start",
        type = Count,
    ),
    PIR_EMAIL_CONFIRMATION_ATTEMPT_SUCCESS(
        baseName = "m_dbp_email-confirmation_attempt-success",
        type = Count,
    ),
    PIR_EMAIL_CONFIRMATION_ATTEMPT_FAILED(
        baseName = "m_dbp_email-confirmation_attempt-failure",
        type = Count,
    ),
    PIR_EMAIL_CONFIRMATION_MAX_RETRIES_EXCEEDED(
        baseName = "m_dbp_email-confirmation_max-retries-exceeded",
        type = Count,
    ),
    PIR_EMAIL_CONFIRMATION_JOB_SUCCESS(
        baseName = "m_dbp_email-confirmation_job-success",
        type = Count,
    ),
    PIR_EMAIL_CONFIRMATION_RUN_STARTED(
        baseName = "m_dbp_email-confirmation_started",
        type = Count,
    ),
    PIR_EMAIL_CONFIRMATION_RUN_COMPLETED(
        baseName = "m_dbp_email-confirmation_completed",
        type = Count,
    ),

    PIR_INTERNAL_SECURE_STORAGE_UNAVAILABLE(
        baseName = "m_dbp_secure-storage_unavailable",
        types = setOf(Count, Daily()),
    ),

    PIR_OPTOUT_SUBMIT_SUCCESS(
        baseName = "m_dbp_optout_process_submit-success",
        types = setOf(Count),
    ),

    PIR_OPTOUT_SUBMIT_FAILURE(
        baseName = "m_dbp_optout_process_failure",
        types = setOf(Count),
    ),

    PIR_BROKER_CUSTOM_STATS_OPTOUT_SUBMIT_SUCCESSRATE(
        baseName = "m_dbp_databroker_custom_stats_optoutsubmit",
        type = Count,
    ),

    PIR_BROKER_CUSTOM_STATS_7DAY_CONFIRMED_OPTOUT(
        baseName = "m_dbp_optoutjob_at-7-days_confirmed",
        type = Count,
    ),

    PIR_BROKER_CUSTOM_STATS_7DAY_UNCONFIRMED_OPTOUT(
        baseName = "m_dbp_optoutjob_at-7-days_unconfirmed",
        type = Count,
    ),
    PIR_BROKER_CUSTOM_STATS_14DAY_CONFIRMED_OPTOUT(
        baseName = "m_dbp_optoutjob_at-14-days_confirmed",
        type = Count,
    ),

    PIR_BROKER_CUSTOM_STATS_14DAY_UNCONFIRMED_OPTOUT(
        baseName = "m_dbp_optoutjob_at-14-days_unconfirmed",
        type = Count,
    ),

    PIR_BROKER_CUSTOM_STATS_21DAY_CONFIRMED_OPTOUT(
        baseName = "m_dbp_optoutjob_at-21-days_confirmed",
        type = Count,
    ),

    PIR_BROKER_CUSTOM_STATS_21DAY_UNCONFIRMED_OPTOUT(
        baseName = "m_dbp_optoutjob_at-21-days_unconfirmed",
        type = Count,
    ),
    PIR_BROKER_CUSTOM_STATS_42DAY_CONFIRMED_OPTOUT(
        baseName = "m_dbp_optoutjob_at-42-days_confirmed",
        type = Count,
    ),

    PIR_BROKER_CUSTOM_STATS_42DAY_UNCONFIRMED_OPTOUT(
        baseName = "m_dbp_optoutjob_at-42-days_unconfirmed",
        type = Count,
    ),

    PIR_ENGAGEMENT_DAU(
        baseName = "m_dbp_engagement_dau",
        type = Count,
    ),

    PIR_ENGAGEMENT_WAU(
        baseName = "m_dbp_engagement_wau",
        type = Count,
    ),

    PIR_ENGAGEMENT_MAU(
        baseName = "m_dbp_engagement_mau",
        type = Count,
    ),

    PIR_WEEKLY_CHILD_ORPHANED_OPTOUTS(
        baseName = "m_dbp_weekly_child-broker_orphaned-optouts",
        type = Count,
    ),
    PIR_SCAN_STARTED(
        baseName = "m_dbp_scan_started",
        type = Count,
    ),
    PIR_SCAN_STAGE(
        baseName = "m_dbp_scan_stage",
        type = Count,
    ),
    PIR_SCAN_STAGE_RESULT_MATCHES(
        baseName = "m_dbp_search_stage_main_status_success",
        type = Count,
    ),
    PIR_SCAN_STAGE_RESULT_NO_MATCH(
        baseName = "m_dbp_search_stage_main_status_no_results",
        type = Count,
    ),
    PIR_SCAN_STAGE_RESULT_ERROR(
        baseName = "m_dbp_search_stage_main_status_error",
        type = Count,
    ),
    PIR_OPTOUT_STAGE_START(
        baseName = "m_dbp_optout_stage_start",
        type = Count,
    ),
    PIR_OPTOUT_STAGE_EMAIL_GENERATE(
        baseName = "m_dbp_optout_stage_email-generate",
        type = Count,
    ),
    PIR_OPTOUT_STAGE_PENDING_EMAIL_CONFIRMATION(
        baseName = "m_dbp_optout_stage_submit-awaiting-email-confirmation",
        type = Count,
    ),
    PIR_OPTOUT_STAGE_CAPTCHA_PARSE(
        baseName = "m_dbp_optout_stage_captcha-parse",
        type = Count,
    ),
    PIR_OPTOUT_STAGE_CAPTCHA_SEND(
        baseName = "m_dbp_optout_stage_captcha-send",
        type = Count,
    ),
    PIR_OPTOUT_STAGE_CAPTCHA_SOLVE(
        baseName = "m_dbp_optout_stage_captcha-solve",
        type = Count,
    ),
    PIR_OPTOUT_STAGE_SUBMIT(
        baseName = "m_dbp_optout_stage_submit",
        type = Count,
    ),
    PIR_OPTOUT_STAGE_CONDITION_FOUND(
        baseName = "m_dbp_optout_stage_condition-found",
        type = Count,
    ),
    PIR_OPTOUT_STAGE_CONDITION_NOT_FOUND(
        baseName = "m_dbp_optout_stage_condition-not-found",
        type = Count,
    ),
    PIR_OPTOUT_STAGE_VALIDATE(
        baseName = "m_dbp_optout_stage_validate",
        type = Count,
    ),
    PIR_OPTOUT_STAGE_FILLFORM(
        baseName = "m_dbp_optout_stage_fill-form",
        type = Count,
    ),
    PIR_OPTOUT_STAGE_FINISH(
        baseName = "m_dbp_optout_stage_finish",
        type = Count,
    ),
    PIR_UPDATE_BROKER_SUCCESS(
        baseName = "m_dbp_update_databrokers_success",
        type = Count,
    ),
    PIR_UPDATE_BROKER_FAILURE(
        baseName = "m_dbp_update_databrokers_failure",
        type = Count,
    ),
    PIR_DOWNLOAD_MAINCONFIG_BE_FAILURE(
        baseName = "m_dbp_download_mainconfig_service_failure",
        type = Count,
    ),
    PIR_DOWNLOAD_MAINCONFIG_FAILURE(
        baseName = "m_dbp_download_mainconfig_failure",
        type = Count,
    ),
    PIR_BROKER_ACTION_FAILED(
        baseName = "m_dbp_data_broker_action-failed_error",
        types = setOf(Count, Daily()),
    ),
    PIR_DASHBOARD_OPENED(
        baseName = "pir_webui_dashboard_opened",
        types = setOf(Count, Daily()),
    ),
    PIR_INITIAL_SCAN_DURATION(
        baseName = "m_dbp_initial_scan_duration",
        type = Count,
    ),
    PIR_BG_STATS(
        baseName = "m_dbp_background-run_stats",
        type = Daily(),
    ),
    PIR_SCAN_INVALID_EVENT(
        baseName = "m_dbp_scan_invalid-event_dropped",
        type = Daily(),
    ),
    PIR_OPTOUT_INVALID_EVENT(
        baseName = "m_dbp_optout_invalid-event_dropped",
        type = Daily(),
    ),
    ;

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
