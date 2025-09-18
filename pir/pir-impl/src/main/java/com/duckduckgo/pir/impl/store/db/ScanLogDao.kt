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

package com.duckduckgo.pir.impl.store.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanLogDao {
    @Query("SELECT * FROM pir_events_log ORDER BY eventTimeInMillis")
    fun getAllEventLogsFlow(): Flow<List<PirEventLog>>

    @Query("SELECT * FROM pir_broker_scan_log ORDER BY eventTimeInMillis")
    fun getAllBrokerScanEventsFlow(): Flow<List<PirBrokerScanLog>>

    @Query("SELECT * FROM pir_broker_scan_log ORDER BY eventTimeInMillis")
    fun getAllBrokerScanEvents(): List<PirBrokerScanLog>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertEventLog(pirScanLog: PirEventLog)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertBrokerScanEvent(pirBrokerScanLog: PirBrokerScanLog)

    @Query("DELETE from pir_events_log")
    fun deleteAllEventLogs()

    @Query("DELETE from pir_broker_scan_log")
    fun deleteAllBrokerScanEvents()
}
