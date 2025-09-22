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
