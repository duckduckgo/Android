/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.autofill.sync

import com.duckduckgo.autofill.store.CredentialsSyncMetadataDao
import com.duckduckgo.autofill.store.CredentialsSyncMetadataEntity
import com.duckduckgo.di.scopes.AppScope
import dagger.SingleInstanceIn
import kotlinx.coroutines.flow.Flow
import logcat.LogPriority.INFO
import logcat.logcat
import javax.inject.Inject

@SingleInstanceIn(AppScope::class)
class CredentialsSyncMetadata @Inject constructor(
    private val dao: CredentialsSyncMetadataDao,
) {

    fun initializeDatabase(autofillIds: List<Long>) {
        val entitiesMetadata = autofillIds.map {
            CredentialsSyncMetadataEntity(localId = it, deleted_at = null, modified_at = SyncDateProvider.now())
        }
        dao.initialize(entitiesMetadata)
    }

    fun addOrUpdate(entity: CredentialsSyncMetadataEntity) {
        dao.insert(entity)
    }

    fun getSyncMetadata(localId: Long): CredentialsSyncMetadataEntity? {
        return dao.getSyncMetadata(localId)
    }

    fun createSyncId(localId: Long): String {
        var syncId = getSyncMetadata(localId)?.syncId
        if (syncId == null) {
            val entity = CredentialsSyncMetadataEntity(localId = localId, deleted_at = null, modified_at = null)
            dao.insert(entity)
            syncId = entity.syncId
        }
        return syncId
    }

    fun getLocalId(syncId: String): Long? {
        return dao.getLocalId(syncId)
    }

    fun getRemovedEntitiesSince(since: Iso8601String): List<CredentialsSyncMetadataEntity> {
        return dao.getRemovedIdsSince(since)
    }

    fun onEntityChanged(localId: Long) {
        val currentTime = SyncDateProvider.now()
        val entity = getOrCreateChangedEntity(localId, currentTime)
        dao.insert(entity)
    }

    fun onEntitiesChanged(localIds: List<Long>) {
        val currentTime = SyncDateProvider.now()
        val toBeUpdated = localIds.map { getOrCreateChangedEntity(it, currentTime) }
        dao.insert(toBeUpdated)
    }

    private fun getOrCreateChangedEntity(localId: Long, currentTime: String): CredentialsSyncMetadataEntity {
        val syncMetadataEntity = dao.getSyncMetadata(localId)
        return if (syncMetadataEntity != null) {
            syncMetadataEntity.modified_at = currentTime
            logcat(INFO) { "SyncMetadata: onEntityChanged modified_at ${syncMetadataEntity.syncId} and... ${syncMetadataEntity.modified_at}" }
            syncMetadataEntity
        } else {
            val newEntity = CredentialsSyncMetadataEntity(localId = localId, deleted_at = null, modified_at = currentTime)
            logcat(INFO) { "SyncMetadata: onEntityChanged modified_at ${newEntity.syncId} and... ${newEntity.modified_at}" }
            newEntity
        }
    }

    fun onEntityRemoved(localId: Long) {
        val syncId = dao.getSyncMetadata(localId)
        if (syncId != null) {
            syncId.deleted_at = SyncDateProvider.now()
            logcat(INFO) { "SyncMetadata: onEntityRemoved -> updateDeletedAt ${syncId.deleted_at}" }
            dao.insert(syncId)
        }
    }

    fun onEntitiesRemoved(localIds: List<Long>) {
        val deletionTimestamp = SyncDateProvider.now()
        val toBeDeleted: List<CredentialsSyncMetadataEntity> = localIds
            .mapNotNull { dao.getSyncMetadata(it) }
            .onEach { it.deleted_at = deletionTimestamp }
        logcat(INFO) { "SyncMetadata: onEntitiesRemoved -> ${toBeDeleted.size} entities had updateDeletedAt set $deletionTimestamp" }
        dao.insert(toBeDeleted)
    }

    fun removeDeletedEntities(before: Iso8601String) {
        dao.removeDeletedEntities(before)
    }

    fun removeEntityWith(localId: Long) {
        dao.removeEntityWithLocalId(localId)
    }

    fun removeEntityWith(syncId: String) {
        dao.removeEntityWithSyncId(syncId)
    }

    fun getChangesSince(since: Iso8601String): List<CredentialsSyncMetadataEntity> {
        return dao.getChangesSince(since)
    }

    fun getAllObservable(): Flow<List<CredentialsSyncMetadataEntity>> {
        return dao.getAllObservable()
    }
    fun getAllCredentials(): List<CredentialsSyncMetadataEntity> {
        return dao.getAll()
    }

    fun clearAll() {
        dao.removeAll()
    }
}
