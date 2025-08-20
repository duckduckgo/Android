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
import com.duckduckgo.pir.impl.models.Address
import com.duckduckgo.pir.impl.models.Broker
import com.duckduckgo.pir.impl.models.ExtractedProfile
import com.duckduckgo.pir.impl.models.MirrorSite
import com.duckduckgo.pir.impl.models.ProfileQuery
import com.duckduckgo.pir.impl.models.scheduling.BrokerSchedulingConfig
import com.duckduckgo.pir.impl.service.DbpService
import com.duckduckgo.pir.impl.service.DbpService.PirJsonBroker
import com.duckduckgo.pir.impl.store.PirRepository.BrokerJson
import com.duckduckgo.pir.impl.store.PirRepository.ConfirmationStatus
import com.duckduckgo.pir.impl.store.db.BrokerDao
import com.duckduckgo.pir.impl.store.db.BrokerEntity
import com.duckduckgo.pir.impl.store.db.BrokerJsonDao
import com.duckduckgo.pir.impl.store.db.BrokerJsonEtag
import com.duckduckgo.pir.impl.store.db.BrokerOptOut
import com.duckduckgo.pir.impl.store.db.BrokerScan
import com.duckduckgo.pir.impl.store.db.BrokerSchedulingConfigEntity
import com.duckduckgo.pir.impl.store.db.ExtractedProfileDao
import com.duckduckgo.pir.impl.store.db.MirrorSiteEntity
import com.duckduckgo.pir.impl.store.db.StoredExtractedProfile
import com.duckduckgo.pir.impl.store.db.UserProfile
import com.duckduckgo.pir.impl.store.db.UserProfileDao
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

interface PirRepository {
    suspend fun getCurrentMainEtag(): String?

    suspend fun updateMainEtag(etag: String?)

    suspend fun updateBrokerJsons(brokers: List<BrokerJson>)

    suspend fun getAllLocalBrokerJsons(): List<BrokerJson>

    suspend fun getStoredBrokersCount(): Int

    suspend fun getAllActiveBrokers(): List<String>

    suspend fun getAllActiveBrokerObjects(): List<Broker>

    suspend fun getAllMirrorSitesForBroker(brokerName: String): List<MirrorSite>

    suspend fun getAllMirrorSites(): List<MirrorSite>

    suspend fun getAllBrokersForScan(): List<String>

    suspend fun getAllBrokerOptOuts(): List<BrokerOptOut>

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

    /**
     * This method saves the new extracted profiles to the database.
     * Any existing profiles (see indices of the table), we ignore them
     */
    suspend fun saveNewExtractedProfiles(
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

    suspend fun getUserProfileQueries(): List<ProfileQuery>

    suspend fun getUserProfileQueriesWithIds(ids: List<Long>): List<ProfileQuery>

    suspend fun deleteAllUserProfilesQueries()

    suspend fun replaceUserProfile(userProfile: UserProfile)

    suspend fun saveUserProfiles(userProfiles: List<UserProfile>)

    suspend fun getEmailForBroker(dataBroker: String): String

    suspend fun getEmailConfirmation(email: String): Pair<ConfirmationStatus, String?>

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
    private val dispatcherProvider: DispatcherProvider,
    private val pirDataStore: PirDataStore,
    private val currentTimeProvider: CurrentTimeProvider,
    private val brokerJsonDao: BrokerJsonDao,
    private val brokerDao: BrokerDao,
    private val userProfileDao: UserProfileDao,
    private val dbpService: DbpService,
    private val extractedProfileDao: ExtractedProfileDao,
) : PirRepository {

    override suspend fun getCurrentMainEtag(): String? = pirDataStore.mainConfigEtag

    override suspend fun updateMainEtag(etag: String?) {
        pirDataStore.mainConfigEtag = etag
    }

    override suspend fun updateBrokerJsons(brokers: List<BrokerJson>) {
        withContext(dispatcherProvider.io()) {
            brokers.map {
                BrokerJsonEtag(
                    fileName = it.fileName,
                    etag = it.etag,
                )
            }.also {
                brokerJsonDao.insertBrokerJsonEtags(it)
            }
        }
    }

    override suspend fun getAllLocalBrokerJsons(): List<BrokerJson> =
        withContext(dispatcherProvider.io()) {
            return@withContext brokerJsonDao.getAllBrokers().map {
                BrokerJson(
                    fileName = it.fileName,
                    etag = it.etag,
                )
            }
        }

    override suspend fun getStoredBrokersCount(): Int = withContext(dispatcherProvider.io()) {
        return@withContext brokerJsonDao.getAllBrokersCount()
    }

    override suspend fun getAllActiveBrokers(): List<String> =
        withContext(dispatcherProvider.io()) {
            return@withContext brokerDao.getAllActiveBrokers().map {
                it.name
            }
        }

    override suspend fun getAllActiveBrokerObjects(): List<Broker> =
        withContext(dispatcherProvider.io()) {
            return@withContext brokerDao.getAllActiveBrokers().map {
                Broker(
                    name = it.name,
                    fileName = it.fileName,
                    url = it.url,
                    version = it.version,
                    parent = it.parent,
                    addedDatetime = it.addedDatetime,
                    removedAt = it.removedAt,
                )
            }
        }

    override suspend fun getAllMirrorSitesForBroker(brokerName: String): List<MirrorSite> {
        return brokerDao.getAllMirrorSitesForBroker(brokerName).map {
            MirrorSite(
                name = it.name,
                url = it.url,
                addedAt = it.addedAt,
                removedAt = it.removedAt,
                optOutUrl = it.optOutUrl,
                parentSite = it.parentSite,
            )
        }
    }

    override suspend fun getAllMirrorSites(): List<MirrorSite> {
        return brokerDao.getAllMirrorSites().map {
            MirrorSite(
                name = it.name,
                url = it.url,
                addedAt = it.addedAt,
                removedAt = it.removedAt,
                optOutUrl = it.optOutUrl,
                parentSite = it.parentSite,
            )
        }
    }

    override suspend fun getAllBrokersForScan(): List<String> =
        withContext(dispatcherProvider.io()) {
            return@withContext brokerDao.getAllBrokersNamesWithScanSteps()
        }

    override suspend fun getAllBrokerOptOuts(): List<BrokerOptOut> =
        withContext(dispatcherProvider.io()) {
            return@withContext brokerDao.getAllBrokerOptOuts()
        }

    override suspend fun getEtagForFilename(fileName: String): String =
        withContext(dispatcherProvider.io()) {
            return@withContext brokerJsonDao.getEtag(fileName)
        }

    override suspend fun updateBrokerData(
        fileName: String,
        broker: PirJsonBroker,
    ) {
        withContext(dispatcherProvider.io()) {
            brokerDao.upsert(
                broker = BrokerEntity(
                    name = broker.name,
                    fileName = fileName,
                    url = broker.url,
                    version = broker.version,
                    parent = broker.parent,
                    addedDatetime = broker.addedDatetime,
                    removedAt = broker.removedAt ?: 0L,
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
                mirrorSiteEntity = broker.mirrorSites.map {
                    MirrorSiteEntity(
                        name = it.name,
                        url = it.url,
                        addedAt = it.addedAt,
                        removedAt = it.removedAt ?: 0L,
                        optOutUrl = it.optOutUrl.orEmpty(),
                        parentSite = broker.name,
                    )
                },
            )
        }
    }

    override suspend fun getBrokerSchedulingConfig(brokerName: String): BrokerSchedulingConfig? =
        withContext(dispatcherProvider.io()) {
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

    override suspend fun getAllBrokerSchedulingConfigs(): List<BrokerSchedulingConfig> =
        withContext(dispatcherProvider.io()) {
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

    override suspend fun getBrokerScanSteps(name: String): String? =
        withContext(dispatcherProvider.io()) {
            brokerDao.getScanJson(name)
        }

    override suspend fun getBrokerOptOutSteps(name: String): String? =
        withContext(dispatcherProvider.io()) {
            brokerDao.getOptOutJson(name)
        }

    override suspend fun getBrokersForOptOut(formOptOutOnly: Boolean): List<String> =
        withContext(dispatcherProvider.io()) {
            extractedProfileDao.getAllExtractedProfiles().map {
                it.brokerName
            }.distinct().run {
                if (formOptOutOnly) {
                    this.filter {
                        brokerDao.getOptOutJson(it)
                            ?.contains("\"optOutType\":\"formOptOut\"") == true
                    }
                } else {
                    this
                }
            }
        }

    override suspend fun saveNewExtractedProfiles(
        extractedProfiles: List<ExtractedProfile>,
    ) {
        withContext(dispatcherProvider.io()) {
            extractedProfiles.map {
                it.toStoredExtractedProfile()
            }.also {
                extractedProfileDao.insertNewExtractedProfiles(it)
            }
        }
    }

    override suspend fun getExtractedProfiles(
        brokerName: String,
        profileQueryId: Long,
    ): List<ExtractedProfile> = withContext(dispatcherProvider.io()) {
        return@withContext extractedProfileDao.getExtractedProfilesForBrokerAndProfile(
            brokerName,
            profileQueryId,
        ).map {
            it.toExtractedProfile()
        }
    }

    override fun getAllExtractedProfilesFlow(): Flow<List<ExtractedProfile>> {
        return extractedProfileDao.getAllExtractedProfileFlow().map { list ->
            list.map {
                it.toExtractedProfile()
            }
        }
    }

    override suspend fun getAllExtractedProfiles(): List<ExtractedProfile> =
        withContext(dispatcherProvider.io()) {
            return@withContext extractedProfileDao.getAllExtractedProfiles().map {
                it.toExtractedProfile()
            }
        }

    override suspend fun getUserProfileQueries(): List<ProfileQuery> =
        withContext(dispatcherProvider.io()) {
            userProfileDao.getUserProfiles().map {
                it.toProfileQuery()
            }
        }

    override suspend fun getUserProfileQueriesWithIds(ids: List<Long>): List<ProfileQuery> =
        withContext(dispatcherProvider.io()) {
            userProfileDao.getUserProfilesWithIds(ids).map {
                it.toProfileQuery()
            }
        }

    override suspend fun deleteAllUserProfilesQueries() {
        withContext(dispatcherProvider.io()) {
            userProfileDao.deleteAllProfiles()
            extractedProfileDao.deleteAllExtractedProfiles()
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

    override suspend fun replaceUserProfile(userProfile: UserProfile) {
        withContext(dispatcherProvider.io()) {
            userProfileDao.deleteAllProfiles()
            userProfileDao.insertUserProfile(userProfile)
        }
    }

    override suspend fun saveUserProfiles(userProfiles: List<UserProfile>) =
        withContext(dispatcherProvider.io()) {
            userProfileDao.insertUserProfiles(userProfiles)
        }

    override suspend fun getEmailForBroker(dataBroker: String): String =
        withContext(dispatcherProvider.io()) {
            return@withContext dbpService.getEmail(brokerDao.getBrokerDetails(dataBroker)!!.url).emailAddress
        }

    override suspend fun getEmailConfirmation(email: String): Pair<ConfirmationStatus, String?> =
        withContext(dispatcherProvider.io()) {
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
