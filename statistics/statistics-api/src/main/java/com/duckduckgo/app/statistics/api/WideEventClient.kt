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

package com.duckduckgo.app.statistics.api

interface WideEventClient {

    /**
     * Begin a new flow.
     *
     * @param name Stable flow name (e.g., "subscription_purchase").
     * @param flowEntryPoint Optional identifier of the flow entry point (e.g., "app_settings")
     * @param metadata Optional metadata (e.g., "free_trial_eligible=true").
     * @return Wide event ID used for subsequent calls.
     */
    suspend fun flowStart(
        name: String,
        flowEntryPoint: String? = null,
        metadata: Map<String, String> = emptyMap(),
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
        success: Boolean,
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
