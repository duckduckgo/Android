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

fun WideEventEntity.mapToRepositoryWideEvent(): WideEventRepository.WideEvent =
    WideEventRepository.WideEvent(
        id = id,
        name = name,
        status = status?.mapToRepositoryWideEventStatus(),
        steps = steps.map { it.mapToRepositoryWideEventStep() },
        metadata = metadata.associate { it.key to it.value },
        flowEntryPoint = flowEntryPoint,
        cleanupPolicy = cleanupPolicy.mapToRepositoryCleanupPolicy(),
        activeIntervals = activeIntervals.map { it.mapToRepositoryWideEventInterval() },
        createdAt = createdAt,
    )

fun WideEventRepository.WideEventStatus.mapToDbWideEventStatus(): WideEventEntity.WideEventStatus =
    when (this) {
        WideEventRepository.WideEventStatus.SUCCESS -> WideEventEntity.WideEventStatus.SUCCESS
        WideEventRepository.WideEventStatus.FAILURE -> WideEventEntity.WideEventStatus.FAILURE
        WideEventRepository.WideEventStatus.CANCELLED -> WideEventEntity.WideEventStatus.CANCELLED
        WideEventRepository.WideEventStatus.UNKNOWN -> WideEventEntity.WideEventStatus.UNKNOWN
    }

fun WideEventEntity.WideEventStatus.mapToRepositoryWideEventStatus(): WideEventRepository.WideEventStatus =
    when (this) {
        WideEventEntity.WideEventStatus.SUCCESS -> WideEventRepository.WideEventStatus.SUCCESS
        WideEventEntity.WideEventStatus.FAILURE -> WideEventRepository.WideEventStatus.FAILURE
        WideEventEntity.WideEventStatus.CANCELLED -> WideEventRepository.WideEventStatus.CANCELLED
        WideEventEntity.WideEventStatus.UNKNOWN -> WideEventRepository.WideEventStatus.UNKNOWN
    }

fun WideEventRepository.WideEventStep.mapToDbWideEventStep(): WideEventEntity.WideEventStep =
    WideEventEntity.WideEventStep(name = name, success = success)

fun WideEventEntity.WideEventStep.mapToRepositoryWideEventStep(): WideEventRepository.WideEventStep =
    WideEventRepository.WideEventStep(name = name, success = success)

fun WideEventEntity.CleanupPolicy.mapToRepositoryCleanupPolicy(): WideEventRepository.CleanupPolicy =
    when (this) {
        is WideEventEntity.CleanupPolicy.OnProcessStart -> WideEventRepository.CleanupPolicy.OnProcessStart(
            ignoreIfIntervalTimeoutPresent = ignoreIfIntervalTimeoutPresent,
            status = status.mapToRepositoryWideEventStatus(),
            metadata = metadata,
        )
        is WideEventEntity.CleanupPolicy.OnTimeout -> WideEventRepository.CleanupPolicy.OnTimeout(
            duration = duration,
            status = status.mapToRepositoryWideEventStatus(),
            metadata = metadata,
        )
    }

fun WideEventRepository.CleanupPolicy.mapToDbCleanupPolicy(): WideEventEntity.CleanupPolicy =
    when (this) {
        is WideEventRepository.CleanupPolicy.OnProcessStart -> WideEventEntity.CleanupPolicy.OnProcessStart(
            ignoreIfIntervalTimeoutPresent = ignoreIfIntervalTimeoutPresent,
            status = status.mapToDbWideEventStatus(),
            metadata = metadata,
        )
        is WideEventRepository.CleanupPolicy.OnTimeout -> WideEventEntity.CleanupPolicy.OnTimeout(
            duration = duration,
            status = status.mapToDbWideEventStatus(),
            metadata = metadata,
        )
    }

fun WideEventEntity.WideEventInterval.mapToRepositoryWideEventInterval(): WideEventRepository.WideEventInterval =
    WideEventRepository.WideEventInterval(
        name = name,
        startedAt = startedAt,
        timeout = timeout,
    )

fun WideEventRepository.WideEventInterval.mapToDbWideEventInterval(): WideEventEntity.WideEventInterval =
    WideEventEntity.WideEventInterval(
        name = name,
        startedAt = startedAt,
        timeout = timeout,
    )
