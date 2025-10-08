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
import com.duckduckgo.app.statistics.wideevents.db.WideEventRepository.CleanupPolicy
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class WideEventRepositoryImpl @Inject constructor(
    private val database: WideEventDatabase,
    private val wideEventDao: WideEventDao,
    private val timeProvider: CurrentTimeProvider,
) : WideEventRepository {
    override suspend fun insertWideEvent(
        name: String,
        flowEntryPoint: String?,
        metadata: Map<String, String?>,
        cleanupPolicy: CleanupPolicy,
    ): Long {
        val entity =
            WideEventEntity(
                name = name,
                flowEntryPoint = flowEntryPoint,
                metadata = metadata.map { (key, value) -> WideEventEntity.MetadataEntry(key, value) },
                steps = emptyList(),
                status = null,
                createdAt = timeProvider.getCurrentTime(),
                cleanupPolicy = cleanupPolicy.mapToDbCleanupPolicy(),
                activeIntervals = emptyList(),
            )

        return wideEventDao.insertWideEvent(entity)
    }

    override suspend fun addWideEventStep(
        eventId: Long,
        step: WideEventRepository.WideEventStep,
        metadata: Map<String, String?>,
    ) {
        updateWideEvent(eventId) { event ->
            checkEventIsActive(event)

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
            checkEventIsActive(event)

            event.copy(
                status = status.mapToDbWideEventStatus(),
                metadata = mergeMetadata(event.metadata, metadata),
            )
        }
    }

    override suspend fun getActiveWideEventIds(): List<Long> = wideEventDao.getActiveWideEventIds()

    override suspend fun getActiveWideEventIdsByName(eventName: String): List<Long> = wideEventDao.getActiveWideEventIdsByName(eventName)

    override suspend fun deleteWideEvent(eventId: Long): Boolean = wideEventDao.deleteWideEvent(eventId) > 0

    override fun getCompletedWideEventIdsFlow(): Flow<Set<Long>> = wideEventDao.getCompletedWideEventIdsFlow().map { it.toSet() }

    override suspend fun getWideEvents(ids: Set<Long>): List<WideEventRepository.WideEvent> {
        if (ids.isEmpty()) return emptyList()

        return wideEventDao
            .getWideEventsByIds(ids)
            .map { it.mapToRepositoryWideEvent() }
    }

    override suspend fun startInterval(
        eventId: Long,
        name: String,
        timeout: Duration?,
    ) {
        updateWideEvent(eventId) { event ->
            checkEventIsActive(event)

            check(event.activeIntervals.none { it.name == name }) {
                "Interval $name is already started for event ${event.name}"
            }

            val interval =
                WideEventEntity.WideEventInterval(
                    name = name,
                    startedAt = timeProvider.getCurrentTime(),
                    timeout = timeout,
                )

            event.copy(activeIntervals = event.activeIntervals + interval)
        }
    }

    override suspend fun endInterval(
        eventId: Long,
        name: String,
    ): Duration {
        lateinit var duration: Duration

        updateWideEvent(eventId) { event ->
            checkEventIsActive(event)

            val interval =
                checkNotNull(event.activeIntervals.find { it.name == name }) {
                    "There is no active interval $name for event ${event.name}"
                }

            duration = Duration.between(interval.startedAt, timeProvider.getCurrentTime())
            val durationBucket = INTERVAL_BUCKETS.firstOrNull { it >= duration } ?: INTERVAL_BUCKETS.last()

            event.copy(
                metadata =
                mergeMetadata(
                    existingMetadata = event.metadata,
                    newMetadata = mapOf(interval.name to durationBucket.toMillis().toString()),
                ),
                activeIntervals = event.activeIntervals - interval,
            )
        }

        return duration
    }

    private suspend fun updateWideEvent(
        id: Long,
        updateAction: (WideEventEntity) -> WideEventEntity,
    ) {
        database.withTransaction {
            val event =
                wideEventDao.getWideEventById(id)
                    ?: throw NoSuchElementException("There is no event with given ID")

            val updatedEvent = updateAction(event)

            if (event != updatedEvent) {
                val updateSuccessful = wideEventDao.updateWideEvent(updatedEvent) == 1
                check(updateSuccessful) { "Failed to update wide event" }
            }
        }
    }

    private companion object {
        val INTERVAL_BUCKETS =
            listOf(
                Duration.ofSeconds(1),
                Duration.ofSeconds(5),
                Duration.ofSeconds(10),
                Duration.ofSeconds(30),
                Duration.ofMinutes(1),
                Duration.ofMinutes(5),
                Duration.ofMinutes(10),
            )
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

private fun CurrentTimeProvider.getCurrentTime(): Instant = Instant.ofEpochMilli(currentTimeMillis())

private fun checkEventIsActive(event: WideEventEntity) =
    check(event.status == null) {
        "Event ${event.name} is already completed"
    }
