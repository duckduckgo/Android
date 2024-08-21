/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.adclick.impl.store.exemptions

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
abstract class AdClickExemptionsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertTabExemption(tabExemptionEntity: AdClickTabExemptionEntity)

    @Query("select * from tab_exemptions where tabId = :tabId")
    abstract fun getTabExemption(tabId: String): AdClickTabExemptionEntity?

    @Query("select * from tab_exemptions")
    abstract fun getAllTabExemptions(): List<AdClickTabExemptionEntity>

    @Query("delete from tab_exemptions where tabId = :tabId")
    abstract fun deleteTabExemption(tabId: String)

    @Query("delete from tab_exemptions")
    abstract fun deleteAllTabExemptions()

    @Query("delete from tab_exemptions where exemptionDeadline < :exemptionDeadline")
    abstract fun deleteAllExpiredTabExemptions(exemptionDeadline: Long)
}
