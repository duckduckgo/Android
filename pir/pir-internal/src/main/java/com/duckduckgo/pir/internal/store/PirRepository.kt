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

import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.pir.internal.scripts.models.PirErrorReponse
import com.duckduckgo.pir.internal.scripts.models.PirSuccessResponse.ExtractedResponse
import com.duckduckgo.pir.internal.scripts.models.PirSuccessResponse.ExtractedResponse.ScrapedData
import com.duckduckgo.pir.internal.scripts.models.PirSuccessResponse.NavigateResponse
import com.duckduckgo.pir.internal.scripts.models.ProfileQuery
import com.duckduckgo.pir.internal.service.DbpService.PirJsonBroker
import com.duckduckgo.pir.internal.store.PirRepository.BrokerJson
import com.duckduckgo.pir.internal.store.PirRepository.ScanResult
import com.duckduckgo.pir.internal.store.db.Broker
import com.duckduckgo.pir.internal.store.db.BrokerDao
import com.duckduckgo.pir.internal.store.db.BrokerJsonDao
import com.duckduckgo.pir.internal.store.db.BrokerJsonEtag
import com.duckduckgo.pir.internal.store.db.BrokerOptOut
import com.duckduckgo.pir.internal.store.db.BrokerScan
import com.duckduckgo.pir.internal.store.db.BrokerSchedulingConfig
import com.duckduckgo.pir.internal.store.db.ExtractProfileResult
import com.duckduckgo.pir.internal.store.db.ScanErrorResult
import com.duckduckgo.pir.internal.store.db.ScanNavigateResult
import com.duckduckgo.pir.internal.store.db.ScanResultsDao
import com.duckduckgo.pir.internal.store.db.UserProfile
import com.duckduckgo.pir.internal.store.db.UserProfileDao
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import logcat.logcat

interface PirRepository {
    suspend fun getCurrentMainEtag(): String?

    suspend fun updateMainEtag(etag: String)

    suspend fun updateBrokerJsons(brokers: Map<BrokerJson, Boolean>)

    suspend fun getAllLocalBrokerJsons(): Map<BrokerJson, Boolean>

    suspend fun getActiveBrokerJsons(): List<BrokerJson>

    suspend fun getAllBrokersForScan(): List<String>

    suspend fun getEtagForFilename(fileName: String): String?

    suspend fun updateBrokerData(
        fileName: String,
        broker: PirJsonBroker,
    )

    suspend fun getBrokerScanSteps(name: String): String?

    suspend fun saveNavigateResult(
        brokerName: String,
        navigateResponse: NavigateResponse,
    )

    suspend fun saveErrorResult(
        brokerName: String,
        actionType: String,
        error: PirErrorReponse,
    )

    suspend fun saveExtractProfileResult(
        brokerName: String,
        response: ExtractedResponse,
    )

    fun getAllResultsFlow(): Flow<List<ScanResult>>

    suspend fun deleteAllResults()

    suspend fun getUserProfiles(): List<UserProfile>

    suspend fun deleteAllUserProfiles()

    suspend fun replaceUserProfile(userProfile: UserProfile)

    data class BrokerJson(
        val fileName: String,
        val etag: String,
    )

    sealed class ScanResult(
        open val brokerName: String,
        open val completionTimeInMillis: Long,
        open val actionType: String,
    ) {
        data class NavigateResult(
            override val brokerName: String,
            override val completionTimeInMillis: Long,
            override val actionType: String,
            val url: String,
        ) : ScanResult(brokerName, completionTimeInMillis, actionType)

        data class ExtractedProfileResult(
            override val brokerName: String,
            override val completionTimeInMillis: Long,
            override val actionType: String,
            val profileQuery: ProfileQuery?,
            val extractResults: List<ScrapedData> = emptyList(),
        ) : ScanResult(brokerName, completionTimeInMillis, actionType)

        data class ErrorResult(
            override val brokerName: String,
            override val completionTimeInMillis: Long,
            override val actionType: String,
            val message: String,
        ) : ScanResult(brokerName, completionTimeInMillis, actionType)
    }
}

class RealPirRepository(
    val moshi: Moshi,
    private val dispatcherProvider: DispatcherProvider,
    private val pirDataStore: PirDataStore,
    private val currentTimeProvider: CurrentTimeProvider,
    private val brokerJsonDao: BrokerJsonDao,
    private val brokerDao: BrokerDao,
    private val scanResultsDao: ScanResultsDao,
    private val userProfileDao: UserProfileDao,
) : PirRepository {
    private val profileQueryAdapter by lazy { moshi.adapter(ProfileQuery::class.java) }
    private val scrapedDataAdapter by lazy { moshi.adapter(ScrapedData::class.java) }

    override suspend fun getCurrentMainEtag(): String? = pirDataStore.mainConfigEtag

    override suspend fun updateMainEtag(etag: String) {
        pirDataStore.mainConfigEtag = etag
    }

    override suspend fun updateBrokerJsons(brokers: Map<BrokerJson, Boolean>) {
        withContext(dispatcherProvider.io()) {
            brokers.map {
                BrokerJsonEtag(
                    fileName = it.key.fileName,
                    etag = it.key.etag,
                    isActive = it.value,
                )
            }.also {
                brokerJsonDao.upsertAll(it)
            }
        }
    }

    override suspend fun getAllLocalBrokerJsons(): Map<BrokerJson, Boolean> = withContext(dispatcherProvider.io()) {
        return@withContext brokerJsonDao.getAllBrokers().associate {
            BrokerJson(
                fileName = it.fileName,
                etag = it.etag,
            ) to it.isActive
        }
    }

    override suspend fun getActiveBrokerJsons(): List<BrokerJson> = withContext(dispatcherProvider.io()) {
        return@withContext brokerJsonDao.getAllActiveBrokers().map { BrokerJson(fileName = it.fileName, etag = it.etag) }
    }

    override suspend fun getAllBrokersForScan(): List<String> = withContext(dispatcherProvider.io()) {
        return@withContext brokerDao.getAllBrokersNamesWithScanSteps()
    }

    override suspend fun getEtagForFilename(fileName: String): String? = withContext(dispatcherProvider.io()) {
        return@withContext brokerJsonDao.getEtag(fileName)
    }

    override suspend fun updateBrokerData(
        fileName: String,
        broker: PirJsonBroker,
    ) {
        withContext(dispatcherProvider.io()) {
            brokerDao.upsert(
                broker = Broker(
                    name = broker.name,
                    fileName = fileName,
                    url = broker.url,
                    version = broker.version,
                    parent = broker.parent,
                    addedDatetime = broker.addedDatetime,
                ),
                brokerScan = BrokerScan(
                    brokerName = broker.name,
                    stepsJson = broker.steps.first { it.contains("\"stepType\":\"scan\"") },
                ),
                brokerOptOut = BrokerOptOut(
                    brokerName = broker.name,
                    stepsJson = broker.steps.first { it.contains("\"stepType\":\"optOut\"") },
                    optOutUrl = broker.optOutUrl,
                ),
                schedulingConfig = BrokerSchedulingConfig(
                    brokerName = broker.name,
                    retryError = broker.schedulingConfig.retryError,
                    confirmOptOutScan = broker.schedulingConfig.confirmOptOutScan,
                    maintenanceScan = broker.schedulingConfig.maintenanceScan,
                    maxAttempts = broker.schedulingConfig.maxAttempts,
                ),
            )
        }
    }

    override suspend fun getBrokerScanSteps(name: String): String? = withContext(dispatcherProvider.io()) {
        brokerDao.getScanJson(name)
    }

    override suspend fun saveNavigateResult(
        brokerName: String,
        navigateResponse: NavigateResponse,
    ) {
        withContext(dispatcherProvider.io()) {
            scanResultsDao.insertNavigateResult(
                ScanNavigateResult(
                    brokerName = brokerName,
                    actionType = navigateResponse.actionType,
                    url = navigateResponse.response.url,
                    completionTimeInMillis = currentTimeProvider.currentTimeMillis(),
                ),
            )

            logcat { "PIR-SCAN: saveNavigateResult: $navigateResponse" }
        }
    }

    override suspend fun saveErrorResult(
        brokerName: String,
        actionType: String,
        error: PirErrorReponse,
    ) {
        withContext(dispatcherProvider.io()) {
            scanResultsDao.insertScanErrorResult(
                ScanErrorResult(
                    brokerName = brokerName,
                    actionType = actionType,
                    message = error.message,
                    completionTimeInMillis = currentTimeProvider.currentTimeMillis(),
                ),
            )
        }
    }

    override suspend fun saveExtractProfileResult(
        brokerName: String,
        response: ExtractedResponse,
    ) {
        withContext(dispatcherProvider.io()) {
            scanResultsDao.insertExtractProfileResult(
                ExtractProfileResult(
                    brokerName = brokerName,
                    actionType = response.actionType,
                    completionTimeInMillis = currentTimeProvider.currentTimeMillis(),
                    userData = profileQueryAdapter.toJson(response.meta?.userData),
                    extractResults = response.meta?.extractResults?.map {
                        scrapedDataAdapter.toJson(it)
                    } ?: emptyList(),
                ),
            )
        }
    }

    override fun getAllResultsFlow(): Flow<List<ScanResult>> {
        return combine(
            scanResultsDao.getAllNavigateResults(),
            scanResultsDao.getAllScanErrorResults(),
            scanResultsDao.getAllExtractProfileResult(),
        ) { navigateResults, errorResults, profileResults ->
            val navScanResult = navigateResults.map {
                ScanResult.NavigateResult(
                    brokerName = it.brokerName,
                    completionTimeInMillis = it.completionTimeInMillis,
                    actionType = it.actionType,
                    url = it.url,
                )
            }
            val errorScanResults = errorResults.map {
                ScanResult.ErrorResult(
                    brokerName = it.brokerName,
                    completionTimeInMillis = it.completionTimeInMillis,
                    actionType = it.actionType,
                    message = it.message,
                )
            }
            val profileScanResults = profileResults.map {
                ScanResult.ExtractedProfileResult(
                    brokerName = it.brokerName,
                    completionTimeInMillis = it.completionTimeInMillis,
                    actionType = it.actionType,
                    profileQuery = profileQueryAdapter.fromJson(it.userData),
                    extractResults = it.extractResults.mapNotNull { data ->
                        scrapedDataAdapter.fromJson(data)
                    },
                )
            }

            return@combine (navScanResult + errorScanResults + profileScanResults).sortedBy {
                it.completionTimeInMillis
            }
        }
    }

    override suspend fun deleteAllResults() {
        withContext(dispatcherProvider.io()) {
            scanResultsDao.deleteAllNavigateResults()
            scanResultsDao.deleteAllScanErrorResults()
            scanResultsDao.deleteAllExtractProfileResult()
        }
    }

    override suspend fun getUserProfiles(): List<UserProfile> = withContext(dispatcherProvider.io()) {
        userProfileDao.getUserProfiles()
    }

    override suspend fun deleteAllUserProfiles() {
        withContext(dispatcherProvider.io()) {
            userProfileDao.deleteAllProfiles()
        }
    }

    override suspend fun replaceUserProfile(userProfile: UserProfile) {
        withContext(dispatcherProvider.io()) {
            userProfileDao.deleteAllProfiles()
            userProfileDao.insertUserProfile(userProfile)
        }
    }
}
