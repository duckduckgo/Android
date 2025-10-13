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

package com.duckduckgo.pir.impl.store

import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.EmailConfirmationJobRecord
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.OptOutJobRecord
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.OptOutJobRecord.OptOutJobStatus
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.ScanJobRecord
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.ScanJobRecord.ScanJobStatus
import com.duckduckgo.pir.impl.store.db.EmailConfirmationJobRecordEntity
import com.duckduckgo.pir.impl.store.db.JobSchedulingDao
import com.duckduckgo.pir.impl.store.db.OptOutJobRecordEntity
import com.duckduckgo.pir.impl.store.db.ScanJobRecordEntity
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface PirSchedulingRepository {
    /**
     * Returns all ScanJobRecord whose state is not INVALID
     */
    suspend fun getAllValidScanJobRecords(): List<ScanJobRecord>

    /**
     * Returns a matching ScanJobRecord whose state is not INVALID
     */
    suspend fun getValidScanJobRecord(
        brokerName: String,
        userProfileId: Long,
    ): ScanJobRecord?

    /**
     * Returns all ScanJobRecord whose state is not INVALID
     */
    suspend fun getAllValidOptOutJobRecords(): List<OptOutJobRecord>

    /**
     * Returns a matching [OptOutJobRecord] whose state is not INVALID
     *
     * @param includeDeprecated If true, will also return deprecated jobs (used to run opt-out jobs on profiles that have been removed)
     */
    suspend fun getValidOptOutJobRecord(extractedProfileId: Long, includeDeprecated: Boolean = false): OptOutJobRecord?

    suspend fun updateScanJobRecordStatus(
        newStatus: ScanJobStatus,
        newLastScanDateMillis: Long,
        brokerName: String,
        profileQueryId: Long,
        deprecated: Boolean,
    )

    suspend fun saveScanJobRecord(scanJobRecord: ScanJobRecord)

    suspend fun saveScanJobRecords(scanJobRecords: List<ScanJobRecord>)

    suspend fun saveOptOutJobRecord(optOutJobRecord: OptOutJobRecord)

    suspend fun saveOptOutJobRecords(optOutJobRecords: List<OptOutJobRecord>)

    suspend fun deleteAllJobRecords()

    suspend fun deleteAllScanJobRecords()

    /**
     * Deletes all job records for the given [profileQueryId] except the ones that belong to the brokers in [brokersToExclude].
     *
     * This is used when a profile is deleted by the user, but we want to keep the job records for the brokers
     * that have an extracted profile associated to it to continue running jobs on them.
     */
    suspend fun deleteJobRecordsForProfile(
        profileQueryId: Long,
        brokersToExclude: List<String>,
    )

    suspend fun deleteAllOptOutJobRecords()

    suspend fun saveEmailConfirmationJobRecord(emailConfirmationJobRecord: EmailConfirmationJobRecord)

    suspend fun getEmailConfirmationJobsWithNoLink(): List<EmailConfirmationJobRecord>

    suspend fun getEmailConfirmationJobsWithLink(): List<EmailConfirmationJobRecord>

    suspend fun getEmailConfirmationJob(extractedProfileId: Long): EmailConfirmationJobRecord?

    suspend fun deleteEmailConfirmationJobRecord(extractedProfileId: Long)

    suspend fun deleteAllEmailConfirmationJobRecords()
}

@ContributesBinding(
    scope = AppScope::class,
    boundType = PirSchedulingRepository::class,
)
@SingleInstanceIn(AppScope::class)
class RealPirSchedulingRepository @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    private val jobSchedulingDao: JobSchedulingDao,
    private val currentTimeProvider: CurrentTimeProvider,
) : PirSchedulingRepository {
    override suspend fun getAllValidScanJobRecords(): List<ScanJobRecord> =
        withContext(dispatcherProvider.io()) {
            return@withContext jobSchedulingDao
                .getAllScanJobRecords()
                .map { it.toRecord() }
                // do not pick-up deprecated jobs as they belong to invalid/removed profiles
                .filter { it.status != ScanJobStatus.INVALID && !it.deprecated }
        }

    override suspend fun getValidScanJobRecord(
        brokerName: String,
        userProfileId: Long,
    ): ScanJobRecord? =
        withContext(dispatcherProvider.io()) {
            return@withContext jobSchedulingDao
                .getScanJobRecord(brokerName, userProfileId)
                ?.run { this.toRecord() }
                // do not pick-up deprecated jobs as they belong to invalid/removed profiles
                ?.takeIf { it.status != ScanJobStatus.INVALID && !it.deprecated }
        }

    override suspend fun getValidOptOutJobRecord(
        extractedProfileId: Long,
        includeDeprecated: Boolean,
    ): OptOutJobRecord? =
        withContext(dispatcherProvider.io()) {
            return@withContext jobSchedulingDao
                .getOptOutJobRecord(extractedProfileId)
                ?.run { this.toRecord() }
                // do not pick-up deprecated jobs as they belong to invalid/removed profiles
                ?.takeIf { it.status != OptOutJobStatus.INVALID && (includeDeprecated || !it.deprecated) }
        }

    override suspend fun getAllValidOptOutJobRecords(): List<OptOutJobRecord> =
        withContext(dispatcherProvider.io()) {
            return@withContext jobSchedulingDao
                .getAllOptOutJobRecords()
                .map { record -> record.toRecord() }
                // do not pick-up deprecated jobs as they belong to invalid/removed profiles
                .filter { it.status != OptOutJobStatus.INVALID && !it.deprecated }
        }

    override suspend fun saveScanJobRecord(scanJobRecord: ScanJobRecord) {
        withContext(dispatcherProvider.io()) {
            jobSchedulingDao.saveScanJobRecord(scanJobRecord.toEntity())
        }
    }

    override suspend fun saveScanJobRecords(scanJobRecords: List<ScanJobRecord>) {
        withContext(dispatcherProvider.io()) {
            scanJobRecords
                .map { it.toEntity() }
                .also {
                    jobSchedulingDao.saveScanJobRecords(it)
                }
        }
    }

    override suspend fun updateScanJobRecordStatus(
        newStatus: ScanJobStatus,
        newLastScanDateMillis: Long,
        brokerName: String,
        profileQueryId: Long,
        deprecated: Boolean,
    ) {
        withContext(dispatcherProvider.io()) {
            jobSchedulingDao.updateScanJobRecordStatus(
                brokerName = brokerName,
                profileQueryId = profileQueryId,
                newStatus = newStatus.name,
                newLastScanDateMillis = newLastScanDateMillis,
                deprecated = deprecated,
            )
        }
    }

    override suspend fun saveOptOutJobRecord(optOutJobRecord: OptOutJobRecord) {
        withContext(dispatcherProvider.io()) {
            optOutJobRecord
                .toEntity()
                .also {
                    jobSchedulingDao.saveOptOutJobRecord(it)
                }
        }
    }

    override suspend fun saveOptOutJobRecords(optOutJobRecords: List<OptOutJobRecord>) {
        withContext(dispatcherProvider.io()) {
            optOutJobRecords
                .map { it.toEntity() }
                .also {
                    jobSchedulingDao.saveOptOutJobRecords(it)
                }
        }
    }

    override suspend fun deleteAllJobRecords() {
        withContext(dispatcherProvider.io()) {
            jobSchedulingDao.deleteAllScanJobRecords()
            jobSchedulingDao.deleteAllOptOutJobRecords()
            jobSchedulingDao.deleteAllEmailConfirmationJobRecords()
        }
    }

    override suspend fun deleteAllScanJobRecords() {
        withContext(dispatcherProvider.io()) {
            jobSchedulingDao.deleteAllScanJobRecords()
        }
    }

    override suspend fun deleteJobRecordsForProfile(
        profileQueryId: Long,
        brokersToExclude: List<String>,
    ) {
        withContext(dispatcherProvider.io()) {
            jobSchedulingDao.deleteJobRecordsForProfile(profileQueryId, brokersToExclude)
        }
    }

    override suspend fun deleteAllOptOutJobRecords() {
        withContext(dispatcherProvider.io()) {
            jobSchedulingDao.deleteAllOptOutJobRecords()
        }
    }

    override suspend fun saveEmailConfirmationJobRecord(emailConfirmationJobRecord: EmailConfirmationJobRecord) {
        withContext(dispatcherProvider.io()) {
            jobSchedulingDao.saveEmailConfirmationJobRecord(emailConfirmationJobRecord.toEntity())
        }
    }

    override suspend fun getEmailConfirmationJobsWithNoLink(): List<EmailConfirmationJobRecord> =
        withContext(dispatcherProvider.io()) {
            return@withContext jobSchedulingDao.getAllActiveEmailConfirmationJobRecordsWithNoLink().map {
                it.toRecord()
            }
        }

    override suspend fun getEmailConfirmationJobsWithLink(): List<EmailConfirmationJobRecord> =
        withContext(dispatcherProvider.io()) {
            return@withContext jobSchedulingDao.getAllActiveEmailConfirmationJobRecordsWithLink().map {
                it.toRecord()
            }
        }

    override suspend fun getEmailConfirmationJob(extractedProfileId: Long): EmailConfirmationJobRecord? =
        withContext(dispatcherProvider.io()) {
            return@withContext jobSchedulingDao.getEmailConfirmationJobRecord(extractedProfileId)?.toRecord()
        }

    override suspend fun deleteEmailConfirmationJobRecord(extractedProfileId: Long) {
        withContext(dispatcherProvider.io()) {
            jobSchedulingDao.deleteEmailConfirmationJobRecord(extractedProfileId)
        }
    }

    override suspend fun deleteAllEmailConfirmationJobRecords() {
        withContext(dispatcherProvider.io()) {
            jobSchedulingDao.deleteAllEmailConfirmationJobRecords()
        }
    }

    private fun ScanJobRecordEntity.toRecord(): ScanJobRecord =
        ScanJobRecord(
            brokerName = this.brokerName,
            userProfileId = this.userProfileId,
            status = ScanJobStatus.entries.find { it.name == this.status } ?: ScanJobStatus.INVALID,
            lastScanDateInMillis = this.lastScanDateInMillis ?: 0L,
            deprecated = this.deprecated,
        )

    private fun ScanJobRecord.toEntity(): ScanJobRecordEntity =
        ScanJobRecordEntity(
            brokerName = this.brokerName,
            userProfileId = this.userProfileId,
            status = this.status.name,
            lastScanDateInMillis = this.lastScanDateInMillis,
            deprecated = this.deprecated,
        )

    private fun OptOutJobRecordEntity.toRecord(): OptOutJobRecord =
        OptOutJobRecord(
            extractedProfileId = this.extractedProfileId,
            brokerName = this.brokerName,
            userProfileId = this.userProfileId,
            status = OptOutJobStatus.entries.find { it.name == this.status } ?: OptOutJobStatus.INVALID,
            attemptCount = this.attemptCount,
            lastOptOutAttemptDateInMillis = this.lastOptOutAttemptDate ?: 0L,
            optOutRequestedDateInMillis = this.optOutRequestedDate,
            optOutRemovedDateInMillis = this.optOutRemovedDate,
            deprecated = this.deprecated,
        )

    private fun OptOutJobRecord.toEntity(): OptOutJobRecordEntity =
        OptOutJobRecordEntity(
            extractedProfileId = this.extractedProfileId,
            brokerName = this.brokerName,
            userProfileId = this.userProfileId,
            status = this.status.name,
            attemptCount = this.attemptCount,
            lastOptOutAttemptDate = this.lastOptOutAttemptDateInMillis,
            optOutRequestedDate = this.optOutRequestedDateInMillis,
            optOutRemovedDate = this.optOutRemovedDateInMillis,
            deprecated = this.deprecated,
        )

    private fun EmailConfirmationJobRecord.toEntity(): EmailConfirmationJobRecordEntity =
        EmailConfirmationJobRecordEntity(
            extractedProfileId = this.extractedProfileId,
            brokerName = this.brokerName,
            userProfileId = this.userProfileId,
            email = this.emailData.email,
            attemptId = this.emailData.attemptId,
            dateCreatedInMillis =
            if (this.dateCreatedInMillis != 0L) {
                this.dateCreatedInMillis
            } else {
                currentTimeProvider.currentTimeMillis()
            },
            emailConfirmationLink = this.linkFetchData.emailConfirmationLink,
            linkFetchAttemptCount = this.linkFetchData.linkFetchAttemptCount,
            lastLinkFetchDateInMillis = this.linkFetchData.lastLinkFetchDateInMillis,
            jobAttemptCount = this.jobAttemptData.jobAttemptCount,
            lastJobAttemptDateInMillis = this.jobAttemptData.lastJobAttemptDateInMillis,
            deprecated = this.deprecated,
        )

    private fun EmailConfirmationJobRecordEntity.toRecord(): EmailConfirmationJobRecord =
        EmailConfirmationJobRecord(
            extractedProfileId = this.extractedProfileId,
            brokerName = this.brokerName,
            userProfileId = this.userProfileId,
            emailData =
            EmailConfirmationJobRecord.EmailData(
                email = this.email,
                attemptId = this.attemptId,
            ),
            linkFetchData =
            EmailConfirmationJobRecord.LinkFetchData(
                emailConfirmationLink = this.emailConfirmationLink,
                linkFetchAttemptCount = this.linkFetchAttemptCount,
                lastLinkFetchDateInMillis = this.lastLinkFetchDateInMillis,
            ),
            jobAttemptData =
            EmailConfirmationJobRecord.JobAttemptData(
                jobAttemptCount = this.jobAttemptCount,
                lastJobAttemptDateInMillis = this.lastJobAttemptDateInMillis,
                lastJobAttemptActionId = this.lastJobAttemptActionId,
            ),
            dateCreatedInMillis = this.dateCreatedInMillis,
            deprecated = this.deprecated,
        )
}
