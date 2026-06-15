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

package com.duckduckgo.adblocking.impl.store

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class AdBlockingExtensionDao {

    @Query("SELECT version FROM ad_blocking_scriptlets_metadata WHERE id = ${ScriptletMetadataEntity.SINGLETON_ID}")
    abstract suspend fun getVersion(): String?

    @Query("SELECT * FROM ad_blocking_scriptlets")
    abstract fun scriptletsFlow(): Flow<List<ScriptletEntity>>

    @Query("DELETE FROM ad_blocking_scriptlets")
    abstract suspend fun deleteAllScriptlets()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertScriptlets(scriptlets: List<ScriptletEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertMetadata(metadata: ScriptletMetadataEntity)

    @Transaction
    open suspend fun replaceAll(version: String, scriptlets: List<ScriptletEntity>) {
        deleteAllScriptlets()
        insertScriptlets(scriptlets)
        upsertMetadata(ScriptletMetadataEntity(version = version))
    }
}
