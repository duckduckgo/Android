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

package com.duckduckgo.site.permissions.store.clienttools

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ClientToolPermissionsDao {

    @Query("select permission from client_tool_permissions where origin = :origin and toolName = :toolName")
    suspend fun getPermission(origin: String, toolName: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ClientToolPermissionEntity)

    @Query("delete from client_tool_permissions where toolName = :toolName")
    suspend fun deleteForTool(toolName: String)

    @Query("delete from client_tool_permissions")
    suspend fun deleteAll()
}
