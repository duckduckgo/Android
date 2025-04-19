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

package com.duckduckgo.voice.store

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
abstract class VoiceSearchDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertAllManufacturers(manufacturers: List<ManufacturerEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertAllLocales(locales: List<LocaleEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertMinVersion(minVersion: MinVersionEntity)

    @Transaction
    open fun updateAll(manufacturers: List<ManufacturerEntity>, locales: List<LocaleEntity>, minVersion: MinVersionEntity) {
        deleteAllManufacturers()
        deleteAllLocales()
        deleteMinVersion()
        insertAllManufacturers(manufacturers)
        insertAllLocales(locales)
        insertMinVersion(minVersion)
    }

    @Query("select * from voice_search_manufacturers")
    abstract fun getManufacturerExceptions(): List<ManufacturerEntity>

    @Query("select * from voice_search_locales")
    abstract fun getLocaleExceptions(): List<LocaleEntity>

    @Query("select * from voice_search_min_version limit 1")
    abstract fun getMinVersion(): MinVersionEntity?

    @Query("delete from voice_search_min_version")
    abstract fun deleteMinVersion()

    @Query("delete from voice_search_manufacturers")
    abstract fun deleteAllManufacturers()

    @Query("delete from voice_search_locales")
    abstract fun deleteAllLocales()
}
