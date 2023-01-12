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

package com.duckduckgo.request.filterer.store

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
abstract class RequestFiltererDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertSettings(settingsEntity: SettingsEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertAllRequestFiltererExceptions(exceptions: List<RequestFiltererExceptionEntity>)

    @Transaction
    open fun updateAll(exceptions: List<RequestFiltererExceptionEntity>, settingsEntity: SettingsEntity) {
        deleteSettings()
        deleteAllExceptions()
        insertAllRequestFiltererExceptions(exceptions)
        insertSettings(settingsEntity)
    }

    @Query("select * from settings_entity")
    abstract fun getSettings(): SettingsEntity?

    @Query("select * from request_filterer_exceptions")
    abstract fun getAllRequestFiltererExceptions(): List<RequestFiltererExceptionEntity>

    @Query("delete from settings_entity")
    abstract fun deleteSettings()

    @Query("delete from request_filterer_exceptions")
    abstract fun deleteAllExceptions()
}
