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

import kotlinx.coroutines.flow.Flow

interface WideEventRepository {
    suspend fun insertWideEvent(
        name: String,
        flowEntryPoint: String?,
        metadata: Map<String, String?>,
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

    suspend fun getActiveWideEventIdsByName(eventName: String): List<Long>

    fun getCompletedWideEventIdsFlow(): Flow<Set<Long>>

    suspend fun getWideEvents(ids: Set<Long>): List<WideEvent>

    data class WideEvent(
        val id: Long,
        val name: String,
        val status: WideEventStatus?,
        val steps: List<WideEventStep>,
        val metadata: Map<String, String?>,
        val flowEntryPoint: String?,
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
}
