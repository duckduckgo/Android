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
import kotlinx.coroutines.flow.Flow

@Dao
interface JobSchedulingDao {
    @Query("SELECT * FROM pir_scan_job_record ORDER BY lastScanDateInMillis")
    fun getAllScanJobRecords(): List<ScanJobRecordEntity>

    @Query("SELECT * FROM pir_scan_job_record ORDER BY lastScanDateInMillis")
    fun getAllScanJobRecordsFlow(): Flow<List<ScanJobRecordEntity>>

    @Query("SELECT * FROM pir_scan_job_record WHERE brokerName = :brokerName AND userProfileId = :userProfileId ORDER BY lastScanDateInMillis")
    fun getScanJobRecord(
        brokerName: String,
        userProfileId: Long,
    ): ScanJobRecordEntity?

    @Query("SELECT * FROM pir_optout_job_record ORDER BY attemptCount")
    fun getAllOptOutJobRecords(): List<OptOutJobRecordEntity>

    @Query("SELECT * FROM pir_optout_job_record ORDER BY attemptCount")
    fun getAllOptOutJobRecordsFlow(): Flow<List<OptOutJobRecordEntity>>

    @Query("SELECT * FROM pir_optout_job_record WHERE extractedProfileId = :extractedProfileId  ORDER BY attemptCount")
    fun getOptOutJobRecord(extractedProfileId: Long): OptOutJobRecordEntity?

    @Query(
        """
        UPDATE pir_scan_job_record
        SET status = :newStatus, lastScanDateInMillis = :newLastScanDateMillis, deprecated = :deprecated
        WHERE brokerName = :brokerName AND userProfileId = :profileQueryId
    """,
    )
    suspend fun updateScanJobRecordStatus(
        brokerName: String,
        profileQueryId: Long,
        newStatus: String,
        newLastScanDateMillis: Long,
        deprecated: Boolean,
    )

    @Query("SELECT * FROM pir_email_confirmation_job_record WHERE emailConfirmationLink = '' AND deprecated == 0 ORDER BY linkFetchAttemptCount")
    fun getAllActiveEmailConfirmationJobRecordsWithNoLink(): List<EmailConfirmationJobRecordEntity>

    @Query("SELECT * FROM pir_email_confirmation_job_record WHERE emailConfirmationLink != '' AND deprecated == 0 order BY jobAttemptCount")
    fun getAllActiveEmailConfirmationJobRecordsWithLink(): List<EmailConfirmationJobRecordEntity>

    @Query("SELECT * FROM pir_email_confirmation_job_record WHERE extractedProfileId = :extractedProfileId")
    fun getEmailConfirmationJobRecord(extractedProfileId: Long): EmailConfirmationJobRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun saveEmailConfirmationJobRecord(emailConfirmationJobRecordEntity: EmailConfirmationJobRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun saveOptOutJobRecord(optOutJobRecord: OptOutJobRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun saveOptOutJobRecords(optOutJobRecords: List<OptOutJobRecordEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun saveScanJobRecord(scanJobRecord: ScanJobRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun saveScanJobRecords(scanJobRecords: List<ScanJobRecordEntity>)

    @Query("DELETE FROM pir_email_confirmation_job_record WHERE extractedProfileId = :extractedProfileId")
    fun deleteEmailConfirmationJobRecord(extractedProfileId: Long)

    @Query("DELETE from pir_scan_job_record")
    fun deleteAllScanJobRecords()

    @Query("DELETE from pir_scan_job_record WHERE userProfileId = :profileQueryId AND brokerName NOT IN (:brokersToExclude)")
    fun deleteScanJobRecordsForProfile(profileQueryId: Long, brokersToExclude: List<String>)

    @Query("DELETE from pir_optout_job_record")
    fun deleteAllOptOutJobRecords()

    @Query("DELETE from pir_optout_job_record WHERE userProfileId = :profileQueryId AND brokerName NOT IN (:brokersToExclude)")
    fun deleteOptOutJobRecordsForProfile(profileQueryId: Long, brokersToExclude: List<String>)

    @Query("DELETE from pir_email_confirmation_job_record")
    fun deleteAllEmailConfirmationJobRecords()

    @Query("DELETE from pir_email_confirmation_job_record WHERE userProfileId = :profileQueryId AND brokerName NOT IN (:brokersToExclude)")
    fun deleteEmailConfirmationJobRecordsForProfile(profileQueryId: Long, brokersToExclude: List<String>)

    @Transaction
    fun deleteJobRecordsForProfile(profileQueryId: Long, brokersToExclude: List<String>) {
        deleteScanJobRecordsForProfile(profileQueryId, brokersToExclude)
        deleteOptOutJobRecordsForProfile(profileQueryId, brokersToExclude)
        deleteEmailConfirmationJobRecordsForProfile(profileQueryId, brokersToExclude)
    }
}
