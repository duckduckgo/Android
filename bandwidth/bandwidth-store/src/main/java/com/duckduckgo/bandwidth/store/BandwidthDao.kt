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

package com.duckduckgo.bandwidth.store

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface BandwidthDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(bandwidthEntity: BandwidthEntity)

    @Query("SELECT * FROM bandwidth")
    fun getBandwidth(): BandwidthEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertBucket(bandwidthBucketEntity: BandwidthBucketEntity)

    @Query("SELECT * FROM bandwidth_buckets")
    fun getBuckets(): List<BandwidthBucketEntity>?

    @Query("delete from bandwidth_buckets")
    fun deleteAllBuckets()
}
