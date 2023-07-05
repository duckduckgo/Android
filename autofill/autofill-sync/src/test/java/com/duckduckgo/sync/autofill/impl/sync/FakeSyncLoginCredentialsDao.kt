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

package com.duckduckgo.sync.autofill.impl.sync

import com.duckduckgo.autofill.sync.LoginCredentialsSync
import com.duckduckgo.autofill.sync.LoginCredentialsSyncDao

class FakeSyncLoginCredentialsDao : LoginCredentialsSyncDao {
    private val entities = mutableListOf<LoginCredentialsSync>()

    override fun getSyncId(id: Long): LoginCredentialsSync? {
        return entities.find { it.id == id }
    }

    override fun insert(entity: LoginCredentialsSync): Long {
        entities.add(entity)
        return 1
    }

    override fun getRemovedIdsSince(since: String): List<LoginCredentialsSync> {
        return entities.filter { it.deleted_at != null && it.deleted_at!! > since }
    }

    override fun delete(entity: LoginCredentialsSync) {
        entities.remove(entity)
    }

    override fun getLocalId(syncId: String): Long? {
        return entities.find { it.syncId == syncId }?.id
    }

    override fun removeDeletedEntities(before: String) {
        entities.removeIf { it.deleted_at != null && it.deleted_at!! < before }
    }
}
