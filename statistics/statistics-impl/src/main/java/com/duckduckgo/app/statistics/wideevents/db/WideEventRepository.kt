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

package com.duckduckgo.app.statistics.wideevents.db

import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.Flow

interface WideEventRepository {
    suspend fun insertWideEvent(
        name: String,
        flowEntryPoint: String?,
        metadata: Map<String, String?>,
        cleanupPolicy: CleanupPolicy,
    ): Long

    suspend fun addWideEventStep(
        eventId: Long,
        step: WideEventStep,
        metadata: Map<String, String?>,
    )

    suspend fun setWideEventStatus(
        eventId: Long,
        status: WideEventStatus,
        metadata: Map<String, String?>,
    )

    suspend fun deleteWideEvent(eventId: Long): Boolean

    suspend fun getActiveWideEventIds(): List<Long>

    suspend fun getActiveWideEventIdsByName(eventName: String): List<Long>

    fun getCompletedWideEventIdsFlow(): Flow<Set<Long>>

    suspend fun getWideEvents(ids: Set<Long>): List<WideEvent>

    suspend fun startInterval(
        eventId: Long,
        name: String,
        timeout: Duration?,
    )

    suspend fun endInterval(
        eventId: Long,
        name: String,
    ): Duration

    data class WideEvent(
        val id: Long,
        val name: String,
        val status: WideEventStatus?,
        val steps: List<WideEventStep>,
        val metadata: Map<String, String?>,
        val flowEntryPoint: String?,
        val cleanupPolicy: CleanupPolicy,
        val activeIntervals: List<WideEventInterval>,
        val createdAt: Instant,
    )

    data class WideEventStep(
        val name: String,
        val success: Boolean,
    )

    enum class WideEventStatus {
        SUCCESS,
        FAILURE,
        CANCELLED,
        UNKNOWN,
    }

    sealed class CleanupPolicy {
        abstract val status: WideEventStatus
        abstract val metadata: Map<String, String>

        data class OnProcessStart(
            val ignoreIfIntervalTimeoutPresent: Boolean,
            override val status: WideEventStatus,
            override val metadata: Map<String, String> = emptyMap(),
        ) : CleanupPolicy()

        data class OnTimeout(
            val duration: Duration,
            override val status: WideEventStatus,
            override val metadata: Map<String, String> = emptyMap(),
        ) : CleanupPolicy()
    }

    data class WideEventInterval(
        val name: String,
        val startedAt: Instant,
        val timeout: Duration?,
    )
}
