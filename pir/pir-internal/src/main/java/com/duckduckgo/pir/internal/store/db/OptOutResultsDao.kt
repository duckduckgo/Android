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
import kotlinx.coroutines.flow.Flow

@Dao
interface OptOutResultsDao {
    @Query("SELECT * FROM pir_opt_out_action_log ORDER BY completionTimeInMillis")
    fun getOptOutActionLogFlow(): Flow<List<OptOutActionLog>>

    @Query("SELECT * FROM pir_opt_out_complete_brokers ORDER BY endTimeInMillis")
    fun getOptOutCompletedBrokerFlow(): Flow<List<OptOutCompletedBroker>>

    @Query("SELECT * FROM pir_opt_out_complete_brokers ORDER BY endTimeInMillis")
    suspend fun getAllOptOutCompletedBrokers(): List<OptOutCompletedBroker>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOptOutActionLog(optOutActionLog: OptOutActionLog)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOptOutCompletedBroker(optOutCompletedBroker: OptOutCompletedBroker)

    @Query("DELETE from pir_opt_out_action_log")
    fun deleteAllOptOutActionLog()

    @Query("DELETE from pir_opt_out_complete_brokers")
    fun deleteAllOptOutCompletedBroker()
}
