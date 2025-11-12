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
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_BROKER_CUSTOM_STATS_14DAY_CONFIRMED_OPTOUT
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_BROKER_CUSTOM_STATS_14DAY_UNCONFIRMED_OPTOUT
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_BROKER_CUSTOM_STATS_21DAY_CONFIRMED_OPTOUT
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_BROKER_CUSTOM_STATS_21DAY_UNCONFIRMED_OPTOUT
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_BROKER_CUSTOM_STATS_42DAY_CONFIRMED_OPTOUT
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_BROKER_CUSTOM_STATS_42DAY_UNCONFIRMED_OPTOUT
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_BROKER_CUSTOM_STATS_7DAY_CONFIRMED_OPTOUT
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_BROKER_CUSTOM_STATS_7DAY_UNCONFIRMED_OPTOUT
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_BROKER_CUSTOM_STATS_OPTOUT_SUBMIT_SUCCESSRATE
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_EMAIL_CONFIRMATION_ATTEMPT_FAILED
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_EMAIL_CONFIRMATION_ATTEMPT_START
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_EMAIL_CONFIRMATION_ATTEMPT_SUCCESS
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_EMAIL_CONFIRMATION_JOB_SUCCESS
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_EMAIL_CONFIRMATION_LINK_BE_ERROR
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_EMAIL_CONFIRMATION_LINK_RECEIVED
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_EMAIL_CONFIRMATION_MAX_RETRIES_EXCEEDED
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_EMAIL_CONFIRMATION_RUN_COMPLETED
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_EMAIL_CONFIRMATION_RUN_STARTED
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_INTERNAL_BROKER_OPT_OUT_STARTED
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_INTERNAL_BROKER_SCAN_COMPLETED
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_INTERNAL_BROKER_SCAN_STARTED
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_INTERNAL_CPU_USAGE
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_INTERNAL_MANUAL_SCAN_COMPLETED
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_INTERNAL_MANUAL_SCAN_STARTED
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_INTERNAL_OPT_OUT_STATS
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_INTERNAL_SCAN_STATS
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_INTERNAL_SCHEDULED_SCAN_COMPLETED
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_INTERNAL_SCHEDULED_SCAN_SCHEDULED
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_INTERNAL_SCHEDULED_SCAN_STARTED
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_INTERNAL_SECURE_STORAGE_UNAVAILABLE
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_OPTOUT_STAGE_PENDING_EMAIL_CONFIRMATION
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_OPTOUT_SUBMIT_FAILURE
import com.duckduckgo.pir.impl.pixels.PirPixel.PIR_OPTOUT_SUBMIT_SUCCESS
import com.squareup.anvil.annotations.ContributesBinding
import logcat.logcat
import javax.inject.Inject

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
     * Emits a pixel to signal that a scan job for a specific broker x profile has been started.
     *
     * @param brokerName for which this scan job is for
     */
    fun reportBrokerScanStarted(
        brokerName: String,
    )

    /**
     * Emits a pixel to signal that a scan job for a specific broker x profile has been completed (status received: either error or success).
     *
     * @param brokerName - broker name
     * @param totalTimeInMillis - How long it took to complete the scan for the broker.
     * @param isSuccess - if result was not an error, it is a success. Doesn't tell us if the profile was found.
     */
    fun reportBrokerScanCompleted(
        brokerName: String,
        totalTimeInMillis: Long,
        isSuccess: Boolean,
    )

    /**
     * Emits a pixel to signal that an opt-out job for a specific extractedProfile has been started.
     *
     * @param brokerName Broker in which the ExtractedProfile being opted out was found.
     */
    fun reportOptOutStarted(
        brokerName: String,
    )

    /**
     * Emits a pixel to signal that an opt-out job for a specific extractedProfile has been successfully submitted.
     *
     * @param brokerUrl url of the Broker for which the opt-out is for
     * @param parent The parent data broker of the one this opt-out attempt targets
     * @param attemptId - Client-generated ID of the opt-out attempt
     * @param durationMs - Total duration of the opt-out attempt in milliseconds
     * @param tries - The number of tries it took to submit successfully.
     * @param emailPattern - Email pattern used during submission, when available else null.
     */
    fun reportOptOutSubmitted(
        brokerUrl: String,
        parent: String,
        attemptId: String,
        durationMs: Long,
        tries: Int,
        emailPattern: String?,
    )

    /**
     * Emits a pixel to signal that an opt-out job for a specific extractedProfile has been failed.
     *
     * @param brokerUrl url of the Broker for which the opt-out is for
     * @param parent The parent data broker of the one this opt-out attempt targets
     * @param brokerJsonVersion The version of the broker JSON file
     * @param attemptId - Client-generated ID of the opt-out attempt
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
        attemptId: String,
        durationMs: Long,
        stage: PirStage,
        tries: Int,
        emailPattern: String?,
        actionId: String,
        actionType: String,
    )

    /**
     * Emits a pixel that contains the scan related stats for the current run.
     *
     * @param totalScanToRun The total number of scans that are eligible for the current run.
     */
    fun reportScanStats(
        totalScanToRun: Int,
    )

    /**
     * Emits a pixel that contains the opt-out related stats for the current run.
     *
     * @param totalOptOutsToRun The total number of opt-outs that are eligible for the current run.
     */
    fun reportOptOutStats(
        totalOptOutsToRun: Int,
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
     * @param attemptId The ID of the opt out attempt
     * @param actionId The ID of the action, to disambiguate between multiple actions in the same opt out attempt
     * @param durationMs The duration of the action execution in milliseconds
     * @param tries The number of tries it took for the action to complete
     */
    fun reportStagePendingEmailConfirmation(
        brokerUrl: String,
        brokerVersion: String,
        attemptId: String,
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
     * @param attemptId The ID of the opt out attempt
     * @param actionId The ID of the action, to disambiguate between multiple actions in the same opt out attempt
     */
    fun reportEmailConfirmationAttemptStart(
        brokerUrl: String,
        brokerVersion: String,
        attemptNumber: Int,
        attemptId: String,
        actionId: String,
    )

    /**
     * Emits a pixel when an email confirmation attempt succeeds
     *
     * @param brokerUrl The URL of the data broker that this action was operating on
     * @param brokerVersion The version of the broker JSON file
     * @param attemptNumber The confirmation attempt number (1..3)
     * @param attemptId The ID of the opt out attempt
     * @param actionId The ID of the action, to disambiguate between multiple actions in the same opt out attempt
     * @param durationMs The duration of the attempt in milliseconds
     */
    fun reportEmailConfirmationAttemptSuccess(
        brokerUrl: String,
        brokerVersion: String,
        attemptNumber: Int,
        attemptId: String,
        actionId: String,
        durationMs: Long,
    )

    /**
     * Emits a pixel when an email confirmation attempt fails (non-final).
     *
     * @param brokerUrl The URL of the data broker that this action was operating on
     * @param brokerVersion The version of the broker JSON file
     * @param attemptNumber The confirmation attempt number (1..3)
     * @param attemptId The ID of the opt out attempt
     * @param actionId The ID of the action, to disambiguate between multiple actions in the same opt out attempt
     * @param durationMs The duration of the attempt in milliseconds
     */
    fun reportEmailConfirmationAttemptFailed(
        brokerUrl: String,
        brokerVersion: String,
        attemptNumber: Int,
        attemptId: String,
        actionId: String,
        durationMs: Long,
    )

    /**
     * Emits a pixel when the email confirmation job hits the maximum number of retries (final failure).
     *
     * @param brokerUrl The URL of the data broker that this action was operating on
     * @param brokerVersion The version of the broker JSON file
     * @param attemptId The ID of the opt out attempt
     * @param actionId The ID of the action, to disambiguate between multiple actions in the same opt out attempt
     */
    fun reportEmailConfirmationAttemptRetriesExceeded(
        brokerUrl: String,
        brokerVersion: String,
        attemptId: String,
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
}

@ContributesBinding(AppScope::class)
class RealPirPixelSender @Inject constructor(
    private val pixelSender: Pixel,
) : PirPixelSender {
    override fun reportManualScanStarted() {
        fire(PIR_INTERNAL_MANUAL_SCAN_STARTED)
    }

    override fun reportManualScanCompleted(
        totalTimeInMillis: Long,
    ) {
        val params = mapOf(
            PARAM_KEY_TOTAL_TIME to totalTimeInMillis.toString(),
        )
        fire(PIR_INTERNAL_MANUAL_SCAN_COMPLETED, params)
    }

    override fun reportBrokerScanStarted(brokerName: String) {
        val params = mapOf(
            PARAM_KEY_BROKER_NAME to brokerName,
        )
        fire(PIR_INTERNAL_BROKER_SCAN_STARTED, params)
    }

    override fun reportBrokerScanCompleted(
        brokerName: String,
        totalTimeInMillis: Long,
        isSuccess: Boolean,
    ) {
        val params = mapOf(
            PARAM_KEY_BROKER_NAME to brokerName,
            PARAM_KEY_TOTAL_TIME to totalTimeInMillis.toString(),
            PARAM_KEY_SUCCESS to isSuccess.toString(),
        )
        fire(PIR_INTERNAL_BROKER_SCAN_COMPLETED, params)
    }

    override fun reportScheduledScanScheduled() {
        fire(PIR_INTERNAL_SCHEDULED_SCAN_SCHEDULED)
    }

    override fun reportScheduledScanStarted() {
        fire(PIR_INTERNAL_SCHEDULED_SCAN_STARTED)
    }

    override fun reportScheduledScanCompleted(
        totalTimeInMillis: Long,
    ) {
        val params = mapOf(
            PARAM_KEY_TOTAL_TIME to totalTimeInMillis.toString(),
        )
        fire(PIR_INTERNAL_SCHEDULED_SCAN_COMPLETED, params)
    }

    override fun reportOptOutStarted(
        brokerName: String,
    ) {
        val params = mapOf(
            PARAM_KEY_BROKER_NAME to brokerName,
        )
        fire(PIR_INTERNAL_BROKER_OPT_OUT_STARTED, params)
    }

    override fun reportOptOutSubmitted(
        brokerUrl: String,
        parent: String,
        attemptId: String,
        durationMs: Long,
        tries: Int,
        emailPattern: String?,
    ) {
        val params = mapOf(
            PARAM_KEY_BROKER to brokerUrl,
            PARAM_KEY_PARENT to parent,
            PARAM_ATTEMPT_ID to attemptId,
            PARAM_DURATION to durationMs.toString(),
            PARAM_TRIES to tries.toString(),
            PARAM_KEY_PATTERN to (emailPattern ?: ""),
        )
        fire(PIR_OPTOUT_SUBMIT_SUCCESS, params)
    }

    override fun reportOptOutFailed(
        brokerUrl: String,
        parent: String,
        brokerJsonVersion: String,
        attemptId: String,
        durationMs: Long,
        stage: PirStage,
        tries: Int,
        emailPattern: String?,
        actionId: String,
        actionType: String,
    ) {
        val params = mapOf(
            PARAM_KEY_BROKER to brokerUrl,
            PARAM_KEY_PARENT to parent,
            PARAM_BROKER_VERSION to brokerJsonVersion,
            PARAM_ATTEMPT_ID to attemptId,
            PARAM_DURATION to durationMs.toString(),
            PARAM_KEY_STAGE to stage.stageName,
            PARAM_TRIES to tries.toString(),
            PARAM_KEY_PATTERN to (emailPattern ?: ""),
            PARAM_ACTION_ID to actionId,
            PARAM_KEY_ACTION_TYPE to actionType,
        )

        fire(PIR_OPTOUT_SUBMIT_FAILURE, params)
    }

    override fun reportScanStats(totalScanToRun: Int) {
        val params = mapOf(
            PARAM_KEY_SCAN_COUNT to totalScanToRun.toString(),
        )
        fire(PIR_INTERNAL_SCAN_STATS, params)
    }

    override fun reportOptOutStats(totalOptOutsToRun: Int) {
        val params = mapOf(
            PARAM_KEY_OPTOUT_COUNT to totalOptOutsToRun.toString(),
        )
        fire(PIR_INTERNAL_OPT_OUT_STATS, params)
    }

    override fun sendCPUUsageAlert(averageCpuUsagePercent: Int) {
        val params = mapOf(
            PARAM_KEY_CPU_USAGE to averageCpuUsagePercent.toString(),
        )
        fire(PIR_INTERNAL_CPU_USAGE, params)
    }

    override fun reportEmailConfirmationLinkFetched(
        brokerUrl: String,
        brokerVersion: String,
        linkAgeMs: Long,
    ) {
        val params = mapOf(
            PARAM_BROKER_URL to brokerUrl,
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
            PARAM_BROKER_URL to brokerUrl,
            PARAM_BROKER_VERSION to brokerVersion,
            PARAM_STATUS to status,
            PARAM_ERROR_CODE to errorCode,
        )
        fire(PIR_EMAIL_CONFIRMATION_LINK_BE_ERROR, params)
    }

    override fun reportStagePendingEmailConfirmation(
        brokerUrl: String,
        brokerVersion: String,
        attemptId: String,
        actionId: String,
        durationMs: Long,
        tries: Int,
    ) {
        val params = mapOf(
            PARAM_BROKER_URL to brokerUrl,
            PARAM_BROKER_VERSION to brokerVersion,
            PARAM_ATTEMPT_ID to attemptId,
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
        attemptId: String,
        actionId: String,
    ) {
        val params = mapOf(
            PARAM_BROKER_URL to brokerUrl,
            PARAM_BROKER_VERSION to brokerVersion,
            PARAM_ATTEMPT_NUMBER to attemptNumber.toString(),
            PARAM_ATTEMPT_ID to attemptId,
            PARAM_ACTION_ID to actionId,
        )
        fire(PIR_EMAIL_CONFIRMATION_ATTEMPT_START, params)
    }

    override fun reportEmailConfirmationAttemptSuccess(
        brokerUrl: String,
        brokerVersion: String,
        attemptNumber: Int,
        attemptId: String,
        actionId: String,
        durationMs: Long,
    ) {
        val params = mapOf(
            PARAM_BROKER_URL to brokerUrl,
            PARAM_BROKER_VERSION to brokerVersion,
            PARAM_ATTEMPT_NUMBER to attemptNumber.toString(),
            PARAM_ATTEMPT_ID to attemptId,
            PARAM_ACTION_ID to actionId,
            PARAM_DURATION to durationMs.toString(),
        )
        fire(PIR_EMAIL_CONFIRMATION_ATTEMPT_SUCCESS, params)
    }

    override fun reportEmailConfirmationAttemptFailed(
        brokerUrl: String,
        brokerVersion: String,
        attemptNumber: Int,
        attemptId: String,
        actionId: String,
        durationMs: Long,
    ) {
        val params = mapOf(
            PARAM_BROKER_URL to brokerUrl,
            PARAM_BROKER_VERSION to brokerVersion,
            PARAM_ATTEMPT_NUMBER to attemptNumber.toString(),
            PARAM_ATTEMPT_ID to attemptId,
            PARAM_ACTION_ID to actionId,
            PARAM_DURATION to durationMs.toString(),
        )
        fire(PIR_EMAIL_CONFIRMATION_ATTEMPT_FAILED, params)
    }

    override fun reportEmailConfirmationAttemptRetriesExceeded(
        brokerUrl: String,
        brokerVersion: String,
        attemptId: String,
        actionId: String,
    ) {
        val params = mapOf(
            PARAM_BROKER_URL to brokerUrl,
            PARAM_BROKER_VERSION to brokerVersion,
            PARAM_ATTEMPT_ID to attemptId,
            PARAM_ACTION_ID to actionId,
        )
        fire(PIR_EMAIL_CONFIRMATION_MAX_RETRIES_EXCEEDED, params)
    }

    override fun reportEmailConfirmationJobSuccess(
        brokerUrl: String,
        brokerVersion: String,
    ) {
        val params = mapOf(
            PARAM_BROKER_URL to brokerUrl,
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

    private fun fire(
        pixel: PirPixel,
        params: Map<String, String> = emptyMap(),
    ) {
        pixel.getPixelNames().forEach { (pixelType, pixelName) ->
            logcat { "PIR-LOGGING: $pixelName params: $params" }
            pixelSender.fire(pixelName = pixelName, type = pixelType, parameters = params)
        }
    }

    companion object {
        private const val PARAM_KEY_BROKER_NAME = "brokerName"
        private const val PARAM_KEY_TOTAL_TIME = "totalTimeInMillis"
        private const val PARAM_KEY_SUCCESS = "isSuccess"
        private const val PARAM_KEY_SCAN_COUNT = "totalScanToRun"
        private const val PARAM_KEY_OPTOUT_COUNT = "totalOptOutToRun"
        private const val PARAM_KEY_CPU_USAGE = "cpuUsage"
        private const val PARAM_BROKER_URL = "data_broker_url"
        private const val PARAM_BROKER_VERSION = "broker_version"
        private const val PARAM_LINK_AGE = "link_age_ms"
        private const val PARAM_STATUS = "status"
        private const val PARAM_ERROR_CODE = "error_code"
        private const val PARAM_ATTEMPT_ID = "attempt_id"
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
    }
}
