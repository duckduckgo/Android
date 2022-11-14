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

package com.duckduckgo.fingerprintprotection.store.seed

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.duckduckgo.fingerprintprotection.store.FingerprintProtectionSeedEntity

@Dao
abstract class FingerprintProtectionSeedDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insert(fingerprintProtectionSeed: FingerprintProtectionSeedEntity)

    @Transaction
    open fun updateAll(
        fingerprintProtectionSeed: FingerprintProtectionSeedEntity
    ) {
        delete()
        insert(fingerprintProtectionSeed)
    }

    @Query("select * from fingerprint_protection_seed")
    abstract fun get(): FingerprintProtectionSeedEntity?

    @Query("delete from fingerprint_protection_seed")
    abstract fun delete()
}
