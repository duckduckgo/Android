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

import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_BG_STATS
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_BROKER_ACTION_FAILED
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_BROKER_CUSTOM_STATS_14DAY_CONFIRMED_OPTOUT
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_BROKER_CUSTOM_STATS_14DAY_UNCONFIRMED_OPTOUT
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_BROKER_CUSTOM_STATS_21DAY_CONFIRMED_OPTOUT
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_BROKER_CUSTOM_STATS_21DAY_UNCONFIRMED_OPTOUT
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_BROKER_CUSTOM_STATS_42DAY_CONFIRMED_OPTOUT
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_BROKER_CUSTOM_STATS_42DAY_UNCONFIRMED_OPTOUT
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_BROKER_CUSTOM_STATS_7DAY_CONFIRMED_OPTOUT
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_BROKER_CUSTOM_STATS_7DAY_UNCONFIRMED_OPTOUT
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_BROKER_CUSTOM_STATS_OPTOUT_SUBMIT_SUCCESSRATE
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_CPU_USAGE
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_DASHBOARD_OPENED
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_DOWNLOAD_MAINCONFIG_BE_FAILURE
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_DOWNLOAD_MAINCONFIG_FAILURE
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_EMAIL_CONFIRMATION_ATTEMPT_FAILED
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_EMAIL_CONFIRMATION_ATTEMPT_START
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_EMAIL_CONFIRMATION_ATTEMPT_SUCCESS
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_EMAIL_CONFIRMATION_JOB_SUCCESS
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_EMAIL_CONFIRMATION_LINK_BE_ERROR
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_EMAIL_CONFIRMATION_LINK_RECEIVED
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_EMAIL_CONFIRMATION_MAX_RETRIES_EXCEEDED
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_EMAIL_CONFIRMATION_RUN_COMPLETED
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_EMAIL_CONFIRMATION_RUN_STARTED
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_ENGAGEMENT_DAU
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_ENGAGEMENT_MAU
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_ENGAGEMENT_WAU
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_FOREGROUND_RUN_COMPLETED
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_FOREGROUND_RUN_LOW_MEMORY
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_FOREGROUND_RUN_STARTED
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_FOREGROUND_RUN_START_FAILED
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_INITIAL_SCAN_DURATION
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_INTERNAL_SECURE_STORAGE_UNAVAILABLE
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_OPTOUT_INVALID_EVENT
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_OPTOUT_STAGE_CAPTCHA_PARSE
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_OPTOUT_STAGE_CAPTCHA_SEND
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_OPTOUT_STAGE_CAPTCHA_SOLVE
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_OPTOUT_STAGE_CONDITION_FOUND
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_OPTOUT_STAGE_CONDITION_NOT_FOUND
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_OPTOUT_STAGE_EMAIL_GENERATE
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_OPTOUT_STAGE_FILLFORM
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_OPTOUT_STAGE_FINISH
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_OPTOUT_STAGE_PENDING_EMAIL_CONFIRMATION
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_OPTOUT_STAGE_START
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_OPTOUT_STAGE_SUBMIT
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_OPTOUT_STAGE_VALIDATE
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_OPTOUT_SUBMIT_FAILURE
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_OPTOUT_SUBMIT_SUCCESS
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_SCAN_INVALID_EVENT
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_SCAN_STAGE
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_SCAN_STAGE_RESULT_ERROR
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_SCAN_STAGE_RESULT_MATCHES
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_SCAN_STAGE_RESULT_NO_MATCH
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_SCAN_STARTED
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_SCHEDULED_RUN_COMPLETED
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_SCHEDULED_RUN_SCHEDULED
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_SCHEDULED_RUN_STARTED
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_UPDATE_BROKER_FAILURE
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_UPDATE_BROKER_SUCCESS
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_WEEKLY_CHILD_ORPHANED_OPTOUTS
import com.squareup.anvil.annotations.ContributesBinding
import logcat.logcat
import javax.inject.Inject

/**
 * Refer to personal_information_removal.json5 for definition of pixels in this class.
 */
interface PirPixelSender {
    /**
     * Emits a pixel to signal that a manually initiated scan has started.
     */
    fun reportManualScanStarted()

    /**
     * Emits a pixel to signal that a manually initiated scan has been completed.
     *
     * @param totalTimeInMillis - how long it took for the scan to complete
     */
    fun reportManualScanCompleted(
        totalTimeInMillis: Long,
    )

    /**
     * Emits a pixel to signal that a manually initiated scan failed to start as foreground.
     */
    fun reportManualScanStartFailed()

    /**
     * Emits a pixel to signal that the foreground scan service is running low on memory.
     */
    fun reportManualScanLowMemory()

    /**
     * Emits a pixel to signal that the scheduled scan has been scheduled.
     */
    fun reportScheduledScanScheduled()

    /**
     * Emits a pixel to signal that s scheduled scan run has started.
     */
    fun reportScheduledScanStarted()

    /**
     * Emits a pixel to signal that a scheduled scan run has been completed.
     */
    fun reportScheduledScanCompleted(
        totalTimeInMillis: Long,
    )

    /**
     * Emits a pixel to signal that an opt-out job for a specific extractedProfile has been successfully submitted.
     *
     * @param brokerUrl url of the Broker for which the opt-out is for
     * @param parent The parent data broker of the one this opt-out attempt targets
     * @param durationMs - Total duration of the opt-out attempt in milliseconds
     * @param optOutAttemptCount - The number of tries it took to submit successfully.
     * @param emailPattern - Email pattern used during submission, when available else null.
     */
    fun reportOptOutSubmitted(
        brokerUrl: String,
        parent: String,
        durationMs: Long,
        optOutAttemptCount: Int,
        emailPattern: String?,
        isVpnRunning: Boolean,
    )

    /**
     * Emits a pixel to signal that an opt-out job for a specific extractedProfile has been failed.
     *
     * @param brokerUrl url of the Broker for which the opt-out is for
     * @param parent The parent data broker of the one this opt-out attempt targets
     * @param brokerJsonVersion The version of the broker JSON file
     * @param durationMs - Total duration of the opt-out attempt in milliseconds
     * @param stage - The stage where the failure occurred
     * @param tries - The number of tries it took to submit successfully.
     * @param emailPattern - Email pattern used during submission, when available else null.
     * @param actionId - Predefined identifier of the broker action that failed
     * @param actionType - Type of action that failed
     */
    fun reportOptOutFailed(
        brokerUrl: String,
        parent: String,
        brokerJsonVersion: String,
        durationMs: Long,
        stage: PirStage,
        tries: Int,
        emailPattern: String?,
        actionId: String,
        actionType: String,
        isVpnRunning: Boolean,
    )

    /**
     * Sends a pixel when the CPU Usage threshold has been reached while executing
     * PIR related work.
     *
     * @param averageCpuUsagePercent - average CPU usage percent
     */
    fun sendCPUUsageAlert(averageCpuUsagePercent: Int)

    /**
     * Emits a pixel when the client received the email confirmation link provided by the BE.
     *
     * @param brokerUrl The URL of the data broker that this action was operating on
     * @param brokerVersion The version of the broker JSON file
     * @param linkAgeMs Time from when the link was received by the BE (returned in the response) to when it was fetched by the client
     */
    fun reportEmailConfirmationLinkFetched(
        brokerUrl: String,
        brokerVersion: String,
        linkAgeMs: Long,
    )

    /**
     * Emits a pixel when the backend returns an unknown/error status while polling email data
     *
     * @param brokerUrl The URL of the data broker that this action was operating on
     * @param brokerVersion The version of the broker JSON file
     * @param status Backend item status (['unknown', 'error'])
     * @param errorCode Backend item error code (['server_error', 'extraction_error', 'request_error'])
     */
    fun reportEmailConfirmationLinkFetchBEError(
        brokerUrl: String,
        brokerVersion: String,
        status: String,
        errorCode: String,
    )

    /**
     * Emits a pixel when the opt-out form is submitted and the operation is awaiting email confirmation
     *
     * @param brokerUrl The URL of the data broker that this action was operating on
     * @param brokerVersion The version of the broker JSON file
     * @param actionId The ID of the action, to disambiguate between multiple actions in the same opt out attempt
     * @param durationMs The duration of the action execution in milliseconds
     * @param tries The number of tries it took for the action to complete
     */
    fun reportStagePendingEmailConfirmation(
        brokerUrl: String,
        brokerVersion: String,
        actionId: String,
        durationMs: Long,
        tries: Int,
    )

    /**
     * Emits a pixel when an email confirmation attempt is starting
     *
     * @param brokerUrl The URL of the data broker that this action was operating on
     * @param brokerVersion The version of the broker JSON file
     * @param attemptNumber The confirmation attempt number (1..3)
     * @param actionId The ID of the action, to disambiguate between multiple actions in the same opt out attempt
     */
    fun reportEmailConfirmationAttemptStart(
        brokerUrl: String,
        brokerVersion: String,
        attemptNumber: Int,
        actionId: String,
    )

    /**
     * Emits a pixel when an email confirmation attempt succeeds
     *
     * @param brokerUrl The URL of the data broker that this action was operating on
     * @param brokerVersion The version of the broker JSON file
     * @param attemptNumber The confirmation attempt number (1..3)
     * @param actionId The ID of the action, to disambiguate between multiple actions in the same opt out attempt
     * @param durationMs The duration of the attempt in milliseconds
     */
    fun reportEmailConfirmationAttemptSuccess(
        brokerUrl: String,
        brokerVersion: String,
        attemptNumber: Int,
        actionId: String,
        durationMs: Long,
    )

    /**
     * Emits a pixel when an email confirmation attempt fails (non-final).
     *
     * @param brokerUrl The URL of the data broker that this action was operating on
     * @param brokerVersion The version of the broker JSON file
     * @param attemptNumber The confirmation attempt number (1..3)
     * @param actionId The ID of the action, to disambiguate between multiple actions in the same opt out attempt
     * @param durationMs The duration of the attempt in milliseconds
     */
    fun reportEmailConfirmationAttemptFailed(
        brokerUrl: String,
        brokerVersion: String,
        attemptNumber: Int,
        actionId: String,
        durationMs: Long,
    )

    /**
     * Emits a pixel when the email confirmation job hits the maximum number of retries (final failure).
     *
     * @param brokerUrl The URL of the data broker that this action was operating on
     * @param brokerVersion The version of the broker JSON file
     * @param actionId The ID of the action, to disambiguate between multiple actions in the same opt out attempt
     */
    fun reportEmailConfirmationAttemptRetriesExceeded(
        brokerUrl: String,
        brokerVersion: String,
        actionId: String,
    )

    /**
     * Emits a pixel when the email confirmation job and everything else have been completely updated accordingly.
     *
     * @param brokerUrl The URL of the data broker that this action was operating on
     * @param brokerVersion The version of the broker JSON file
     */
    fun reportEmailConfirmationJobSuccess(
        brokerUrl: String,
        brokerVersion: String,
    )

    fun reportEmailConfirmationStarted()

    fun reportEmailConfirmationCompleted(
        totalTimeInMillis: Long,
        totalFetchAttempts: Int,
        totalEmailConfirmationJobs: Int,
    )

    /**
     * Emits a pixel to signal that PIR encrypted database is unavailable.
     */
    fun reportSecureStorageUnavailable()

    /**
     * Emits a pixel containing the opt-out submit success rate for a broker for the last 24 hours
     *
     * @param brokerUrl url of the Broker for which the opt-out submit rate is for
     * @param optOutSuccessRate opt out submit success rate for the past 24 hours
     */
    fun reportBrokerCustomStateOptOutSubmitRate(
        brokerUrl: String,
        optOutSuccessRate: Double,
    )

    /**
     * Emits a pixel when an opt-out has been confirmed within 7 days.
     */
    fun reportBrokerOptOutConfirmed7Days(brokerUrl: String)

    /**
     * Emits a pixel when an opt-out is unconfirmed within 7 days.
     */
    fun reportBrokerOptOutUnconfirmed7Days(brokerUrl: String)

    /**
     * Emits a pixel when an opt-out has been confirmed within 14 days.
     */
    fun reportBrokerOptOutConfirmed14Days(brokerUrl: String)

    /**
     * Emits a pixel when an opt-out is unconfirmed within 14 days.
     */
    fun reportBrokerOptOutUnconfirmed14Days(brokerUrl: String)

    /**
     * Emits a pixel when an opt-out has been confirmed within 21 days.
     */
    fun reportBrokerOptOutConfirmed21Days(brokerUrl: String)

    /**
     * Emits a pixel when an opt-out is unconfirmed within 21 days.
     */
    fun reportBrokerOptOutUnconfirmed21Days(brokerUrl: String)

    /**
     * Emits a pixel when an opt-out has been confirmed within 42 days.
     */
    fun reportBrokerOptOutConfirmed42Days(brokerUrl: String)

    /**
     * Emits a pixel when an opt-out is unconfirmed within 42 days.
     */
    fun reportBrokerOptOutUnconfirmed42Days(brokerUrl: String)

    /**
     * Emits a pixel to report Daily Active Users for PIR.
     */
    fun reportDAU()

    /**
     * Emits a pixel to report Weekly Active Users for PIR.
     */
    fun reportWAU()

    /**
     * Emits a pixel to report Monthly Active Users for PIR.
     */
    fun reportMAU()

    /**
     * Emits a pixel to report weekly data on orphaned opt-out records on child data brokers that don't have matching profiles on their parent broker.
     *
     * @param brokerUrl The URL of the child data broker site
     * @param childParentRecordDifference (child extracted profile count) - (parent extracted profile count)
     * @param orphanedRecordsCount The Number of child profiles with no parent match
     */
    fun reportWeeklyChildOrphanedOptOuts(
        brokerUrl: String,
        childParentRecordDifference: Int,
        orphanedRecordsCount: Int,
    )

    fun reportScanStarted(
        brokerUrl: String,
    )

    fun reportScanStage(
        brokerUrl: String,
        brokerVersion: String,
        tries: Int,
        parentUrl: String,
        actionId: String,
        actionType: String,
    )

    fun reportScanMatches(
        brokerUrl: String,
        totalMatches: Int,
        durationMs: Long,
        inManualStarted: Boolean,
        parentUrl: String,
        isVpnRunning: Boolean,
    )

    fun reportScanNoMatch(
        brokerUrl: String,
        brokerVersion: String,
        durationMs: Long,
        inManualStarted: Boolean,
        parentUrl: String,
        actionId: String,
        actionType: String,
        isVpnRunning: Boolean,
    )

    fun reportScanError(
        brokerUrl: String,
        brokerVersion: String,
        durationMs: Long,
        errorCategory: String,
        errorDetails: String,
        inManualStarted: Boolean,
        parentUrl: String,
        actionId: String,
        actionType: String,
        isVpnRunning: Boolean,
    )

    fun reportOptOutStageStart(
        brokerUrl: String,
        parentUrl: String,
    )

    fun reportOptOutStageEmailGenerate(
        brokerUrl: String,
        parentUrl: String,
        brokerVersion: String,
        durationMs: Long,
        tries: Int,
        actionId: String,
    )

    fun reportOptOutStageCaptchaParse(
        brokerUrl: String,
        parentUrl: String,
        brokerVersion: String,
        durationMs: Long,
        tries: Int,
        actionId: String,
    )

    fun reportOptOutStageCaptchaSend(
        brokerUrl: String,
        parentUrl: String,
        brokerVersion: String,
        durationMs: Long,
        tries: Int,
        actionId: String,
    )

    fun reportOptOutStageCaptchaSolve(
        brokerUrl: String,
        parentUrl: String,
        brokerVersion: String,
        durationMs: Long,
        tries: Int,
        actionId: String,
    )

    fun reportOptOutStageSubmit(
        brokerUrl: String,
        parentUrl: String,
        brokerVersion: String,
        durationMs: Long,
        tries: Int,
        actionId: String,
    )

    fun reportOptOutStageValidate(
        brokerUrl: String,
        parentUrl: String,
        brokerVersion: String,
        durationMs: Long,
        tries: Int,
        actionId: String,
    )

    fun reportOptOutStageFillForm(
        brokerUrl: String,
        parentUrl: String,
        brokerVersion: String,
        durationMs: Long,
        tries: Int,
        actionId: String,
    )

    fun reportOptOutConditionFound(
        brokerUrl: String,
        parentUrl: String,
        brokerVersion: String,
        durationMs: Long,
        tries: Int,
        actionId: String,
    )

    fun reportOptOutConditionNotFound(
        brokerUrl: String,
        parentUrl: String,
        brokerVersion: String,
        durationMs: Long,
        tries: Int,
        actionId: String,
    )

    fun reportOptOutStageFinish(
        brokerUrl: String,
        parentUrl: String,
        durationMs: Long,
    )

    fun reportUpdateBrokerJsonSuccess(
        brokerJsonFileName: String,
        removedAtMs: Long,
    )

    fun reportUpdateBrokerJsonFailure(
        brokerJsonFileName: String,
        removedAtMs: Long,
    )

    fun reportDownloadMainConfigBEFailure(
        errorCode: String,
    )

    fun reportDownloadMainConfigFailure()

    fun reportBrokerActionFailure(
        brokerUrl: String,
        brokerVersion: String,
        parentUrl: String,
        actionId: String,
        errorMessage: String,
        stepType: String,
    )

    fun reportDashboardOpened()

    fun reportInitialScanDuration(
        durationMs: Long,
        profileQueryCount: Int,
    )

    fun reportBackgroundScanStats(
        scanFrequencyWithinThreshold: Boolean,
    )

    fun reportScanInvalidEvent(
        brokerUrl: String,
        brokerVersion: String,
    )

    fun reportOptOutInvalidEvent(
        brokerUrl: String,
        brokerVersion: String,
    )
}

@ContributesBinding(AppScope::class)
class RealPirPixelSender @Inject constructor(
    private val pixelSender: Pixel,
) : PirPixelSender {
    override fun reportManualScanStarted() {
        fire(PIR_FOREGROUND_RUN_STARTED)
    }

    override fun reportManualScanCompleted(
        totalTimeInMillis: Long,
    ) {
        val params = mapOf(
            PARAM_KEY_TOTAL_TIME to totalTimeInMillis.toString(),
        )
        fire(PIR_FOREGROUND_RUN_COMPLETED, params)
    }

    override fun reportManualScanStartFailed() {
        enqueueFire(PIR_FOREGROUND_RUN_START_FAILED)
    }

    override fun reportManualScanLowMemory() {
        enqueueFire(PIR_FOREGROUND_RUN_LOW_MEMORY)
    }

    override fun reportScheduledScanScheduled() {
        fire(PIR_SCHEDULED_RUN_SCHEDULED)
    }

    override fun reportScheduledScanStarted() {
        fire(PIR_SCHEDULED_RUN_STARTED)
    }

    override fun reportScheduledScanCompleted(
        totalTimeInMillis: Long,
    ) {
        val params = mapOf(
            PARAM_KEY_TOTAL_TIME to totalTimeInMillis.toString(),
        )
        fire(PIR_SCHEDULED_RUN_COMPLETED, params)
    }

    override fun reportOptOutSubmitted(
        brokerUrl: String,
        parent: String,
        durationMs: Long,
        optOutAttemptCount: Int,
        emailPattern: String?,
        isVpnRunning: Boolean,
    ) {
        val params = mapOf(
            PARAM_KEY_BROKER to brokerUrl,
            PARAM_KEY_PARENT to parent,
            PARAM_DURATION to durationMs.toString(),
            PARAM_TRIES to optOutAttemptCount.toString(),
            PARAM_KEY_PATTERN to (emailPattern ?: ""),
            PARAM_KEY_VPN_STATE to isVpnRunning.toVpnConnectionState(),
        )
        fire(PIR_OPTOUT_SUBMIT_SUCCESS, params)
    }

    private fun Boolean.toVpnConnectionState(): String {
        return if (this) {
            "connected"
        } else {
            "disconnected"
        }
    }

    override fun reportOptOutFailed(
        brokerUrl: String,
        parent: String,
        brokerJsonVersion: String,
        durationMs: Long,
        stage: PirStage,
        tries: Int,
        emailPattern: String?,
        actionId: String,
        actionType: String,
        isVpnRunning: Boolean,
    ) {
        val params = mapOf(
            PARAM_KEY_BROKER to brokerUrl,
            PARAM_KEY_PARENT to parent,
            PARAM_BROKER_VERSION to brokerJsonVersion,
            PARAM_DURATION to durationMs.toString(),
            PARAM_KEY_STAGE to stage.stageName,
            PARAM_TRIES to tries.toString(),
            PARAM_KEY_PATTERN to (emailPattern ?: ""),
            PARAM_ACTION_ID to actionId,
            PARAM_KEY_ACTION_TYPE to actionType,
            PARAM_KEY_VPN_STATE to isVpnRunning.toVpnConnectionState(),
        )

        fire(PIR_OPTOUT_SUBMIT_FAILURE, params)
    }

    override fun sendCPUUsageAlert(averageCpuUsagePercent: Int) {
        val params = mapOf(
            PARAM_KEY_CPU_USAGE to averageCpuUsagePercent.toString(),
        )
        fire(PIR_CPU_USAGE, params)
    }

    override fun reportEmailConfirmationLinkFetched(
        brokerUrl: String,
        brokerVersion: String,
        linkAgeMs: Long,
    ) {
        val params = mapOf(
            PARAM_KEY_BROKER to brokerUrl,
            PARAM_BROKER_VERSION to brokerVersion,
            PARAM_LINK_AGE to linkAgeMs.toString(),
        )
        fire(PIR_EMAIL_CONFIRMATION_LINK_RECEIVED, params)
    }

    override fun reportEmailConfirmationLinkFetchBEError(
        brokerUrl: String,
        brokerVersion: String,
        status: String,
        errorCode: String,
    ) {
        val params = mapOf(
            PARAM_KEY_BROKER to brokerUrl,
            PARAM_BROKER_VERSION to brokerVersion,
            PARAM_STATUS to status,
            PARAM_ERROR_CODE to errorCode,
        )
        fire(PIR_EMAIL_CONFIRMATION_LINK_BE_ERROR, params)
    }

    override fun reportStagePendingEmailConfirmation(
        brokerUrl: String,
        brokerVersion: String,
        actionId: String,
        durationMs: Long,
        tries: Int,
    ) {
        val params = mapOf(
            PARAM_KEY_BROKER to brokerUrl,
            PARAM_BROKER_VERSION to brokerVersion,
            PARAM_ACTION_ID to actionId,
            PARAM_DURATION to durationMs.toString(),
            PARAM_TRIES to tries.toString(),
        )
        fire(PIR_OPTOUT_STAGE_PENDING_EMAIL_CONFIRMATION, params)
    }

    override fun reportEmailConfirmationAttemptStart(
        brokerUrl: String,
        brokerVersion: String,
        attemptNumber: Int,
        actionId: String,
    ) {
        val params = mapOf(
            PARAM_KEY_BROKER to brokerUrl,
            PARAM_BROKER_VERSION to brokerVersion,
            PARAM_ATTEMPT_NUMBER to attemptNumber.toString(),
            PARAM_ACTION_ID to actionId,
        )
        fire(PIR_EMAIL_CONFIRMATION_ATTEMPT_START, params)
    }

    override fun reportEmailConfirmationAttemptSuccess(
        brokerUrl: String,
        brokerVersion: String,
        attemptNumber: Int,
        actionId: String,
        durationMs: Long,
    ) {
        val params = mapOf(
            PARAM_KEY_BROKER to brokerUrl,
            PARAM_BROKER_VERSION to brokerVersion,
            PARAM_ATTEMPT_NUMBER to attemptNumber.toString(),
            PARAM_ACTION_ID to actionId,
            PARAM_DURATION to durationMs.toString(),
        )
        fire(PIR_EMAIL_CONFIRMATION_ATTEMPT_SUCCESS, params)
    }

    override fun reportEmailConfirmationAttemptFailed(
        brokerUrl: String,
        brokerVersion: String,
        attemptNumber: Int,
        actionId: String,
        durationMs: Long,
    ) {
        val params = mapOf(
            PARAM_KEY_BROKER to brokerUrl,
            PARAM_BROKER_VERSION to brokerVersion,
            PARAM_ATTEMPT_NUMBER to attemptNumber.toString(),
            PARAM_ACTION_ID to actionId,
            PARAM_DURATION to durationMs.toString(),
        )
        fire(PIR_EMAIL_CONFIRMATION_ATTEMPT_FAILED, params)
    }

    override fun reportEmailConfirmationAttemptRetriesExceeded(
        brokerUrl: String,
        brokerVersion: String,
        actionId: String,
    ) {
        val params = mapOf(
            PARAM_KEY_BROKER to brokerUrl,
            PARAM_BROKER_VERSION to brokerVersion,
            PARAM_ACTION_ID to actionId,
        )
        fire(PIR_EMAIL_CONFIRMATION_MAX_RETRIES_EXCEEDED, params)
    }

    override fun reportEmailConfirmationJobSuccess(
        brokerUrl: String,
        brokerVersion: String,
    ) {
        val params = mapOf(
            PARAM_KEY_BROKER to brokerUrl,
            PARAM_BROKER_VERSION to brokerVersion,
        )
        fire(PIR_EMAIL_CONFIRMATION_JOB_SUCCESS, params)
    }

    override fun reportEmailConfirmationStarted() {
        fire(PIR_EMAIL_CONFIRMATION_RUN_STARTED)
    }

    override fun reportEmailConfirmationCompleted(
        totalTimeInMillis: Long,
        totalFetchAttempts: Int,
        totalEmailConfirmationJobs: Int,
    ) {
        val params = mapOf(
            PARAM_KEY_TOTAL_TIME to totalTimeInMillis.toString(),
            PARAM_TOTAL_FETCH to totalFetchAttempts.toString(),
            PARAM_TOTAL_EMAIL_CONFIRMATION to totalEmailConfirmationJobs.toString(),
        )
        fire(PIR_EMAIL_CONFIRMATION_RUN_COMPLETED, params)
    }

    override fun reportSecureStorageUnavailable() {
        fire(PIR_INTERNAL_SECURE_STORAGE_UNAVAILABLE)
    }

    override fun reportBrokerCustomStateOptOutSubmitRate(
        brokerUrl: String,
        optOutSuccessRate: Double,
    ) {
        val params = mapOf(
            PARAM_KEY_BROKER to brokerUrl,
            PARAM_KEY_OPTOUT_SUBMIT_SUCCESS_RATE to optOutSuccessRate.toString(),
        )

        fire(PIR_BROKER_CUSTOM_STATS_OPTOUT_SUBMIT_SUCCESSRATE, params)
    }

    override fun reportBrokerOptOutConfirmed7Days(brokerUrl: String) {
        val params = mapOf(
            PARAM_KEY_BROKER to brokerUrl,
        )

        fire(PIR_BROKER_CUSTOM_STATS_7DAY_CONFIRMED_OPTOUT, params)
    }

    override fun reportBrokerOptOutUnconfirmed7Days(brokerUrl: String) {
        val params = mapOf(
            PARAM_KEY_BROKER to brokerUrl,
        )

        fire(PIR_BROKER_CUSTOM_STATS_7DAY_UNCONFIRMED_OPTOUT, params)
    }

    override fun reportBrokerOptOutConfirmed14Days(brokerUrl: String) {
        val params = mapOf(
            PARAM_KEY_BROKER to brokerUrl,
        )

        fire(PIR_BROKER_CUSTOM_STATS_14DAY_CONFIRMED_OPTOUT, params)
    }

    override fun reportBrokerOptOutUnconfirmed14Days(brokerUrl: String) {
        val params = mapOf(
            PARAM_KEY_BROKER to brokerUrl,
        )

        fire(PIR_BROKER_CUSTOM_STATS_14DAY_UNCONFIRMED_OPTOUT, params)
    }

    override fun reportBrokerOptOutConfirmed21Days(brokerUrl: String) {
        val params = mapOf(
            PARAM_KEY_BROKER to brokerUrl,
        )

        fire(PIR_BROKER_CUSTOM_STATS_21DAY_CONFIRMED_OPTOUT, params)
    }

    override fun reportBrokerOptOutUnconfirmed21Days(brokerUrl: String) {
        val params = mapOf(
            PARAM_KEY_BROKER to brokerUrl,
        )

        fire(PIR_BROKER_CUSTOM_STATS_21DAY_UNCONFIRMED_OPTOUT, params)
    }

    override fun reportBrokerOptOutConfirmed42Days(brokerUrl: String) {
        val params = mapOf(
            PARAM_KEY_BROKER to brokerUrl,
        )

        fire(PIR_BROKER_CUSTOM_STATS_42DAY_CONFIRMED_OPTOUT, params)
    }

    override fun reportBrokerOptOutUnconfirmed42Days(brokerUrl: String) {
        val params = mapOf(
            PARAM_KEY_BROKER to brokerUrl,
        )

        fire(PIR_BROKER_CUSTOM_STATS_42DAY_UNCONFIRMED_OPTOUT, params)
    }

    override fun reportDAU() {
        fire(PIR_ENGAGEMENT_DAU)
    }

    override fun reportWAU() {
        fire(PIR_ENGAGEMENT_WAU)
    }

    override fun reportMAU() {
        fire(PIR_ENGAGEMENT_MAU)
    }

    override fun reportWeeklyChildOrphanedOptOuts(
        brokerUrl: String,
        childParentRecordDifference: Int,
        orphanedRecordsCount: Int,
    ) {
        val params = mapOf(
            PARAM_KEY_BROKER to brokerUrl,
            PARAM_KEY_ORPHANED_DIFF to childParentRecordDifference.toString(),
            PARAM_KEY_ORPHANED_COUNT to orphanedRecordsCount.toString(),
        )

        fire(PIR_WEEKLY_CHILD_ORPHANED_OPTOUTS, params)
    }

    override fun reportScanStarted(brokerUrl: String) {
        val params = mapOf(
            PARAM_KEY_BROKER to brokerUrl,
        )

        fire(PIR_SCAN_STARTED, params)
    }

    override fun reportScanStage(
        brokerUrl: String,
        brokerVersion: String,
        tries: Int,
        parentUrl: String,
        actionId: String,
        actionType: String,
    ) {
        val params = mapOf(
            PARAM_KEY_BROKER to brokerUrl,
            PARAM_BROKER_VERSION to brokerVersion,
            PARAM_TRIES to tries.toString(),
            PARAM_KEY_PARENT to parentUrl,
            PARAM_ACTION_ID to actionId,
            PARAM_KEY_ACTION_TYPE to actionType,
        )

        fire(PIR_SCAN_STAGE, params)
    }

    override fun reportScanMatches(
        brokerUrl: String,
        totalMatches: Int,
        durationMs: Long,
        inManualStarted: Boolean,
        parentUrl: String,
        isVpnRunning: Boolean,
    ) {
        val params = mapOf(
            PARAM_KEY_BROKER to brokerUrl,
            PARAM_KEY_MATCHES_COUNT to totalMatches.toString(),
            PARAM_DURATION to durationMs.toString(),
            PARAM_KEY_PARENT to parentUrl,
            PARAM_KEY_MANUAL_STARTED to inManualStarted.toString(),
            PARAM_KEY_PARENT to parentUrl,
            PARAM_KEY_VPN_STATE to isVpnRunning.toVpnConnectionState(),
        )

        fire(PIR_SCAN_STAGE_RESULT_MATCHES, params)
    }

    override fun reportScanNoMatch(
        brokerUrl: String,
        brokerVersion: String,
        durationMs: Long,
        inManualStarted: Boolean,
        parentUrl: String,
        actionId: String,
        actionType: String,
        isVpnRunning: Boolean,
    ) {
        val params = mapOf(
            PARAM_KEY_BROKER to brokerUrl,
            PARAM_BROKER_VERSION to brokerVersion,
            PARAM_DURATION to durationMs.toString(),
            PARAM_KEY_MANUAL_STARTED to inManualStarted.toString(),
            PARAM_KEY_PARENT to parentUrl,
            PARAM_ACTION_ID to actionId,
            PARAM_KEY_ACTION_TYPE to actionType,
            PARAM_KEY_VPN_STATE to isVpnRunning.toVpnConnectionState(),
        )

        fire(PIR_SCAN_STAGE_RESULT_NO_MATCH, params)
    }

    override fun reportScanError(
        brokerUrl: String,
        brokerVersion: String,
        durationMs: Long,
        errorCategory: String,
        errorDetails: String,
        inManualStarted: Boolean,
        parentUrl: String,
        actionId: String,
        actionType: String,
        isVpnRunning: Boolean,
    ) {
        val params = mapOf(
            PARAM_KEY_BROKER to brokerUrl,
            PARAM_BROKER_VERSION to brokerVersion,
            PARAM_DURATION to durationMs.toString(),
            PARAM_KEY_ERROR_CATEGORY to errorCategory,
            PARAM_KEY_ERROR_DETAILS to errorDetails,
            PARAM_KEY_MANUAL_STARTED to inManualStarted.toString(),
            PARAM_KEY_PARENT to parentUrl,
            PARAM_ACTION_ID to actionId,
            PARAM_KEY_ACTION_TYPE to actionType,
            PARAM_KEY_VPN_STATE to isVpnRunning.toVpnConnectionState(),
        )

        fire(PIR_SCAN_STAGE_RESULT_ERROR, params)
    }

    override fun reportOptOutStageStart(
        brokerUrl: String,
        parentUrl: String,
    ) {
        val defaultParams = mutableMapOf(
            PARAM_KEY_BROKER to brokerUrl,
            PARAM_KEY_PARENT to parentUrl,
        )

        fire(PIR_OPTOUT_STAGE_START, defaultParams)
    }

    override fun reportOptOutStageEmailGenerate(
        brokerUrl: String,
        parentUrl: String,
        brokerVersion: String,
        durationMs: Long,
        tries: Int,
        actionId: String,
    ) {
        fire(
            PIR_OPTOUT_STAGE_EMAIL_GENERATE,
            getFullStageParams(
                brokerUrl = brokerUrl,
                parentUrl = parentUrl,
                brokerVersion = brokerVersion,
                durationMs = durationMs,
                tries = tries,
                actionId = actionId,
            ),
        )
    }

    override fun reportOptOutStageCaptchaParse(
        brokerUrl: String,
        parentUrl: String,
        brokerVersion: String,
        durationMs: Long,
        tries: Int,
        actionId: String,
    ) {
        fire(
            PIR_OPTOUT_STAGE_CAPTCHA_PARSE,
            getFullStageParams(
                brokerUrl = brokerUrl,
                parentUrl = parentUrl,
                brokerVersion = brokerVersion,
                durationMs = durationMs,
                tries = tries,
                actionId = actionId,
            ),
        )
    }

    override fun reportOptOutStageCaptchaSend(
        brokerUrl: String,
        parentUrl: String,
        brokerVersion: String,
        durationMs: Long,
        tries: Int,
        actionId: String,
    ) {
        fire(
            PIR_OPTOUT_STAGE_CAPTCHA_SEND,
            getFullStageParams(
                brokerUrl = brokerUrl,
                parentUrl = parentUrl,
                brokerVersion = brokerVersion,
                durationMs = durationMs,
                tries = tries,
                actionId = actionId,
            ),
        )
    }

    override fun reportOptOutStageCaptchaSolve(
        brokerUrl: String,
        parentUrl: String,
        brokerVersion: String,
        durationMs: Long,
        tries: Int,
        actionId: String,
    ) {
        fire(
            PIR_OPTOUT_STAGE_CAPTCHA_SOLVE,
            getFullStageParams(
                brokerUrl = brokerUrl,
                parentUrl = parentUrl,
                brokerVersion = brokerVersion,
                durationMs = durationMs,
                tries = tries,
                actionId = actionId,
            ),
        )
    }

    override fun reportOptOutStageSubmit(
        brokerUrl: String,
        parentUrl: String,
        brokerVersion: String,
        durationMs: Long,
        tries: Int,
        actionId: String,
    ) {
        fire(
            PIR_OPTOUT_STAGE_SUBMIT,
            getFullStageParams(
                brokerUrl = brokerUrl,
                parentUrl = parentUrl,
                brokerVersion = brokerVersion,
                durationMs = durationMs,
                tries = tries,
                actionId = actionId,
            ),
        )
    }

    override fun reportOptOutStageValidate(
        brokerUrl: String,
        parentUrl: String,
        brokerVersion: String,
        durationMs: Long,
        tries: Int,
        actionId: String,
    ) {
        fire(
            PIR_OPTOUT_STAGE_VALIDATE,
            getFullStageParams(
                brokerUrl = brokerUrl,
                parentUrl = parentUrl,
                brokerVersion = brokerVersion,
                durationMs = durationMs,
                tries = tries,
                actionId = actionId,
            ),
        )
    }

    override fun reportOptOutStageFillForm(
        brokerUrl: String,
        parentUrl: String,
        brokerVersion: String,
        durationMs: Long,
        tries: Int,
        actionId: String,
    ) {
        fire(
            PIR_OPTOUT_STAGE_FILLFORM,
            getFullStageParams(
                brokerUrl = brokerUrl,
                parentUrl = parentUrl,
                brokerVersion = brokerVersion,
                durationMs = durationMs,
                tries = tries,
                actionId = actionId,
            ),
        )
    }

    override fun reportOptOutConditionFound(
        brokerUrl: String,
        parentUrl: String,
        brokerVersion: String,
        durationMs: Long,
        tries: Int,
        actionId: String,
    ) {
        fire(
            PIR_OPTOUT_STAGE_CONDITION_FOUND,
            getFullStageParams(
                brokerUrl = brokerUrl,
                parentUrl = parentUrl,
                brokerVersion = brokerVersion,
                durationMs = durationMs,
                tries = tries,
                actionId = actionId,
            ),
        )
    }

    override fun reportOptOutConditionNotFound(
        brokerUrl: String,
        parentUrl: String,
        brokerVersion: String,
        durationMs: Long,
        tries: Int,
        actionId: String,
    ) {
        fire(
            PIR_OPTOUT_STAGE_CONDITION_NOT_FOUND,
            getFullStageParams(
                brokerUrl = brokerUrl,
                parentUrl = parentUrl,
                brokerVersion = brokerVersion,
                durationMs = durationMs,
                tries = tries,
                actionId = actionId,
            ),
        )
    }

    private fun getFullStageParams(
        brokerUrl: String,
        parentUrl: String,
        brokerVersion: String,
        durationMs: Long,
        tries: Int,
        actionId: String,
    ): Map<String, String> {
        return mapOf(
            PARAM_KEY_BROKER to brokerUrl,
            PARAM_KEY_PARENT to parentUrl,
            PARAM_BROKER_VERSION to brokerVersion,
            PARAM_DURATION to durationMs.toString(),
            PARAM_TRIES to tries.toString(),
            PARAM_ACTION_ID to actionId,
        )
    }

    override fun reportOptOutStageFinish(
        brokerUrl: String,
        parentUrl: String,
        durationMs: Long,
    ) {
        val params = mapOf(
            PARAM_KEY_BROKER to brokerUrl,
            PARAM_KEY_PARENT to parentUrl,
            PARAM_DURATION to durationMs.toString(),
        )

        fire(PIR_OPTOUT_STAGE_FINISH, params)
    }

    override fun reportUpdateBrokerJsonSuccess(
        brokerJsonFileName: String,
        removedAtMs: Long,
    ) {
        val params = mapOf(
            PARAM_KEY_BROKER_JSON_FILE to brokerJsonFileName,
            PARAM_KEY_REMOVED_AT to removedAtMs.toString(),
        )

        fire(PIR_UPDATE_BROKER_SUCCESS, params)
    }

    override fun reportUpdateBrokerJsonFailure(
        brokerJsonFileName: String,
        removedAtMs: Long,
    ) {
        val params = mapOf(
            PARAM_KEY_BROKER_JSON_FILE to brokerJsonFileName,
            PARAM_KEY_REMOVED_AT to removedAtMs.toString(),
        )

        fire(PIR_UPDATE_BROKER_FAILURE, params)
    }

    override fun reportDownloadMainConfigBEFailure(errorCode: String) {
        val params = mapOf(
            PARAM_ERROR_CODE to errorCode,
        )

        fire(PIR_DOWNLOAD_MAINCONFIG_BE_FAILURE, params)
    }

    override fun reportDownloadMainConfigFailure() {
        fire(PIR_DOWNLOAD_MAINCONFIG_FAILURE)
    }

    override fun reportBrokerActionFailure(
        brokerUrl: String,
        brokerVersion: String,
        parentUrl: String,
        actionId: String,
        errorMessage: String,
        stepType: String,
    ) {
        val params = mapOf(
            PARAM_KEY_BROKER to brokerUrl,
            PARAM_KEY_PARENT to parentUrl,
            PARAM_BROKER_VERSION to brokerVersion,
            PARAM_ACTION_ID to actionId,
            PARAM_KEY_MSG to errorMessage,
            PARAM_KEY_STEP to stepType,
        )

        fire(PIR_BROKER_ACTION_FAILED, params)
    }

    override fun reportDashboardOpened() {
        fire(PIR_DASHBOARD_OPENED)
    }

    override fun reportInitialScanDuration(
        durationMs: Long,
        profileQueryCount: Int,
    ) {
        val params = mapOf(
            PARAM_KEY_DURATION_MS to durationMs.toString(),
            PARAM_KEY_PROFILE_QUERY_COUNT to profileQueryCount.toString(),
        )

        fire(PIR_INITIAL_SCAN_DURATION, params)
    }

    override fun reportBackgroundScanStats(scanFrequencyWithinThreshold: Boolean) {
        val params = mapOf(
            PARAM_KEY_SCAN_FREQUENCY to scanFrequencyWithinThreshold.toString(),
        )
        fire(PIR_BG_STATS, params)
    }

    override fun reportScanInvalidEvent(
        brokerUrl: String,
        brokerVersion: String,
    ) {
        val params = mapOf(
            PARAM_KEY_BROKER to brokerUrl,
            PARAM_BROKER_VERSION to brokerVersion,
        )
        fire(PIR_SCAN_INVALID_EVENT, params)
    }

    override fun reportOptOutInvalidEvent(
        brokerUrl: String,
        brokerVersion: String,
    ) {
        val params = mapOf(
            PARAM_KEY_BROKER to brokerUrl,
            PARAM_BROKER_VERSION to brokerVersion,
        )
        fire(PIR_OPTOUT_INVALID_EVENT, params)
    }

    private fun fire(
        pixel: PirPixel,
        params: Map<String, String> = emptyMap(),
    ) {
        pixel.getPixelNames().forEach { (pixelType, pixelName) ->
            logcat { "PIR-LOGGING: $pixelName params: $params" }
            pixelSender.fire(pixelName = pixelName, type = pixelType, parameters = params)
        }
    }

    private fun enqueueFire(
        pixel: PirPixel,
        params: Map<String, String> = emptyMap(),
    ) {
        pixel.getPixelNames().forEach { (_, pixelName) ->
            logcat { "PIR-LOGGING: $pixelName params: $params" }
            pixelSender.enqueueFire(pixelName = pixelName, parameters = params)
        }
    }

    companion object {
        private const val PARAM_KEY_TOTAL_TIME = "totalTimeInMillis"
        private const val PARAM_KEY_CPU_USAGE = "cpuUsage"
        private const val PARAM_BROKER_VERSION = "broker_version"
        private const val PARAM_LINK_AGE = "link_age_ms"
        private const val PARAM_STATUS = "status"
        private const val PARAM_ERROR_CODE = "error_code"
        private const val PARAM_ACTION_ID = "action_id"
        private const val PARAM_DURATION = "duration"
        private const val PARAM_TRIES = "tries"
        private const val PARAM_ATTEMPT_NUMBER = "attempt_number"
        private const val PARAM_TOTAL_FETCH = "totalFetchAttempts"
        private const val PARAM_TOTAL_EMAIL_CONFIRMATION = "totalEmailConfirmationJobs"

        private const val PARAM_KEY_BROKER = "data_broker"
        private const val PARAM_KEY_PARENT = "parent"
        private const val PARAM_KEY_STAGE = "stage"
        private const val PARAM_KEY_PATTERN = "pattern"
        private const val PARAM_KEY_ACTION_TYPE = "action_type"
        private const val PARAM_KEY_OPTOUT_SUBMIT_SUCCESS_RATE = "optout_submit_success_rate"
        private const val PARAM_KEY_ORPHANED_DIFF = "child-parent-record-difference"
        private const val PARAM_KEY_ORPHANED_COUNT = "calculated-orphaned-records"
        private const val PARAM_KEY_MATCHES_COUNT = "num_found"
        private const val PARAM_KEY_MANUAL_STARTED = "is_manual_scan"
        private const val PARAM_KEY_ERROR_CATEGORY = "error_category"
        private const val PARAM_KEY_ERROR_DETAILS = "error_details"
        private const val PARAM_KEY_BROKER_JSON_FILE = "data_broker_json_file"
        private const val PARAM_KEY_REMOVED_AT = "removed_at"
        private const val PARAM_KEY_MSG = "message"
        private const val PARAM_KEY_STEP = "stepType"
        private const val PARAM_KEY_DURATION_MS = "duration_in_ms"
        private const val PARAM_KEY_PROFILE_QUERY_COUNT = "profile_queries"
        private const val PARAM_KEY_SCAN_FREQUENCY = "scanFrequencyWithinThreshold"
        private const val PARAM_KEY_VPN_STATE = "vpn_connection_state"
    }
}
