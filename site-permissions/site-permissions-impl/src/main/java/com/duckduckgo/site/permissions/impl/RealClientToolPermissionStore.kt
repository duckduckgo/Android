/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.site.permissions.impl

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.site.permissions.api.ClientToolPermission
import com.duckduckgo.site.permissions.api.ClientToolPermissionStore
import com.duckduckgo.site.permissions.store.clienttools.ClientToolPermissionEntity
import com.duckduckgo.site.permissions.store.clienttools.ClientToolPermissionsDao
import dev.zacsweers.metro.ContributesBinding
import kotlinx.coroutines.withContext
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class RealClientToolPermissionStore @Inject constructor(
    private val dao: ClientToolPermissionsDao,
    private val dispatcherProvider: DispatcherProvider,
) : ClientToolPermissionStore {

    override suspend fun get(
        origin: String,
        toolName: String,
    ): ClientToolPermission? = withContext(dispatcherProvider.io()) {
        val stored = dao.getPermission(origin, toolName) ?: return@withContext null
        ClientToolPermission.entries.firstOrNull { it.name == stored }
    }

    override suspend fun set(
        origin: String,
        toolName: String,
        permission: ClientToolPermission,
    ) = withContext(dispatcherProvider.io()) {
        dao.insert(ClientToolPermissionEntity(origin, toolName, permission.name))
    }

    override suspend fun clear(toolName: String) = withContext(dispatcherProvider.io()) {
        dao.deleteForTool(toolName)
    }

    override suspend fun clearAll() = withContext(dispatcherProvider.io()) {
        dao.deleteAll()
    }
}
