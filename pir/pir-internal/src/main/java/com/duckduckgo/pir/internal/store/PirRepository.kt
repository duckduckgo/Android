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
import com.duckduckgo.pir.internal.models.Address
import com.duckduckgo.pir.internal.models.ExtractedProfile
import com.duckduckgo.pir.internal.models.ProfileQuery
import com.duckduckgo.pir.internal.models.scheduling.BrokerSchedulingConfig
import com.duckduckgo.pir.internal.service.DbpService
import com.duckduckgo.pir.internal.service.DbpService.PirJsonBroker
import com.duckduckgo.pir.internal.store.PirRepository.BrokerJson
import com.duckduckgo.pir.internal.store.PirRepository.ConfirmationStatus
import com.duckduckgo.pir.internal.store.db.Broker
import com.duckduckgo.pir.internal.store.db.BrokerDao
import com.duckduckgo.pir.internal.store.db.BrokerJsonDao
import com.duckduckgo.pir.internal.store.db.BrokerJsonEtag
import com.duckduckgo.pir.internal.store.db.BrokerOptOut
import com.duckduckgo.pir.internal.store.db.BrokerScan
import com.duckduckgo.pir.internal.store.db.BrokerScanEventType.BROKER_ERROR
import com.duckduckgo.pir.internal.store.db.BrokerScanEventType.BROKER_SUCCESS
import com.duckduckgo.pir.internal.store.db.BrokerSchedulingConfigEntity
import com.duckduckgo.pir.internal.store.db.OptOutActionLog
import com.duckduckgo.pir.internal.store.db.OptOutCompletedBroker
import com.duckduckgo.pir.internal.store.db.OptOutResultsDao
import com.duckduckgo.pir.internal.store.db.PirBrokerScanLog
import com.duckduckgo.pir.internal.store.db.PirEventLog
import com.duckduckgo.pir.internal.store.db.ScanCompletedBroker
import com.duckduckgo.pir.internal.store.db.ScanLogDao
import com.duckduckgo.pir.internal.store.db.ScanResultsDao
import com.duckduckgo.pir.internal.store.db.StoredExtractedProfile
import com.duckduckgo.pir.internal.store.db.UserProfile
import com.duckduckgo.pir.internal.store.db.UserProfileDao
import com.squareup.moshi.Moshi
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

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

    suspend fun getBrokerSchedulingConfig(brokerName: String): BrokerSchedulingConfig?

    suspend fun getAllBrokerSchedulingConfigs(): List<BrokerSchedulingConfig>

    suspend fun getBrokerScanSteps(name: String): String?

    suspend fun getBrokerOptOutSteps(name: String): String?

    /**
     * Returns a list of broker names for which an extract profile result is available.
     *
     * @param formOptOutOnly - True to only return the brokers with a form opt-out type.
     */
    suspend fun getBrokersForOptOut(formOptOutOnly: Boolean): List<String>

    suspend fun saveExtractedProfile(
        extractedProfiles: List<ExtractedProfile>,
    )

    /**
     * Returns a list of all [ExtractedProfile] found for this particular broker.
     *
     * @param brokerName - Name of the broker
     *  @param profileQueryId - Profile id of the user submitted profile
     */
    suspend fun getExtractedProfiles(
        brokerName: String,
        profileQueryId: Long,
    ): List<ExtractedProfile>

    fun getAllExtractedProfilesFlow(): Flow<List<ExtractedProfile>>

    suspend fun getAllExtractedProfiles(): List<ExtractedProfile>

    suspend fun getScanErrorResultsCount(): Int

    suspend fun getScanSuccessResultsCount(): Int

    suspend fun deleteAllScanResults()

    suspend fun getUserProfileQueries(): List<ProfileQuery>

    suspend fun getUserProfileQueriesWithIds(ids: List<Long>): List<ProfileQuery>

    suspend fun deleteAllUserProfilesQueries()

    suspend fun replaceUserProfile(userProfile: UserProfile)

    fun getAllEventLogsFlow(): Flow<List<PirEventLog>>

    suspend fun saveScanLog(pirScanLog: PirEventLog)

    suspend fun saveBrokerScanLog(pirBrokerScanLog: PirBrokerScanLog)

    suspend fun deleteEventLogs()

    suspend fun getEmailForBroker(dataBroker: String): String

    suspend fun getEmailConfirmation(email: String): Pair<ConfirmationStatus, String?>

    fun getScannedBrokersFlow(): Flow<List<ScanCompletedBroker>>

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

    data class BrokerJson(
        val fileName: String,
        val etag: String,
    )

    sealed class ConfirmationStatus(open val statusName: String) {
        data object Ready : ConfirmationStatus("ready")
        data object Pending : ConfirmationStatus("pending")
        data object Unknown : ConfirmationStatus("unknown")
    }
}

internal class RealPirRepository(
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
    private val extractedProfileAdapter by lazy { moshi.adapter(ExtractedProfile::class.java) }

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

    override suspend fun getEtagForFilename(fileName: String): String = withContext(dispatcherProvider.io()) {
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
                schedulingConfig = BrokerSchedulingConfigEntity(
                    brokerName = broker.name,
                    retryError = broker.schedulingConfig.retryError,
                    confirmOptOutScan = broker.schedulingConfig.confirmOptOutScan,
                    maintenanceScan = broker.schedulingConfig.maintenanceScan,
                    maxAttempts = broker.schedulingConfig.maxAttempts,
                ),
            )
        }
    }

    override suspend fun getBrokerSchedulingConfig(brokerName: String): BrokerSchedulingConfig? = withContext(dispatcherProvider.io()) {
        return@withContext brokerDao.getSchedulingConfig(brokerName)?.run {
            BrokerSchedulingConfig(
                brokerName = this.brokerName,
                retryErrorInMillis = TimeUnit.HOURS.toMillis(this.retryError.toLong()),
                confirmOptOutScanInMillis = TimeUnit.HOURS.toMillis(this.confirmOptOutScan.toLong()),
                maintenanceScanInMillis = TimeUnit.HOURS.toMillis(this.maintenanceScan.toLong()),
                maxAttempts = this.maxAttempts ?: -1,
            )
        }
    }

    override suspend fun getAllBrokerSchedulingConfigs(): List<BrokerSchedulingConfig> = withContext(dispatcherProvider.io()) {
        return@withContext brokerDao.getAllSchedulingConfigs().map {
            BrokerSchedulingConfig(
                brokerName = it.brokerName,
                retryErrorInMillis = TimeUnit.HOURS.toMillis(it.retryError.toLong()),
                confirmOptOutScanInMillis = TimeUnit.HOURS.toMillis(it.confirmOptOutScan.toLong()),
                maintenanceScanInMillis = TimeUnit.HOURS.toMillis(it.maintenanceScan.toLong()),
                maxAttempts = it.maxAttempts ?: -1,
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
        scanResultsDao.getAllExtractedProfiles().map {
            it.brokerName
        }.distinct().run {
            if (formOptOutOnly) {
                this.filter {
                    brokerDao.getOptOutJson(it)?.contains("\"optOutType\":\"formOptOut\"") == true
                }
            } else {
                this
            }
        }
    }

    override suspend fun saveExtractedProfile(
        extractedProfiles: List<ExtractedProfile>,
    ) {
        withContext(dispatcherProvider.io()) {
            extractedProfiles.map {
                it.toStoredExtractedProfile()
            }.also {
                val updated = scanResultsDao.updateExtractedProfiles(it)
                if (updated == 0) {
                    // If no profiles were updated, insert them
                    scanResultsDao.insertExtractedProfiles(it)
                }
            }
        }
    }

    override suspend fun getExtractedProfiles(
        brokerName: String,
        profileQueryId: Long,
    ): List<ExtractedProfile> = withContext(dispatcherProvider.io()) {
        return@withContext scanResultsDao.getExtractedProfilesForBrokerAndProfile(brokerName, profileQueryId).map {
            it.toExtractedProfile()
        }
    }

    override fun getAllExtractedProfilesFlow(): Flow<List<ExtractedProfile>> {
        return scanResultsDao.getAllExtractedProfileFlow().map { list ->
            list.map {
                it.toExtractedProfile()
            }
        }
    }

    override suspend fun getAllExtractedProfiles(): List<ExtractedProfile> = withContext(dispatcherProvider.io()) {
        return@withContext scanResultsDao.getAllExtractedProfiles().map {
            it.toExtractedProfile()
        }
    }

    override suspend fun deleteAllScanResults() {
        withContext(dispatcherProvider.io()) {
            scanResultsDao.deleteAllScanCompletedBroker()
            scanLogDao.deleteAllBrokerScanEvents()
        }
    }

    override suspend fun getUserProfileQueries(): List<ProfileQuery> = withContext(dispatcherProvider.io()) {
        userProfileDao.getUserProfiles().map {
            it.toProfileQuery()
        }
    }

    override suspend fun getUserProfileQueriesWithIds(ids: List<Long>): List<ProfileQuery> = withContext(dispatcherProvider.io()) {
        userProfileDao.getUserProfilesWithIds(ids).map {
            it.toProfileQuery()
        }
    }

    override suspend fun deleteAllUserProfilesQueries() {
        withContext(dispatcherProvider.io()) {
            userProfileDao.deleteAllProfiles()
            scanResultsDao.deleteAllExtractedProfiles()
        }
    }

    private fun UserProfile.toProfileQuery(): ProfileQuery {
        return ProfileQuery(
            id = this.id,
            firstName = this.userName.firstName,
            lastName = this.userName.lastName,
            city = this.addresses.city,
            state = this.addresses.state,
            addresses = listOf(
                Address(
                    city = this.addresses.city,
                    state = this.addresses.state,
                ),
            ),
            birthYear = this.birthYear,
            fullName = this.userName.middleName?.let { middleName ->
                "${this.userName.firstName} $middleName ${this.userName.lastName}"
            } ?: "${this.userName.firstName} ${this.userName.lastName}",
            age = currentTimeProvider.localDateTimeNow().year - this.birthYear,
            deprecated = false,
        )
    }

    override suspend fun getScanErrorResultsCount(): Int = withContext(dispatcherProvider.io()) {
        scanLogDao.getAllBrokerScanEvents().filter { it.eventType == BROKER_ERROR }.size
    }

    override suspend fun getScanSuccessResultsCount(): Int = withContext(dispatcherProvider.io()) {
        scanLogDao.getAllBrokerScanEvents().filter { it.eventType == BROKER_SUCCESS }.size
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
                (extractedProfileAdapter.fromJson(it.extractedProfile)?.identifier ?: "Unknown") to it.brokerName
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

    private fun StoredExtractedProfile.toExtractedProfile(): ExtractedProfile {
        return ExtractedProfile(
            dbId = this.id,
            profileUrl = this.profileUrl,
            profileQueryId = this.profileQueryId,
            brokerName = this.brokerName,
            name = this.name,
            alternativeNames = this.alternativeNames,
            age = this.age,
            addresses = this.addresses,
            phoneNumbers = this.phoneNumbers,
            relatives = this.relatives,
            identifier = this.identifier,
            reportId = this.reportId,
            email = this.email,
            fullName = this.fullName,
            dateAddedInMillis = this.dateAddedInMillis,
            deprecated = this.deprecated,
        )
    }

    private fun ExtractedProfile.toStoredExtractedProfile(): StoredExtractedProfile {
        return StoredExtractedProfile(
            id = this.dbId,
            profileQueryId = this.profileQueryId,
            brokerName = this.brokerName,
            name = this.name,
            alternativeNames = this.alternativeNames,
            age = this.age,
            addresses = this.addresses,
            phoneNumbers = this.phoneNumbers,
            relatives = this.relatives,
            reportId = this.reportId,
            email = this.email,
            fullName = this.fullName,
            profileUrl = this.profileUrl,
            identifier = this.identifier,
            dateAddedInMillis = if (this.dateAddedInMillis == 0L) {
                currentTimeProvider.currentTimeMillis()
            } else {
                this.dateAddedInMillis
            },
            deprecated = this.deprecated,
        )
    }
}
