/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.app.trackerdetection.db

import androidx.room.*
import com.duckduckgo.app.trackerdetection.model.TemporaryTrackingWhitelistedDomain

/**
 * A list of sites that are temporarily whitelisted from tracker blocking due to site issue.
 * Note this is applied to the entire site not a specific tracker. e.g if example.com is added to the
 * list we would not block any trackers on example.com.
 */
@Dao
abstract class TemporaryTrackingWhitelistDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertAll(domains: List<TemporaryTrackingWhitelistedDomain>)

    @Transaction
    open fun updateAll(domains: List<TemporaryTrackingWhitelistedDomain>) {
        deleteAll()
        insertAll(domains)
    }

    @Query("select * from temporary_tracking_whitelist")
    abstract fun getAll(): List<TemporaryTrackingWhitelistedDomain>

    @Query("select * from temporary_tracking_whitelist where domain = :domain")
    abstract fun get(domain: String): TemporaryTrackingWhitelistedDomain

    @Query("select count(*) from temporary_tracking_whitelist")
    abstract fun count(): Int

    @Query("delete from temporary_tracking_whitelist")
    abstract fun deleteAll()

    @Query("select count(1) > 0 from temporary_tracking_whitelist where :domain LIKE '%'||domain||'%'")
    abstract fun contains(domain: String): Boolean
}
