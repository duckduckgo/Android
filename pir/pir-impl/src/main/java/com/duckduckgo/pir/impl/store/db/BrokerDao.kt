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

package com.duckduckgo.pir.impl.store.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface BrokerDao {
    @Query("SELECT * from pir_broker_details where name = :brokerName")
    fun getBrokerDetails(brokerName: String): BrokerEntity?

    @Query("SELECT * FROM pir_broker_details WHERE removedAt = 0")
    fun getAllActiveBrokers(): List<BrokerEntity>

    @Query("SELECT stepsJson from pir_broker_scan where brokerName = :brokerName")
    fun getScanJson(brokerName: String): String?

    @Query("SELECT stepsJson from pir_broker_opt_out where brokerName = :brokerName")
    fun getOptOutJson(brokerName: String): String?

    @Query("SELECT * from pir_broker_scheduling_config where brokerName = :brokerName")
    fun getSchedulingConfig(brokerName: String): BrokerSchedulingConfigEntity?

    @Query("SELECT * from pir_broker_scheduling_config")
    fun getAllSchedulingConfigs(): List<BrokerSchedulingConfigEntity>

    @Query("DELETE from pir_broker_details")
    fun deleteAllBrokers()

    @Query("DELETE from pir_broker_details where name = :brokerName")
    fun deleteBroker(brokerName: String)

    @Query("SELECT * FROM pir_broker_opt_out")
    fun getAllBrokerOptOuts(): List<BrokerOptOut>

    @Query("SELECT * from pir_broker_mirror_sites")
    fun getAllMirrorSites(): List<MirrorSiteEntity>

    @Query("SELECT * from pir_broker_mirror_sites where removedAt = 0")
    fun getAllActiveMirrorSites(): List<MirrorSiteEntity>

    @Query("SELECT * from pir_broker_mirror_sites where removedAt = 0 AND parentSite = :brokerName")
    fun getAllActiveMirrorSitesForBroker(brokerName: String): List<MirrorSiteEntity>

    @Query("SELECT * from pir_broker_mirror_sites where parentSite = :brokerName")
    fun getAllMirrorSitesForBroker(brokerName: String): List<MirrorSiteEntity>

    @Query("DELETE from pir_broker_mirror_sites")
    fun deleteAllMirrorSites()

    @Query("DELETE from pir_broker_mirror_sites where parentSite = :brokerName")
    fun deleteMirrorSitesForBroker(brokerName: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertMirrorSites(mirrorSiteEntity: List<MirrorSiteEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertBroker(broker: BrokerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertScanSteps(brokerScan: BrokerScan)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOptOutSteps(brokerOptOut: BrokerOptOut)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertBrokerSchedulingConfig(schedulingConfig: BrokerSchedulingConfigEntity)

    @Transaction
    fun upsert(
        broker: BrokerEntity,
        brokerScan: BrokerScan,
        brokerOptOut: BrokerOptOut,
        schedulingConfig: BrokerSchedulingConfigEntity,
        mirrorSiteEntity: List<MirrorSiteEntity>,
    ) {
        deleteBroker(broker.name)
        deleteMirrorSitesForBroker(broker.name)
        insertBroker(broker)
        insertScanSteps(brokerScan)
        insertOptOutSteps(brokerOptOut)
        insertBrokerSchedulingConfig(schedulingConfig)
        insertMirrorSites(mirrorSiteEntity)
    }

    @Query("SELECT brokerName from pir_broker_scan")
    fun getAllBrokersNamesWithScanSteps(): List<String>
}
