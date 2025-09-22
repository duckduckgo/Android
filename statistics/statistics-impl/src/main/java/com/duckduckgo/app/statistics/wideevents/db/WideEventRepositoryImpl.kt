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

import androidx.room.withTransaction
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@ContributesBinding(AppScope::class)
class WideEventRepositoryImpl @Inject constructor(
    private val database: WideEventDatabase,
    private val wideEventDao: WideEventDao,
) : WideEventRepository {

    override suspend fun insertWideEvent(
        name: String,
        flowEntryPoint: String?,
        metadata: Map<String, String?>,
    ): Long {
        val entity = WideEventEntity(
            name = name,
            flowEntryPoint = flowEntryPoint,
            metadata = metadata.map { (key, value) -> WideEventEntity.MetadataEntry(key, value) },
            steps = emptyList(),
            status = null,
        )

        return wideEventDao.insertWideEvent(entity)
    }

    override suspend fun addWideEventStep(
        eventId: Long,
        step: WideEventRepository.WideEventStep,
        metadata: Map<String, String?>,
    ) {
        updateWideEvent(eventId) { event ->
            check(event.status == null) {
                "Event ${event.name} is already completed"
            }

            check(event.steps.none { it.name == step.name }) {
                "Step ${step.name} was already recorded for event ${event.name}"
            }

            event.copy(
                steps = event.steps + step.mapToDbWideEventStep(),
                metadata = mergeMetadata(event.metadata, metadata),
            )
        }
    }

    override suspend fun setWideEventStatus(
        eventId: Long,
        status: WideEventRepository.WideEventStatus,
        metadata: Map<String, String?>,
    ) {
        updateWideEvent(eventId) { event ->
            check(event.status == null) {
                "Event ${event.name} is already completed"
            }

            event.copy(
                status = status.mapToDbWideEventStatus(),
                metadata = mergeMetadata(event.metadata, metadata),
            )
        }
    }

    override suspend fun getActiveWideEventIdsByName(eventName: String): List<Long> {
        return wideEventDao.getActiveWideEventIdsByName(eventName)
    }

    override suspend fun deleteWideEvent(eventId: Long): Boolean {
        return wideEventDao.deleteWideEvent(eventId) > 0
    }

    override fun getCompletedWideEventIdsFlow(): Flow<Set<Long>> {
        return wideEventDao.getCompletedWideEventIdsFlow().map { it.toSet() }
    }

    override suspend fun getWideEvents(ids: Set<Long>): List<WideEventRepository.WideEvent> {
        if (ids.isEmpty()) return emptyList()

        return wideEventDao.getWideEventsByIds(ids)
            .map { it.mapToRepositoryWideEvent() }
    }

    private suspend fun updateWideEvent(
        id: Long,
        updateAction: (WideEventEntity) -> WideEventEntity,
    ) {
        database.withTransaction {
            val event = wideEventDao.getWideEventById(id)
                ?: throw NoSuchElementException("There is no event with given ID")

            val updatedEvent = updateAction(event)

            if (event != updatedEvent) {
                val updated = wideEventDao.updateWideEvent(updatedEvent) == 1
                check(updated) { "Failed to update wide event" }
            }
        }
    }
}

private fun mergeMetadata(
    existingMetadata: List<WideEventEntity.MetadataEntry>,
    newMetadata: Map<String, String?>,
): List<WideEventEntity.MetadataEntry> {
    if (newMetadata.isEmpty()) return existingMetadata

    val mergedMetadata: MutableMap<String, String?> = mutableMapOf()
    existingMetadata.forEach { mergedMetadata.put(it.key, it.value) }
    mergedMetadata.putAll(newMetadata)
    return mergedMetadata.map { WideEventEntity.MetadataEntry(it.key, it.value) }
}
