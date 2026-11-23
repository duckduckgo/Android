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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

interface WideEventClient {
    /**
     * Begin a new flow.
     *
     * @param name Stable flow name (e.g., "subscription_purchase").
     * @param flowEntryPoint Optional identifier of the flow entry point (e.g., "app_settings")
     * @param metadata Optional metadata (e.g., "free_trial_eligible=true").
     * @param cleanupPolicy Strategy for dealing with abandoned events, see [CleanupPolicy] for details.
     * @param samplingProbability Probability in [0.0, 1.0] that this event will be recorded.
     *   E.g., 0.1 means ~10 % of events are kept and the rest are silently dropped.
     *   When an event is sampled out, [SAMPLED_OUT_FLOW_ID] is returned and all subsequent
     *   operations on that ID become no-ops.
     * @param definition Definition that this event conforms to, see [WideEventDefinition].
     * @return Wide event ID used for subsequent calls.
     */
    suspend fun flowStart(
        name: String,
        flowEntryPoint: String? = null,
        metadata: Map<String, String> = emptyMap(),
        cleanupPolicy: CleanupPolicy = OnTimeout(duration = 7.days),
        samplingProbability: Float = 1.0f,
        definition: WideEventDefinition = WideEventDefinition(),
    ): Result<Long>

    /**
     * Record a step's outcome in an active flow. Each step name is recorded once and sent as `step.<name>`
     * with its success flag.
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
     * Append [step] to the path of an active flow. The path is sent as `steps` (comma-separated), and its latest
     * step as `last_step`. Both replace a metadata entry with the same key.
     *
     * Step names must not contain commas, since commas separate entries in `steps`. Any comma in [step] is
     * dropped before it is appended.
     *
     * A step equal to the last appended one isn't added to the path again, but its [metadata] is still written,
     * so a flow can save what it learned without moving to a new step. When the path would exceed 20 entries,
     * it keeps its first 19 steps and ends with `truncated`; `last_step` still holds the latest step.
     *
     * Independent of [flowStep]: a flow can record step outcomes, a path, or both.
     *
     * @param wideEventId ID of the wide event.
     * @param step Step label (e.g., "code_screen"). Must not contain commas.
     * @param metadata Optional metadata.
     */
    suspend fun flowAppendToPath(
        wideEventId: Long,
        step: String,
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
     * Get ids of active flows by their name, regardless of the process that started them.
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
     * The measured duration is bucketed according to [buckets] and [rounding]. A null [buckets] disables
     * bucketing and records the raw duration.
     *
     * @param wideEventId ID of the wide event.
     * @param key Interval key (e.g., "token_refresh_duration").
     * @param timeout Optional duration for auto-finish.
     * @param buckets Bucket boundaries for the measured duration, or null to disable bucketing.
     *   Defaults to [DEFAULT_INTERVAL_BUCKETS].
     * @param rounding Which bucket boundary is recorded. Ignored when [buckets] is null.
     */
    suspend fun intervalStart(
        wideEventId: Long,
        key: String,
        timeout: Duration? = null,
        buckets: Set<Duration>? = DEFAULT_INTERVAL_BUCKETS,
        rounding: BucketRounding = BucketRounding.RoundDown,
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

    companion object {
        val DEFAULT_INTERVAL_BUCKETS: Set<Duration> = setOf(
            1.seconds,
            5.seconds,
            10.seconds,
            30.seconds,
            1.minutes,
            5.minutes,
            10.minutes,
        )

        /**
         * Sentinel ID returned by [flowStart] when the event is sampled out.
         * All subsequent operations on this ID are silent no-ops with zero disk I/O.
         */
        const val SAMPLED_OUT_FLOW_ID = -1L
    }
}

/**
 * Identifies the wide event definition that a flow conforms to.
 *
 * @param version Version of the event definition. Bump it whenever the set of parameters the event sends changes.
 * @param type Globally unique identifier of the event definition. Defaults to the flow name prefixed with
 *   the platform, e.g. "android-subscription-purchase".
 */
data class WideEventDefinition(
    val version: Version = Version.INITIAL,
    val type: String? = null,
) {
    /**
     * MINOR and PATCH components of the definition version, as declared in the definition itself.
     * MAJOR is shared by all wide events and set automatically.
     */
    data class Version(
        val minor: Int,
        val patch: Int,
    ) {
        companion object {
            val INITIAL = Version(minor = 0, patch = 0)
        }
    }
}

/**
 * How an interval's duration is mapped to a bucket boundary. A duration equal to a boundary records that
 * boundary. A negative duration, e.g. after a clock change, records -1 in both modes.
 */
enum class BucketRounding {
    /**
     * The largest boundary not greater than the duration. Durations shorter than the smallest boundary record 0.
     */
    RoundDown,

    /**
     * The smallest boundary not less than the duration. Durations longer than the largest boundary record the
     * largest boundary.
     */
    RoundUp,
}

/** Represents the final outcome status of a wide event. */
sealed class FlowStatus {
    /** The operation completed successfully */
    data object Success : FlowStatus()

    /** The operation failed */
    data class Failure(
        val reason: String,
    ) : FlowStatus()

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
     * Apply cleanup on the next start of the process that began the flow: the main process cleans up on startup, a secondary
     * process on its first wide-event operation. Ownership never moves to another process, so a flow that must outlive the
     * process that started it should use [OnTimeout] instead.
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
