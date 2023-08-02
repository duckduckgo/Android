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
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import timber.log.Timber

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
        val syncId = dao.getSyncMetadata(localId)
        if (syncId != null) {
            syncId.modified_at = currentTime
            Timber.i("SyncMetadata: onEntityChanged modified_at ${syncId.syncId} and... ${syncId.modified_at}")
            dao.insert(syncId)
        } else {
            val entity = CredentialsSyncMetadataEntity(localId = localId, deleted_at = null, modified_at = currentTime)
            dao.insert(entity)
            Timber.i("SyncMetadata: onEntityChanged modified_at ${entity.syncId} and... ${entity.modified_at}")
        }
    }

    fun onEntityRemoved(localId: Long) {
        val syncId = dao.getSyncMetadata(localId)
        if (syncId != null) {
            syncId.deleted_at = SyncDateProvider.now()
            Timber.i("SyncMetadata: onEntityRemoved -> updateDeletedAt ${syncId.deleted_at}")
            dao.insert(syncId)
        }
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
