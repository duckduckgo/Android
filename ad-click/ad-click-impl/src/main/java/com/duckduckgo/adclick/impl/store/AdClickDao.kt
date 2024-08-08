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

package com.duckduckgo.adclick.impl.store

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
abstract class AdClickDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertLinkFormats(linkFormats: List<AdClickAttributionLinkFormatEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertAllowList(allowList: List<AdClickAttributionAllowlistEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertExpirations(expirations: List<AdClickAttributionExpirationEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertDetections(detections: List<AdClickAttributionDetectionEntity>)

    @Transaction
    open fun setAll(
        linkFormats: List<AdClickAttributionLinkFormatEntity>,
        allowList: List<AdClickAttributionAllowlistEntity>,
        expirations: List<AdClickAttributionExpirationEntity>,
        detections: List<AdClickAttributionDetectionEntity>,
    ) {
        deleteLinkFormats()
        insertLinkFormats(linkFormats)

        deleteAllowList()
        insertAllowList(allowList)

        deleteExpirations()
        insertExpirations(expirations)

        deleteDetections()
        insertDetections(detections)
    }

    @Query("select * from link_formats")
    abstract fun getLinkFormats(): List<AdClickAttributionLinkFormatEntity>

    @Query("select * from allowlist")
    abstract fun getAllowList(): List<AdClickAttributionAllowlistEntity>

    @Query("select * from expirations")
    abstract fun getExpirations(): List<AdClickAttributionExpirationEntity>

    @Query("select * from detections")
    abstract fun getDetections(): List<AdClickAttributionDetectionEntity>

    @Query("delete from link_formats")
    abstract fun deleteLinkFormats()

    @Query("delete from allowlist")
    abstract fun deleteAllowList()

    @Query("delete from expirations")
    abstract fun deleteExpirations()

    @Query("delete from detections")
    abstract fun deleteDetections()
}
