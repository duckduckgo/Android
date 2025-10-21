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

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pir.impl.models.ExtractedProfile
import com.duckduckgo.pir.impl.store.db.BrokerScanEventType.BROKER_ERROR
import com.duckduckgo.pir.impl.store.db.BrokerScanEventType.BROKER_SUCCESS
import com.duckduckgo.pir.impl.store.db.EmailConfirmationEventType
import com.duckduckgo.pir.impl.store.db.EmailConfirmationLogDao
import com.duckduckgo.pir.impl.store.db.OptOutActionLog
import com.duckduckgo.pir.impl.store.db.OptOutCompletedBroker
import com.duckduckgo.pir.impl.store.db.OptOutResultsDao
import com.duckduckgo.pir.impl.store.db.PirBrokerScanLog
import com.duckduckgo.pir.impl.store.db.PirEmailConfirmationLog
import com.duckduckgo.pir.impl.store.db.PirEventLog
import com.duckduckgo.pir.impl.store.db.ScanCompletedBroker
import com.duckduckgo.pir.impl.store.db.ScanLogDao
import com.duckduckgo.pir.impl.store.db.ScanResultsDao
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.Moshi
import dagger.SingleInstanceIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface PirEventsRepository {
    fun getAllEventLogsFlow(): Flow<List<PirEventLog>>

    suspend fun saveEventLog(pirEventLog: PirEventLog)

    suspend fun saveBrokerScanLog(pirBrokerScanLog: PirBrokerScanLog)

    suspend fun deleteEventLogs()

    fun getScannedBrokersFlow(): Flow<List<ScanCompletedBroker>>

    suspend fun getScanErrorResultsCount(): Int

    suspend fun getScanSuccessResultsCount(): Int

    suspend fun deleteAllScanResults()

    fun getTotalScannedBrokersFlow(): Flow<Int>

    fun getTotalOptOutCompletedFlow(): Flow<Int>

    fun getAllOptOutActionLogFlow(): Flow<List<OptOutActionLog>>

    fun getAllSuccessfullySubmittedOptOutFlow(): Flow<Map<String, String>>

    suspend fun saveScanCompletedBroker(
        brokerName: String,
        profileQueryId: Long,
        startTimeInMillis: Long,
        endTimeInMillis: Long,
        isSuccess: Boolean,
    )

    suspend fun saveOptOutCompleted(
        brokerName: String,
        extractedProfile: ExtractedProfile,
        startTimeInMillis: Long,
        endTimeInMillis: Long,
        isSubmitSuccess: Boolean,
    )

    suspend fun saveOptOutActionLog(
        brokerName: String,
        extractedProfile: ExtractedProfile,
        completionTimeInMillis: Long,
        actionType: String,
        isError: Boolean,
        result: String,
    )

    suspend fun deleteAllOptOutData()

    suspend fun saveEmailConfirmationLog(
        eventTimeInMillis: Long,
        type: EmailConfirmationEventType,
        detail: String,
    )

    fun getAllEmailConfirmationLogFlow(): Flow<List<PirEmailConfirmationLog>>

    suspend fun deleteAllEmailConfirmationsLogs()
}

@ContributesBinding(
    scope = AppScope::class,
    boundType = PirEventsRepository::class,
)
@SingleInstanceIn(AppScope::class)
class RealPirEventsRepository @Inject constructor(
    val moshi: Moshi,
    private val dispatcherProvider: DispatcherProvider,
    private val scanResultsDao: ScanResultsDao,
    private val scanLogDao: ScanLogDao,
    private val optOutResultsDao: OptOutResultsDao,
    private val emailConfirmationLogDao: EmailConfirmationLogDao,
) : PirEventsRepository {
    private val extractedProfileAdapter by lazy { moshi.adapter(ExtractedProfile::class.java) }

    override suspend fun deleteAllScanResults() {
        withContext(dispatcherProvider.io()) {
            scanResultsDao.deleteAllScanCompletedBroker()
            scanLogDao.deleteAllBrokerScanEvents()
        }
    }

    override suspend fun getScanErrorResultsCount(): Int = withContext(dispatcherProvider.io()) {
        scanLogDao.getAllBrokerScanEvents().filter { it.eventType == BROKER_ERROR }.size
    }

    override suspend fun getScanSuccessResultsCount(): Int = withContext(dispatcherProvider.io()) {
        scanLogDao.getAllBrokerScanEvents().filter { it.eventType == BROKER_SUCCESS }.size
    }

    override fun getAllEventLogsFlow(): Flow<List<PirEventLog>> {
        return scanLogDao.getAllEventLogsFlow()
    }

    override suspend fun saveEventLog(pirEventLog: PirEventLog) {
        withContext(dispatcherProvider.io()) {
            scanLogDao.insertEventLog(pirEventLog)
        }
    }

    override suspend fun saveBrokerScanLog(pirBrokerScanLog: PirBrokerScanLog) {
        withContext(dispatcherProvider.io()) {
            scanLogDao.insertBrokerScanEvent(pirBrokerScanLog)
        }
    }

    override suspend fun deleteEventLogs() {
        withContext(dispatcherProvider.io()) {
            scanLogDao.deleteAllEventLogs()
        }
    }

    override fun getScannedBrokersFlow(): Flow<List<ScanCompletedBroker>> {
        return scanResultsDao.getScanCompletedBrokerFlow()
    }

    override fun getTotalScannedBrokersFlow(): Flow<Int> {
        return scanResultsDao.getScanCompletedBrokerFlow().map { it.size }
    }

    override fun getTotalOptOutCompletedFlow(): Flow<Int> {
        return optOutResultsDao.getOptOutCompletedBrokerFlow().map { it.size }
    }

    override fun getAllSuccessfullySubmittedOptOutFlow(): Flow<Map<String, String>> {
        return optOutResultsDao.getOptOutCompletedBrokerFlow().map {
            it.filter {
                it.isSubmitSuccess
            }.map {
                (
                    extractedProfileAdapter.fromJson(it.extractedProfile)?.identifier
                        ?: "Unknown"
                    ) to it.brokerName
            }.distinct().toMap()
        }
    }

    override fun getAllOptOutActionLogFlow(): Flow<List<OptOutActionLog>> {
        return optOutResultsDao.getOptOutActionLogFlow()
    }

    override suspend fun saveScanCompletedBroker(
        brokerName: String,
        profileQueryId: Long,
        startTimeInMillis: Long,
        endTimeInMillis: Long,
        isSuccess: Boolean,
    ) = withContext(dispatcherProvider.io()) {
        scanResultsDao.insertScanCompletedBroker(
            ScanCompletedBroker(
                brokerName = brokerName,
                profileQueryId = profileQueryId,
                startTimeInMillis = startTimeInMillis,
                endTimeInMillis = endTimeInMillis,
                isSuccess = isSuccess,
            ),
        )
    }

    override suspend fun saveOptOutCompleted(
        brokerName: String,
        extractedProfile: ExtractedProfile,
        startTimeInMillis: Long,
        endTimeInMillis: Long,
        isSubmitSuccess: Boolean,
    ) = withContext(dispatcherProvider.io()) {
        optOutResultsDao.insertOptOutCompletedBroker(
            OptOutCompletedBroker(
                brokerName = brokerName,
                extractedProfile = extractedProfileAdapter.toJson(extractedProfile),
                startTimeInMillis = startTimeInMillis,
                endTimeInMillis = endTimeInMillis,
                isSubmitSuccess = isSubmitSuccess,
            ),
        )
    }

    override suspend fun saveOptOutActionLog(
        brokerName: String,
        extractedProfile: ExtractedProfile,
        completionTimeInMillis: Long,
        actionType: String,
        isError: Boolean,
        result: String,
    ) = withContext(dispatcherProvider.io()) {
        optOutResultsDao.insertOptOutActionLog(
            OptOutActionLog(
                brokerName = brokerName,
                extractedProfile = extractedProfileAdapter.toJson(extractedProfile),
                completionTimeInMillis = completionTimeInMillis,
                actionType = actionType,
                isError = isError,
                result = result,
            ),
        )
    }

    override suspend fun deleteAllOptOutData() = withContext(dispatcherProvider.io()) {
        optOutResultsDao.deleteAllOptOutActionLog()
        optOutResultsDao.deleteAllOptOutCompletedBroker()
    }

    override suspend fun saveEmailConfirmationLog(
        eventTimeInMillis: Long,
        type: EmailConfirmationEventType,
        detail: String,
    ) = withContext(dispatcherProvider.io()) {
        emailConfirmationLogDao.insertEmailConfirmationLog(
            PirEmailConfirmationLog(
                eventTimeInMillis = eventTimeInMillis,
                eventType = type,
                value = detail,
            ),
        )
    }

    override fun getAllEmailConfirmationLogFlow(): Flow<List<PirEmailConfirmationLog>> {
        return emailConfirmationLogDao.getAllEmailConfirmationLogsFlow()
    }

    override suspend fun deleteAllEmailConfirmationsLogs() = withContext(dispatcherProvider.io()) {
        emailConfirmationLogDao.deleteAllEmailConfirmationLogs()
    }
}
