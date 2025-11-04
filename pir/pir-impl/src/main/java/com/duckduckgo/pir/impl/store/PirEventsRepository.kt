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

import com.duckduckgo.app.di.AppCoroutineScope
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
import com.duckduckgo.pir.impl.store.secure.PirSecureStorageDatabaseFactory
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.Moshi
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import logcat.LogPriority.ERROR
import logcat.logcat
import javax.inject.Inject

interface PirEventsRepository {
    suspend fun getAllEventLogsFlow(): Flow<List<PirEventLog>>

    suspend fun saveEventLog(pirEventLog: PirEventLog)

    suspend fun saveBrokerScanLog(pirBrokerScanLog: PirBrokerScanLog)

    suspend fun deleteEventLogs()

    suspend fun getScannedBrokersFlow(): Flow<List<ScanCompletedBroker>>

    suspend fun getScanErrorResultsCount(): Int

    suspend fun getScanSuccessResultsCount(): Int

    suspend fun deleteAllScanResults()

    suspend fun getTotalScannedBrokersFlow(): Flow<Int>

    suspend fun getTotalOptOutCompletedFlow(): Flow<Int>

    suspend fun getAllOptOutActionLogFlow(): Flow<List<OptOutActionLog>>

    suspend fun getAllSuccessfullySubmittedOptOutFlow(): Flow<Map<String, String>>

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

    suspend fun getAllEmailConfirmationLogFlow(): Flow<List<PirEmailConfirmationLog>>

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
    private val databaseFactory: PirSecureStorageDatabaseFactory,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : PirEventsRepository {
    private val extractedProfileAdapter by lazy { moshi.adapter(ExtractedProfile::class.java) }

    private val database: Deferred<PirDatabase?> = appCoroutineScope.async(start = CoroutineStart.LAZY) {
        prepareDatabase()
    }

    override suspend fun deleteAllScanResults() {
        withContext(dispatcherProvider.io()) {
            scanResultsDao()?.deleteAllScanCompletedBroker()
            scanLogDao()?.deleteAllBrokerScanEvents()
        }
    }

    override suspend fun getScanErrorResultsCount(): Int = withContext(dispatcherProvider.io()) {
        scanLogDao()?.getAllBrokerScanEvents()?.filter { it.eventType == BROKER_ERROR }?.size ?: 0
    }

    override suspend fun getScanSuccessResultsCount(): Int = withContext(dispatcherProvider.io()) {
        scanLogDao()?.getAllBrokerScanEvents()?.filter { it.eventType == BROKER_SUCCESS }?.size ?: 0
    }

    override suspend fun getAllEventLogsFlow(): Flow<List<PirEventLog>> {
        return scanLogDao()?.getAllEventLogsFlow() ?: emptyFlow()
    }

    override suspend fun saveEventLog(pirEventLog: PirEventLog) {
        withContext(dispatcherProvider.io()) {
            scanLogDao()?.insertEventLog(pirEventLog)
        }
    }

    override suspend fun saveBrokerScanLog(pirBrokerScanLog: PirBrokerScanLog) {
        withContext(dispatcherProvider.io()) {
            scanLogDao()?.insertBrokerScanEvent(pirBrokerScanLog)
        }
    }

    override suspend fun deleteEventLogs() {
        withContext(dispatcherProvider.io()) {
            scanLogDao()?.deleteAllEventLogs()
        }
    }

    override suspend fun getScannedBrokersFlow(): Flow<List<ScanCompletedBroker>> {
        return scanResultsDao()?.getScanCompletedBrokerFlow() ?: flowOf(emptyList())
    }

    override suspend fun getTotalScannedBrokersFlow(): Flow<Int> {
        return scanResultsDao()?.getScanCompletedBrokerFlow()?.map { it.size } ?: flowOf(0)
    }

    override suspend fun getTotalOptOutCompletedFlow(): Flow<Int> {
        return optOutResultsDao()?.getOptOutCompletedBrokerFlow()?.map { it.size } ?: flowOf(0)
    }

    override suspend fun getAllSuccessfullySubmittedOptOutFlow(): Flow<Map<String, String>> {
        return optOutResultsDao()?.getOptOutCompletedBrokerFlow()?.map {
            it.filter {
                it.isSubmitSuccess
            }.map {
                (
                    extractedProfileAdapter.fromJson(it.extractedProfile)?.identifier
                        ?: "Unknown"
                    ) to it.brokerName
            }.distinct().toMap()
        } ?: flowOf(emptyMap())
    }

    override suspend fun getAllOptOutActionLogFlow(): Flow<List<OptOutActionLog>> {
        return optOutResultsDao()?.getOptOutActionLogFlow() ?: flowOf(emptyList())
    }

    override suspend fun saveScanCompletedBroker(
        brokerName: String,
        profileQueryId: Long,
        startTimeInMillis: Long,
        endTimeInMillis: Long,
        isSuccess: Boolean,
    ): Unit = withContext(dispatcherProvider.io()) {
        scanResultsDao()?.insertScanCompletedBroker(
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
    ): Unit = withContext(dispatcherProvider.io()) {
        optOutResultsDao()?.insertOptOutCompletedBroker(
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
    ): Unit = withContext(dispatcherProvider.io()) {
        optOutResultsDao()?.insertOptOutActionLog(
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

    override suspend fun deleteAllOptOutData(): Unit = withContext(dispatcherProvider.io()) {
        optOutResultsDao()?.deleteAllOptOutActionLog()
        optOutResultsDao()?.deleteAllOptOutCompletedBroker()
    }

    override suspend fun saveEmailConfirmationLog(
        eventTimeInMillis: Long,
        type: EmailConfirmationEventType,
        detail: String,
    ): Unit = withContext(dispatcherProvider.io()) {
        emailConfirmationLogDao()?.insertEmailConfirmationLog(
            PirEmailConfirmationLog(
                eventTimeInMillis = eventTimeInMillis,
                eventType = type,
                value = detail,
            ),
        )
    }

    override suspend fun getAllEmailConfirmationLogFlow(): Flow<List<PirEmailConfirmationLog>> {
        return emailConfirmationLogDao()?.getAllEmailConfirmationLogsFlow() ?: flowOf(emptyList())
    }

    override suspend fun deleteAllEmailConfirmationsLogs(): Unit = withContext(dispatcherProvider.io()) {
        emailConfirmationLogDao()?.deleteAllEmailConfirmationLogs()
    }

    private suspend fun prepareDatabase(): PirDatabase? {
        val database = databaseFactory.getDatabase()
        return if (database != null && database.databaseContentsAreReadable()) {
            database
        } else {
            logcat(ERROR) { "PIR-DB: PIR events repository is not readable" }
            null
        }
    }

    private fun PirDatabase.databaseContentsAreReadable(): Boolean {
        return kotlin.runCatching {
            // Try to read from the database to verify it's accessible
            brokerJsonDao().getAllBrokersCount()
            true
        }.getOrElse {
            logcat(ERROR) { "PIR-DB: Error reading from PIR events repository: ${it.message}" }
            false
        }
    }

    private suspend fun scanResultsDao(): ScanResultsDao? = database.await()?.scanResultsDao()

    private suspend fun scanLogDao(): ScanLogDao? = database.await()?.scanLogDao()

    private suspend fun optOutResultsDao(): OptOutResultsDao? = database.await()?.optOutResultsDao()

    private suspend fun emailConfirmationLogDao(): EmailConfirmationLogDao? = database.await()?.emailConfirmationLogDao()
}
