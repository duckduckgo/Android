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
     */
    suspend fun getValidOptOutJobRecord(extractedProfileId: Long): OptOutJobRecord?

    suspend fun updateScanJobRecordStatus(
        newStatus: ScanJobStatus,
        newLastScanDateMillis: Long,
        brokerName: String,
        profileQueryId: Long,
    )

    suspend fun saveScanJobRecord(scanJobRecord: ScanJobRecord)

    suspend fun saveScanJobRecords(scanJobRecords: List<ScanJobRecord>)

    suspend fun saveOptOutJobRecord(optOutJobRecord: OptOutJobRecord)

    suspend fun saveOptOutJobRecords(optOutJobRecords: List<OptOutJobRecord>)

    suspend fun deleteAllJobRecords()

    suspend fun deleteAllScanJobRecords()

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
                .map { record ->
                    ScanJobRecord(
                        brokerName = record.brokerName,
                        userProfileId = record.userProfileId,
                        status = ScanJobStatus.entries.find { it.name == record.status } ?: ScanJobStatus.INVALID,
                        lastScanDateInMillis = record.lastScanDateInMillis ?: 0L,
                    )
                }.filter {
                    it.status != ScanJobStatus.INVALID
                }
        }

    override suspend fun getValidScanJobRecord(
        brokerName: String,
        userProfileId: Long,
    ): ScanJobRecord? =
        withContext(dispatcherProvider.io()) {
            return@withContext jobSchedulingDao
                .getScanJobRecord(brokerName, userProfileId)
                ?.run {
                    ScanJobRecord(
                        brokerName = this.brokerName,
                        userProfileId = this.userProfileId,
                        status = ScanJobStatus.entries.find { it.name == this.status } ?: ScanJobStatus.INVALID,
                        lastScanDateInMillis = this.lastScanDateInMillis ?: 0L,
                    )
                }?.takeIf {
                    it.status != ScanJobStatus.INVALID
                }
        }

    override suspend fun getValidOptOutJobRecord(extractedProfileId: Long): OptOutJobRecord? =
        withContext(dispatcherProvider.io()) {
            return@withContext jobSchedulingDao
                .getOptOutJobRecord(extractedProfileId)
                ?.run {
                    OptOutJobRecord(
                        extractedProfileId = this.extractedProfileId,
                        brokerName = this.brokerName,
                        userProfileId = this.userProfileId,
                        status = OptOutJobStatus.entries.find { it.name == this.status } ?: OptOutJobStatus.INVALID,
                        attemptCount = this.attemptCount,
                        lastOptOutAttemptDateInMillis = this.lastOptOutAttemptDate ?: 0L,
                        optOutRequestedDateInMillis = this.optOutRequestedDate,
                        optOutRemovedDateInMillis = this.optOutRemovedDate,
                    )
                }?.takeIf {
                    it.status != OptOutJobStatus.INVALID
                }
        }

    override suspend fun getAllValidOptOutJobRecords(): List<OptOutJobRecord> =
        withContext(dispatcherProvider.io()) {
            return@withContext jobSchedulingDao
                .getAllOptOutJobRecords()
                .map { record ->
                    OptOutJobRecord(
                        extractedProfileId = record.extractedProfileId,
                        brokerName = record.brokerName,
                        userProfileId = record.userProfileId,
                        status = OptOutJobStatus.entries.find { it.name == record.status } ?: OptOutJobStatus.INVALID,
                        attemptCount = record.attemptCount,
                        lastOptOutAttemptDateInMillis = record.lastOptOutAttemptDate ?: 0L,
                        optOutRequestedDateInMillis = record.optOutRequestedDate,
                        optOutRemovedDateInMillis = record.optOutRemovedDate,
                    )
                }.filter {
                    it.status != OptOutJobStatus.INVALID
                }
        }

    override suspend fun saveScanJobRecord(scanJobRecord: ScanJobRecord) {
        withContext(dispatcherProvider.io()) {
            ScanJobRecordEntity(
                brokerName = scanJobRecord.brokerName,
                userProfileId = scanJobRecord.userProfileId,
                status = scanJobRecord.status.name,
                lastScanDateInMillis = scanJobRecord.lastScanDateInMillis,
            ).also {
                jobSchedulingDao.saveScanJobRecord(it)
            }
        }
    }

    override suspend fun saveScanJobRecords(scanJobRecords: List<ScanJobRecord>) {
        withContext(dispatcherProvider.io()) {
            scanJobRecords
                .map {
                    ScanJobRecordEntity(
                        brokerName = it.brokerName,
                        userProfileId = it.userProfileId,
                        status = it.status.name,
                        lastScanDateInMillis = it.lastScanDateInMillis,
                    )
                }.also {
                    jobSchedulingDao.saveScanJobRecords(it)
                }
        }
    }

    override suspend fun updateScanJobRecordStatus(
        newStatus: ScanJobStatus,
        newLastScanDateMillis: Long,
        brokerName: String,
        profileQueryId: Long,
    ) {
        withContext(dispatcherProvider.io()) {
            jobSchedulingDao.updateScanJobRecordStatus(
                brokerName = brokerName,
                profileQueryId = profileQueryId,
                newStatus = newStatus.name,
                newLastScanDateMillis = newLastScanDateMillis,
            )
        }
    }

    override suspend fun saveOptOutJobRecord(optOutJobRecord: OptOutJobRecord) {
        withContext(dispatcherProvider.io()) {
            OptOutJobRecordEntity(
                extractedProfileId = optOutJobRecord.extractedProfileId,
                brokerName = optOutJobRecord.brokerName,
                userProfileId = optOutJobRecord.userProfileId,
                status = optOutJobRecord.status.name,
                attemptCount = optOutJobRecord.attemptCount,
                lastOptOutAttemptDate = optOutJobRecord.lastOptOutAttemptDateInMillis,
                optOutRequestedDate = optOutJobRecord.optOutRequestedDateInMillis,
                optOutRemovedDate = optOutJobRecord.optOutRemovedDateInMillis,
            ).also {
                jobSchedulingDao.saveOptOutJobRecord(it)
            }
        }
    }

    override suspend fun saveOptOutJobRecords(optOutJobRecords: List<OptOutJobRecord>) {
        withContext(dispatcherProvider.io()) {
            optOutJobRecords
                .map {
                    OptOutJobRecordEntity(
                        extractedProfileId = it.extractedProfileId,
                        brokerName = it.brokerName,
                        userProfileId = it.userProfileId,
                        status = it.status.name,
                        attemptCount = it.attemptCount,
                        lastOptOutAttemptDate = it.lastOptOutAttemptDateInMillis,
                        optOutRequestedDate = it.optOutRequestedDateInMillis,
                        optOutRemovedDate = it.optOutRemovedDateInMillis,
                    )
                }.also {
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
