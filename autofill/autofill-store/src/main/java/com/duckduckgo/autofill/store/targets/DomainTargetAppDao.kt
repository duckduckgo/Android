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

package com.duckduckgo.autofill.store.targets

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
abstract class DomainTargetAppDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertAllMapping(mapping: List<DomainTargetAppEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insert(mapping: DomainTargetAppEntity)

    @Transaction
    open fun updateAll(mapping: List<DomainTargetAppEntity>) {
        deleteAllMapping()
        insertAllMapping(mapping)
    }

    @Transaction
    open fun updateRemote(mapping: List<DomainTargetAppEntity>) {
        deleteAllRemote()
        insertAllMapping(mapping)
    }

    @Query("delete from autofill_domain_target_apps_mapping")
    abstract fun deleteAllMapping()

    @Query("delete from autofill_domain_target_apps_mapping WHERE dataExpiryInMillis = 0")
    abstract fun deleteAllRemote()

    @Query("delete from autofill_domain_target_apps_mapping WHERE dataExpiryInMillis > 0 AND dataExpiryInMillis < :currentTimeMillis")
    abstract fun deleteAllExpired(currentTimeMillis: Long)

    @Query("select app_package, app_fingerprint from autofill_domain_target_apps_mapping where domain = :domain")
    abstract fun getTargetAppsForDomain(domain: String): List<TargetApp>

    @Query("select domain from autofill_domain_target_apps_mapping where app_package = :packageName AND app_fingerprint = :fingerprint")
    abstract fun getDomainsForApp(
        packageName: String,
        fingerprint: String,
    ): List<String>
}
