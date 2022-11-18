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

package com.duckduckgo.fingerprintprotection.store.features.fingerprintingscreensize

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.duckduckgo.fingerprintprotection.store.FingerprintingScreenSizeEntity

@Dao
abstract class FingerprintingScreenSizeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insert(fingerprintingScreenSizeEntity: FingerprintingScreenSizeEntity)

    @Transaction
    open fun updateAll(
        fingerprintingScreenSizeEntity: FingerprintingScreenSizeEntity,
    ) {
        delete()
        insert(fingerprintingScreenSizeEntity)
    }

    @Query("select * from fingerprinting_screen_size")
    abstract fun get(): FingerprintingScreenSizeEntity?

    @Query("delete from fingerprinting_screen_size")
    abstract fun delete()
}
