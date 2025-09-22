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

import com.duckduckgo.app.statistics.api.FlowStatus
import com.duckduckgo.app.statistics.api.WideEventClient
import com.duckduckgo.app.statistics.wideevents.db.WideEventRepository
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlin.runCatching

@ContributesBinding(AppScope::class)
class WideEventClientImpl @Inject constructor(
    private val wideEventRepository: WideEventRepository,
) : WideEventClient {

    override suspend fun flowStart(
        name: String,
        flowEntryPoint: String?,
        metadata: Map<String, String>,
    ): Result<Long> = runCatching {
        wideEventRepository.insertWideEvent(
            name = name,
            flowEntryPoint = flowEntryPoint,
            metadata = metadata,
        )
    }

    override suspend fun flowStep(
        wideEventId: Long,
        stepName: String,
        success: Boolean,
        metadata: Map<String, String>,
    ): Result<Unit> = runCatching {
        wideEventRepository.addWideEventStep(
            eventId = wideEventId,
            step = WideEventRepository.WideEventStep(name = stepName, success = success),
            metadata = metadata,
        )
    }

    override suspend fun flowFinish(
        wideEventId: Long,
        status: FlowStatus,
        metadata: Map<String, String>,
    ): Result<Unit> = runCatching {
        val (wideEventStatus, statusMetadata) = when (status) {
            FlowStatus.Cancelled -> WideEventRepository.WideEventStatus.CANCELLED to emptyMap()
            is FlowStatus.Failure -> WideEventRepository.WideEventStatus.FAILURE to mapOf("failure_reason" to status.reason)
            FlowStatus.Success -> WideEventRepository.WideEventStatus.SUCCESS to emptyMap()
            FlowStatus.Unknown -> WideEventRepository.WideEventStatus.UNKNOWN to emptyMap()
        }

        wideEventRepository.setWideEventStatus(
            eventId = wideEventId,
            status = wideEventStatus,
            metadata = metadata + statusMetadata,
        )
    }

    override suspend fun flowAbort(wideEventId: Long): Result<Unit> = runCatching {
        val deleted = wideEventRepository.deleteWideEvent(wideEventId)
        check(deleted) { "There is no event with given ID" }
    }

    override suspend fun getFlowIds(name: String): Result<List<Long>> = runCatching {
        wideEventRepository.getActiveWideEventIdsByName(name)
    }
}
