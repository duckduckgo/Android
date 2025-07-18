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
import com.duckduckgo.pir.internal.models.ExtractedProfile
import com.duckduckgo.pir.internal.scripts.models.AddressCityState
import com.duckduckgo.pir.internal.scripts.models.PirSuccessResponse.NavigateResponse
import com.duckduckgo.pir.internal.scripts.models.ProfileQuery
import com.duckduckgo.pir.internal.service.DbpService
import com.duckduckgo.pir.internal.service.DbpService.PirJsonBroker
import com.duckduckgo.pir.internal.store.PirRepository.BrokerJson
import com.duckduckgo.pir.internal.store.PirRepository.ConfirmationStatus
import com.duckduckgo.pir.internal.store.PirRepository.ScanResult
import com.duckduckgo.pir.internal.store.db.Broker
import com.duckduckgo.pir.internal.store.db.BrokerDao
import com.duckduckgo.pir.internal.store.db.BrokerJsonDao
import com.duckduckgo.pir.internal.store.db.BrokerJsonEtag
import com.duckduckgo.pir.internal.store.db.BrokerOptOut
import com.duckduckgo.pir.internal.store.db.BrokerScan
import com.duckduckgo.pir.internal.store.db.BrokerScanEventType.BROKER_ERROR
import com.duckduckgo.pir.internal.store.db.BrokerScanEventType.BROKER_SUCCESS
import com.duckduckgo.pir.internal.store.db.BrokerSchedulingConfig
import com.duckduckgo.pir.internal.store.db.OptOutActionLog
import com.duckduckgo.pir.internal.store.db.OptOutCompletedBroker
import com.duckduckgo.pir.internal.store.db.OptOutResultsDao
import com.duckduckgo.pir.internal.store.db.PirBrokerScanLog
import com.duckduckgo.pir.internal.store.db.PirEventLog
import com.duckduckgo.pir.internal.store.db.ScanCompletedBroker
import com.duckduckgo.pir.internal.store.db.ScanErrorResult
import com.duckduckgo.pir.internal.store.db.ScanLogDao
import com.duckduckgo.pir.internal.store.db.ScanNavigateResult
import com.duckduckgo.pir.internal.store.db.ScanResultsDao
import com.duckduckgo.pir.internal.store.db.StoredExtractedProfile
import com.duckduckgo.pir.internal.store.db.UserProfile
import com.duckduckgo.pir.internal.store.db.UserProfileDao
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
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

    suspend fun getBrokerOptOutSteps(name: String): String?

    /**
     * Returns a list of broker names for which an extract profile result is available.
     *
     * @param formOptOutOnly - True to only return the brokers with a form opt-out type.
     */
    suspend fun getBrokersForOptOut(formOptOutOnly: Boolean): List<String>

    suspend fun saveNavigateResult(
        brokerName: String,
        navigateResponse: NavigateResponse,
    )

    suspend fun saveErrorResult(
        brokerName: String,
        actionType: String,
        message: String,
    )

    suspend fun saveExtractedProfile(
        brokerName: String,
        profileQueryId: Long,
        extractedProfiles: List<ExtractedProfile>,
    )

    /**
     * Returns a list of all [ExtractedProfileResult] found for this particular broker.
     *
     * @param brokerName - Name of the broker
     */
    suspend fun getExtractedProfiles(
        brokerName: String,
        profileQueryId: Long,
    ): List<ExtractedProfile>

    fun getAllScanResultsFlow(): Flow<List<ScanResult>>

    suspend fun getScanErrorResultsCount(): Int

    suspend fun getScanSuccessResultsCount(): Int

    suspend fun deleteAllScanResults()

    suspend fun getUserProfiles(): List<UserProfile>

    suspend fun deleteAllUserProfiles()

    suspend fun replaceUserProfile(userProfile: UserProfile)

    fun getAllEventLogsFlow(): Flow<List<PirEventLog>>

    suspend fun saveScanLog(pirScanLog: PirEventLog)

    suspend fun saveBrokerScanLog(pirBrokerScanLog: PirBrokerScanLog)

    suspend fun deleteEventLogs()

    suspend fun getEmailForBroker(dataBroker: String): String

    suspend fun getEmailConfirmation(email: String): Pair<ConfirmationStatus, String?>

    fun getTotalScannedBrokersFlow(): Flow<Int>

    fun getTotalOptOutCompletedFlow(): Flow<Int>

    fun getAllOptOutActionLogFlow(): Flow<List<OptOutActionLog>>

    fun getAllSuccessfullySubmittedOptOutFlow(): Flow<Map<String, String>>

    suspend fun saveScanCompletedBroker(
        brokerName: String,
        startTimeInMillis: Long,
        endTimeInMillis: Long,
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
            val extractResults: List<ExtractedProfile> = emptyList(),
        ) : ScanResult(brokerName, completionTimeInMillis, actionType)

        data class ErrorResult(
            override val brokerName: String,
            override val completionTimeInMillis: Long,
            override val actionType: String,
            val message: String,
        ) : ScanResult(brokerName, completionTimeInMillis, actionType)
    }

    sealed class ConfirmationStatus(open val statusName: String) {
        data object Ready : ConfirmationStatus("ready")
        data object Pending : ConfirmationStatus("pending")
        data object Unknown : ConfirmationStatus("unknown")
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
    private val scanLogDao: ScanLogDao,
    private val dbpService: DbpService,
    private val optOutResultsDao: OptOutResultsDao,
) : PirRepository {
    private val profileQueryAdapter by lazy { moshi.adapter(ProfileQuery::class.java) }
    private val extractedProfileAdapter by lazy { moshi.adapter(ExtractedProfile::class.java) }
    private val addressCityStateAdapter by lazy { moshi.adapter(AddressCityState::class.java) }

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

    override suspend fun getBrokerOptOutSteps(name: String): String? = withContext(dispatcherProvider.io()) {
        brokerDao.getOptOutJson(name)
    }

    override suspend fun getBrokersForOptOut(formOptOutOnly: Boolean): List<String> = withContext(dispatcherProvider.io()) {
        scanResultsDao.getAllExtractProfileResult().filter {
            it.extractResults.isNotEmpty()
        }.map {
            it.brokerName
        }.distinct()
            .run {
                if (formOptOutOnly) {
                    this.filter {
                        brokerDao.getOptOutJson(it)?.contains("\"optOutType\":\"formOptOut\"") == true
                    }
                } else {
                    this
                }
            }
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
        message: String,
    ) {
        withContext(dispatcherProvider.io()) {
            scanResultsDao.insertScanErrorResult(
                ScanErrorResult(
                    brokerName = brokerName,
                    actionType = actionType,
                    message = message,
                    completionTimeInMillis = currentTimeProvider.currentTimeMillis(),
                ),
            )
        }
    }

    override suspend fun saveExtractedProfile(
        brokerName: String,
        profileQueryId: Long,
        extractedProfiles: List<ExtractedProfile>,
    ) {
        withContext(dispatcherProvider.io()) {
            extractedProfiles.map {
                StoredExtractedProfile(
                    profileUrl = it.profileUrl,
                    profileQueryId = profileQueryId,
                    brokerName = brokerName,
                    name = it.name,
                    alternativeNames = it.alternativeNames,
                    age = it.age,
                    addresses = it.addresses?.mapNotNull { item -> addressCityStateAdapter.toJson(item) },
                    phoneNumbers = it.phoneNumbers,
                    relatives = it.relatives,
                    identifier = it.identifier,
                    reportId = it.reportId,
                    email = it.email,
                    fullName = it.fullName,
                    dateAddedInMillis = it.dateAddedInMillis,
                    deprecated = it.deprecated,
                )
            }.also {
                scanResultsDao.insertExtractedProfiles(it)
            }
        }
    }

    override suspend fun getExtractedProfiles(
        brokerName: String,
        profileQueryId: Long,
    ): List<ExtractedProfile> = withContext(dispatcherProvider.io()) {
        return@withContext scanResultsDao.getExtractedProfilesForBrokerAndProfile(brokerName, profileQueryId).map {
            ExtractedProfile(
                profileUrl = it.profileUrl,
                profileQueryId = it.profileQueryId,
                brokerName = it.brokerName,
                name = it.name,
                alternativeNames = it.alternativeNames,
                age = it.age,
                addresses = it.addresses?.mapNotNull { item -> addressCityStateAdapter.fromJson(item) },
                phoneNumbers = it.phoneNumbers,
                relatives = it.relatives,
                identifier = it.identifier,
                reportId = it.reportId,
                email = it.email,
                fullName = it.fullName,
                dateAddedInMillis = it.dateAddedInMillis,
                deprecated = it.deprecated,
            )
        }
    }

    override fun getAllScanResultsFlow(): Flow<List<ScanResult>> {
        return combine(
            scanResultsDao.getAllNavigateResultsFlow(),
            scanResultsDao.getAllScanErrorResultsFlow(),
            scanResultsDao.getAllExtractProfileResultFlow(),
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
                        extractedProfileAdapter.fromJson(data)
                    },
                )
            }

            return@combine (navScanResult + errorScanResults + profileScanResults).sortedBy {
                it.completionTimeInMillis
            }
        }
    }

    override suspend fun deleteAllScanResults() {
        withContext(dispatcherProvider.io()) {
            scanResultsDao.deleteAllNavigateResults()
            scanResultsDao.deleteAllScanErrorResults()
            scanResultsDao.deleteAllExtractProfileResult()
            scanResultsDao.deleteAllScanCompletedBroker()
            scanLogDao.deleteAllBrokerScanEvents()
        }
    }

    override suspend fun getUserProfiles(): List<UserProfile> = withContext(dispatcherProvider.io()) {
        userProfileDao.getUserProfiles()
    }

    override suspend fun getScanErrorResultsCount(): Int = withContext(dispatcherProvider.io()) {
        scanLogDao.getAllBrokerScanEvents().filter { it.eventType == BROKER_ERROR }.size
    }

    override suspend fun getScanSuccessResultsCount(): Int = withContext(dispatcherProvider.io()) {
        scanLogDao.getAllBrokerScanEvents().filter { it.eventType == BROKER_SUCCESS }.size
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

    override fun getAllEventLogsFlow(): Flow<List<PirEventLog>> {
        return scanLogDao.getAllEventLogsFlow()
    }

    override suspend fun saveScanLog(pirScanLog: PirEventLog) {
        withContext(dispatcherProvider.io()) {
            scanLogDao.insertEventLog(pirScanLog)
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

    override suspend fun getEmailForBroker(dataBroker: String): String = withContext(dispatcherProvider.io()) {
        return@withContext dbpService.getEmail(brokerDao.getBrokerDetails(dataBroker)!!.url).emailAddress
    }

    override suspend fun getEmailConfirmation(email: String): Pair<ConfirmationStatus, String?> = withContext(dispatcherProvider.io()) {
        return@withContext dbpService.getEmailStatus(email).run {
            this.status.toConfirmationStatus() to this.link
        }
    }

    private fun String.toConfirmationStatus(): ConfirmationStatus {
        return when (this) {
            "pending" -> ConfirmationStatus.Pending
            "ready" -> ConfirmationStatus.Ready
            else -> ConfirmationStatus.Unknown
        }
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
                (extractedProfileAdapter.fromJson(it.extractedProfile)?.identifier ?: "Unknown") to it.brokerName
            }.distinct().toMap()
        }
    }

    override fun getAllOptOutActionLogFlow(): Flow<List<OptOutActionLog>> {
        return optOutResultsDao.getOptOutActionLogFlow()
    }

    override suspend fun saveScanCompletedBroker(
        brokerName: String,
        startTimeInMillis: Long,
        endTimeInMillis: Long,
    ) = withContext(dispatcherProvider.io()) {
        scanResultsDao.insertScanCompletedBroker(
            ScanCompletedBroker(
                brokerName = brokerName,
                startTimeInMillis = startTimeInMillis,
                endTimeInMillis = endTimeInMillis,
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
}
