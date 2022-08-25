/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.autoconsent.store

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
abstract class AutoconsentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertDisabledCmps(disabledCmps: List<DisabledCmpsEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertExceptions(exceptions: List<AutoconsentExceptionEntity>)

    @Transaction
    open fun updateAll(exceptions: List<AutoconsentExceptionEntity>, disabledCmps: List<DisabledCmpsEntity>) {
        deleteDisabledCmps()
        deleteExceptions()
        insertDisabledCmps(disabledCmps)
        insertExceptions(exceptions)
    }

    @Query("select * from autoconsent_exceptions")
    abstract fun getExceptions(): List<AutoconsentExceptionEntity>

    @Query("select * from autoconsent_disabled_cmps")
    abstract fun getDisabledCmps(): List<DisabledCmpsEntity>

    @Query("delete from autoconsent_disabled_cmps")
    abstract fun deleteDisabledCmps()

    @Query("delete from autoconsent_exceptions")
    abstract fun deleteExceptions()
}
