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

package com.duckduckgo.app.statistics.wideevents

import com.duckduckgo.app.statistics.wideevents.CleanupPolicy.OnTimeout
import java.time.Duration

interface WideEventClient {

    /**
     * Begin a new flow.
     *
     * @param name Stable flow name (e.g., "subscription_purchase").
     * @param flowEntryPoint Optional identifier of the flow entry point (e.g., "app_settings")
     * @param metadata Optional metadata (e.g., "free_trial_eligible=true").
     * @param cleanupPolicy Strategy for dealing with abandoned events, see [CleanupPolicy] for details.
     * @return Wide event ID used for subsequent calls.
     */
    suspend fun flowStart(
        name: String,
        flowEntryPoint: String? = null,
        metadata: Map<String, String> = emptyMap(),
        cleanupPolicy: CleanupPolicy = OnTimeout(duration = Duration.ofDays(7)),
    ): Result<Long>

    /**
     * Record a step in an active flow.
     *
     * @param wideEventId ID of the wide event.
     * @param stepName Step label (e.g., "account_created").
     * @param success Whether this step succeeded.
     * @param metadata Optional metadata.
     */
    suspend fun flowStep(
        wideEventId: Long,
        stepName: String,
        success: Boolean = true,
        metadata: Map<String, String> = emptyMap(),
    ): Result<Unit>

    /**
     * Finish an active flow and send it with a terminal status.
     *
     * @param wideEventId ID of the wide event.
     * @param status Status of the entire flow.
     * @param metadata Optional metadata.
     */
    suspend fun flowFinish(
        wideEventId: Long,
        status: FlowStatus,
        metadata: Map<String, String> = emptyMap(),
    ): Result<Unit>

    /**
     * Abort an active flow and discard all data (nothing is sent).
     *
     * @param wideEventId ID of the wide event.
     */
    suspend fun flowAbort(wideEventId: Long): Result<Unit>

    /**
     * Get ids of active flows by their name.
     * Ids are ordered chronologically based on wide event creation time.
     *
     * Use to pick up wideEventId after process restart or if the flow started
     * in a different module. Or to clean up abandoned events.
     *
     * @param name Stable flow name that was previously used with [flowStart].
     */
    suspend fun getFlowIds(name: String): Result<List<Long>>

    /**
     * Start a named interval inside the flow.
     *
     * If [timeout] elapses before [intervalEnd], [flowFinish] or [flowAbort], the flow auto-finishes with
     * [FlowStatus.Unknown] and is sent. Explicit finish/abort cancels any pending timeouts.
     *
     * @param wideEventId ID of the wide event.
     * @param key Interval key (e.g., "token_refresh_duration").
     * @param timeout Optional duration for auto-finish.
     */
    suspend fun intervalStart(
        wideEventId: Long,
        key: String,
        timeout: Duration? = null,
    ): Result<Unit>

    /**
     * End a previously started interval.
     *
     * Cancels any timeout set by the corresponding [intervalStart].
     *
     * @param wideEventId ID of the wide event.
     * @param key Interval key passed to [intervalStart].
     * @return Duration of the interval.
     */
    suspend fun intervalEnd(
        wideEventId: Long,
        key: String,
    ): Result<Duration>
}

/** Represents the final outcome status of a wide event. */
sealed class FlowStatus {

    /** The operation completed successfully */
    data object Success : FlowStatus()

    /** The operation failed */
    data class Failure(val reason: String) : FlowStatus()

    /** The operation was cancelled by the user */
    data object Cancelled : FlowStatus()

    /** The final status could not be determined */
    data object Unknown : FlowStatus()
}

/**
 * Per-flow cleanup behavior applied when a flow is left open.
 *
 * If the flow is explicitly finished or aborted before conditions defined by the policy are met,
 * the policy has no effect.
 *
 * Each policy carries a target [flowStatus] - the flow is auto-finished with that status and sent.
 */
sealed class CleanupPolicy {

    /**
     * The status to use when this policy triggers.
     */
    abstract val flowStatus: FlowStatus

    /**
     * Apply cleanup on the next (main) process start for any still-open flow.
     *
     * If [ignoreIfIntervalTimeoutPresent] is true and the flow has a pending interval timeout,
     * this policy is skipped so the interval timeout can handle cleanup.
     *
     * @param ignoreIfIntervalTimeoutPresent When true, do not apply this policy if an interval timeout exists.
     */
    data class OnProcessStart(
        val ignoreIfIntervalTimeoutPresent: Boolean,
        override val flowStatus: FlowStatus = FlowStatus.Unknown,
    ) : CleanupPolicy()

    /**
     * Apply cleanup if a flow stays open beyond a specified time.
     *
     * - This API does not guarantee immediate cleanup as soon as the timeout expires — the event becomes eligible
     *   for cleanup after the duration has passed, and the actual cleanup may occur later.
     *
     * @param duration Duration after flow start for it to become eligible for cleanup.
     */
    data class OnTimeout(
        val duration: Duration,
        override val flowStatus: FlowStatus = FlowStatus.Unknown,
    ) : CleanupPolicy()
}
