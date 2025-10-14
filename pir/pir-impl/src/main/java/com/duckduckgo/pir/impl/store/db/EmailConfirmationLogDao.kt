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
interface EmailConfirmationLogDao {
    @Query("SELECT * FROM pir_email_confirmation_log ORDER BY eventTimeInMillis")
    fun getAllEmailConfirmationLogsFlow(): Flow<List<PirEmailConfirmationLog>>

    @Query("SELECT * FROM pir_email_confirmation_log ORDER BY eventTimeInMillis")
    fun getAllEmailConfirmationLogs(): List<PirEmailConfirmationLog>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertEmailConfirmationLog(pirEmailConfirmationLog: PirEmailConfirmationLog)

    @Query("DELETE from pir_email_confirmation_log")
    fun deleteAllEmailConfirmationLogs()
}
