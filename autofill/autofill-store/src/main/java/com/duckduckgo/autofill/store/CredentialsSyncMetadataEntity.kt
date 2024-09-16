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

package com.duckduckgo.autofill.store

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.*

@Entity(
    tableName = "credentials_sync_meta",
    indices = [Index(value = ["syncId"], unique = true)],
)
data class CredentialsSyncMetadataEntity(
    val syncId: String = UUID.randomUUID().toString(),
    @PrimaryKey val localId: Long,
    var deleted_at: String?, // should follow iso8601 format
    var modified_at: String?, // should follow iso8601 format
)
