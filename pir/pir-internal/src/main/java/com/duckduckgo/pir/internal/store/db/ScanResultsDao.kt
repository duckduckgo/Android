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

package com.duckduckgo.pir.internal.store.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanResultsDao {
    @Query("SELECT * FROM pir_scan_complete_brokers ORDER BY endTimeInMillis")
    fun getScanCompletedBrokerFlow(): Flow<List<ScanCompletedBroker>>

    @Query("SELECT * FROM pir_scan_complete_brokers ORDER BY endTimeInMillis")
    suspend fun getAllScanCompletedBrokers(): List<ScanCompletedBroker>

    @Query("SELECT * FROM pir_scan_navigate_results ORDER BY completionTimeInMillis")
    fun getAllNavigateResultsFlow(): Flow<List<ScanNavigateResult>>

    @Query("SELECT * FROM pir_scan_navigate_results ORDER BY completionTimeInMillis")
    fun getAllNavigateResults(): List<ScanNavigateResult>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertNavigateResult(scanNavigateResult: ScanNavigateResult)

    @Query("SELECT * FROM pir_scan_error ORDER BY completionTimeInMillis")
    fun getAllScanErrorResultsFlow(): Flow<List<ScanErrorResult>>

    @Query("SELECT * FROM pir_scan_error ORDER BY completionTimeInMillis")
    fun getAllScanErrorResults(): List<ScanErrorResult>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertScanErrorResult(scanErrorResult: ScanErrorResult)

    @Query("SELECT * FROM pir_scan_extracted_profile ORDER BY completionTimeInMillis")
    fun getAllExtractProfileResultFlow(): Flow<List<ExtractProfileResult>>

    @Query("SELECT * FROM pir_scan_extracted_profile ORDER BY completionTimeInMillis")
    fun getAllExtractProfileResult(): List<ExtractProfileResult>

    @Query("SELECT * FROM pir_scan_extracted_profile WHERE brokerName = :brokerName ORDER BY completionTimeInMillis")
    fun getExtractProfileResultForBroker(brokerName: String): List<ExtractProfileResult>

    @Query("SELECT * FROM pir_extracted_profiles WHERE profileUrl = :profileUrl ORDER BY dateAddedInMillis")
    fun getExtractedProfile(profileUrl: String): StoredExtractedProfile

    @Query("SELECT * FROM pir_extracted_profiles WHERE profileQueryId = :profileQueryId ORDER BY dateAddedInMillis")
    fun getExtractedProfilesForProfile(profileQueryId: Long): List<StoredExtractedProfile>

    @Query("SELECT * FROM pir_extracted_profiles WHERE brokerName = :brokerName ORDER BY dateAddedInMillis")
    fun getExtractedProfilesForBroker(brokerName: String): List<StoredExtractedProfile>

    @Query("SELECT * FROM pir_extracted_profiles WHERE brokerName = :brokerName AND profileQueryId = :profileQueryId ORDER BY dateAddedInMillis")
    fun getExtractedProfilesForBrokerAndProfile(brokerName: String, profileQueryId: Long): List<StoredExtractedProfile>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertExtractedProfile(extractedProfile: StoredExtractedProfile)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertExtractedProfiles(extractedProfiles: List<StoredExtractedProfile>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertExtractProfileResult(extractProfileResult: ExtractProfileResult)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertScanCompletedBroker(scanCompletedBroker: ScanCompletedBroker)

    @Query("DELETE from pir_extracted_profiles")
    fun deleteAllExtractedProfiles()

    @Query("DELETE from pir_scan_navigate_results")
    fun deleteAllNavigateResults()

    @Query("DELETE from pir_scan_error")
    fun deleteAllScanErrorResults()

    @Query("DELETE from pir_scan_extracted_profile")
    fun deleteAllExtractProfileResult()

    @Query("DELETE from pir_scan_complete_brokers")
    fun deleteAllScanCompletedBroker()
}
