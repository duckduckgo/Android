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

package com.duckduckgo.pir.internal.store.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface BrokerJsonDao {
    @Query("SELECT etag from pir_broker_json_etag where fileName = :fileName")
    fun getEtag(fileName: String): String

    @Query("SELECT * from pir_broker_json_etag where isActive = 1 ")
    fun getAllActiveBrokers(): List<BrokerJsonEtag>

    @Query("SELECT * from pir_broker_json_etag")
    fun getAllBrokers(): List<BrokerJsonEtag>

    @Query("SELECT COUNT(*) from pir_broker_json_etag")
    fun getAllBrokersCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertBrokerJsonEtags(etags: List<BrokerJsonEtag>)

    @Query("DELETE from pir_broker_json_etag")
    fun deleteAll()

    @Transaction
    fun upsertAll(etags: List<BrokerJsonEtag>) {
        deleteAll()
        insertBrokerJsonEtags(etags)
    }
}
