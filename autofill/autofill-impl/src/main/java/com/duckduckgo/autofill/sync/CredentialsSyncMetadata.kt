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

import com.duckduckgo.app.global.formatters.time.DatabaseDateFormatter
import com.duckduckgo.autofill.store.CredentialsSyncMetadataDao
import com.duckduckgo.autofill.store.CredentialsSyncMetadataEntity
import com.duckduckgo.di.scopes.AppScope
import dagger.SingleInstanceIn
import javax.inject.Inject

@SingleInstanceIn(AppScope::class)
class CredentialsSyncMetadata @Inject constructor(
    private val dao: CredentialsSyncMetadataDao,
) {
    fun addOrUpdate(entity: CredentialsSyncMetadataEntity) {
        dao.insert(entity)
    }

    fun getSyncId(id: Long): String? {
        return dao.getSyncId(id)?.syncId
    }

    fun createSyncId(id: Long): String {
        var syncId = getSyncId(id)
        if (syncId == null) {
            val entity = CredentialsSyncMetadataEntity(id = id)
            dao.insert(entity)
            syncId = entity.syncId
        }
        return syncId
    }

    fun getLocalId(syncId: String): Long? {
        return dao.getLocalId(syncId)
    }

    fun getRemovedEntitiesSince(since: String): List<CredentialsSyncMetadataEntity> {
        return dao.getRemovedIdsSince(since)
    }

    fun onEntityRemoved(id: Long) {
        val syncId = dao.getSyncId(id)
        if (syncId != null) {
            syncId.deleted_at = DatabaseDateFormatter.iso8601()
            dao.insert(syncId)
        }
    }

    fun removeDeletedEntities(before: String) {
        dao.removeDeletedEntities(before)
    }

    fun removeEntityWithLocalId(id: Long) {
        dao.removeEntityWithLocalId(id)
    }

    fun removeEntityWithSyncId(syncId: String) {
        dao.removeEntityWithSyncId(syncId)
    }
}
