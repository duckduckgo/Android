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

package com.duckduckgo.pir.internal.store

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pir.internal.models.scheduling.OptOutJobRecord
import com.duckduckgo.pir.internal.models.scheduling.OptOutJobStatus
import com.duckduckgo.pir.internal.models.scheduling.ScanJobRecord
import com.duckduckgo.pir.internal.models.scheduling.ScanJobStatus
import com.duckduckgo.pir.internal.store.db.JobSchedulingDao
import com.duckduckgo.pir.internal.store.db.OptOutJobRecordEntity
import com.duckduckgo.pir.internal.store.db.ScanJobRecordEntity
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.withContext

interface PirSchedulingRepository {
    /**
     * Returns all ScanJobRecord whose state is not INVALID
     */
    suspend fun getAllValidScanJobRecords(): List<ScanJobRecord>

    /**
     * Returns all ScanJobRecord whose state is not INVALID
     */
    suspend fun getAllValidOptOutJobRecords(): List<OptOutJobRecord>
    suspend fun saveScanJobRecord(scanJobRecord: ScanJobRecord)
    suspend fun saveScanJobRecords(scanJobRecords: List<ScanJobRecord>)
    suspend fun saveOptOutJobRecord(optOutJobRecord: OptOutJobRecord)
    suspend fun saveOptOutJobRecords(optOutJobRecords: List<OptOutJobRecord>)
    suspend fun deleteAllJobRecords()
    suspend fun deleteAllScanJobRecords()
    suspend fun deleteAllOptOutJobRecords()
}

@ContributesBinding(
    scope = AppScope::class,
    boundType = PirSchedulingRepository::class,
)
@SingleInstanceIn(AppScope::class)
class RealPirSchedulingRepository @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    private val jobSchedulingDao: JobSchedulingDao,
) : PirSchedulingRepository {

    override suspend fun getAllValidScanJobRecords(): List<ScanJobRecord> = withContext(dispatcherProvider.io()) {
        return@withContext jobSchedulingDao.getAllScanJobRecords().map {
            ScanJobRecord(
                brokerName = it.brokerName,
                userProfileId = it.userProfileId,
                status = ScanJobStatus.valueOf(it.status),
                lastScanDateInMillis = it.lastScanDateInMillis,
            )
        }.filter {
            it.status != ScanJobStatus.INVALID
        }
    }

    override suspend fun getAllValidOptOutJobRecords(): List<OptOutJobRecord> = withContext(dispatcherProvider.io()) {
        return@withContext jobSchedulingDao.getAllOptOutJobRecords().map {
            OptOutJobRecord(
                extractedProfileId = it.extractedProfileId,
                brokerName = it.brokerName,
                userProfileId = it.userProfileId,
                status = OptOutJobStatus.valueOf(it.status),
                attemptCount = it.attemptCount,
                lastOptOutAttemptDate = it.lastOptOutAttemptDate,
                optOutRequestedDate = it.optOutRequestedDate,
                optOutRemovedDate = it.optOutRemovedDate,
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
            scanJobRecords.map {
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

    override suspend fun saveOptOutJobRecord(optOutJobRecord: OptOutJobRecord) {
        withContext(dispatcherProvider.io()) {
            OptOutJobRecordEntity(
                extractedProfileId = optOutJobRecord.extractedProfileId,
                brokerName = optOutJobRecord.brokerName,
                userProfileId = optOutJobRecord.userProfileId,
                status = optOutJobRecord.status.name,
                attemptCount = optOutJobRecord.attemptCount,
                lastOptOutAttemptDate = optOutJobRecord.lastOptOutAttemptDate,
                optOutRequestedDate = optOutJobRecord.optOutRequestedDate,
                optOutRemovedDate = optOutJobRecord.optOutRemovedDate,
            ).also {
                jobSchedulingDao.saveOptOutJobRecord(it)
            }
        }
    }

    override suspend fun saveOptOutJobRecords(optOutJobRecords: List<OptOutJobRecord>) {
        withContext(dispatcherProvider.io()) {
            optOutJobRecords.map {
                OptOutJobRecordEntity(
                    extractedProfileId = it.extractedProfileId,
                    brokerName = it.brokerName,
                    userProfileId = it.userProfileId,
                    status = it.status.name,
                    attemptCount = it.attemptCount,
                    lastOptOutAttemptDate = it.lastOptOutAttemptDate,
                    optOutRequestedDate = it.optOutRequestedDate,
                    optOutRemovedDate = it.optOutRemovedDate,
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
}
