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
import com.duckduckgo.pir.impl.models.AddressCityState
import com.duckduckgo.pir.impl.models.Broker
import com.duckduckgo.pir.impl.models.ExtractedProfile
import com.duckduckgo.pir.impl.models.MirrorSite
import com.duckduckgo.pir.impl.models.ProfileQuery
import com.duckduckgo.pir.impl.models.scheduling.BrokerSchedulingConfig
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.EmailConfirmationJobRecord.EmailData
import com.duckduckgo.pir.impl.pixels.PirPixelSender
import com.duckduckgo.pir.impl.service.DbpService
import com.duckduckgo.pir.impl.service.DbpService.PirEmailConfirmationDataRequest
import com.duckduckgo.pir.impl.service.DbpService.PirJsonBroker
import com.duckduckgo.pir.impl.store.PirRepository.BrokerJson
import com.duckduckgo.pir.impl.store.PirRepository.EmailConfirmationLinkFetchStatus
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
import com.duckduckgo.pir.impl.store.db.UserName
import com.duckduckgo.pir.impl.store.db.UserProfile
import com.duckduckgo.pir.impl.store.db.UserProfileDao
import com.duckduckgo.pir.impl.store.secure.PirSecureStorageDatabaseFactory
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import logcat.LogPriority.ERROR
import logcat.logcat
import java.util.concurrent.TimeUnit

interface PirRepository {
    /**
     * @return Returns `true` if the repository and underlying database is available for use, `false` otherwise.
     */
    suspend fun isRepositoryAvailable(): Boolean

    suspend fun getCurrentMainEtag(): String?

    suspend fun updateMainEtag(etag: String?)

    suspend fun updateBrokerJsons(brokers: List<BrokerJson>)

    suspend fun getAllLocalBrokerJsons(): List<BrokerJson>

    suspend fun getStoredBrokersCount(): Int

    suspend fun getAllActiveBrokers(): List<String>

    suspend fun getAllActiveBrokerObjects(): List<Broker>

    suspend fun getBrokerForName(name: String): Broker?

    suspend fun getAllMirrorSitesForBroker(brokerName: String): List<MirrorSite>

    suspend fun getAllMirrorSites(): List<MirrorSite>

    suspend fun getAllBrokersForScan(): List<String>

    /**
     * Returns a map of broker names to their opt-out URLs.
     */
    suspend fun getAllBrokerOptOutUrls(): Map<String, String?>

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
    suspend fun saveNewExtractedProfiles(extractedProfiles: List<ExtractedProfile>)

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

    suspend fun getExtractedProfile(
        extractedProfileId: Long,
    ): ExtractedProfile?

    suspend fun getAllExtractedProfilesFlow(): Flow<List<ExtractedProfile>>

    suspend fun getAllExtractedProfiles(): List<ExtractedProfile>

    suspend fun getUserProfileQuery(id: Long): ProfileQuery?

    /**
     * Returns all user profile queries stored in the database, including deprecated ones.
     * In some cases we still want to run jobs on them.
     */
    suspend fun getAllUserProfileQueries(): List<ProfileQuery>

    /**
     * Returns all user profile queries that are not marked as deprecated.
     */
    suspend fun getValidUserProfileQueries(): List<ProfileQuery>

    suspend fun getUserProfileQueriesWithIds(ids: List<Long>): List<ProfileQuery>

    suspend fun deleteAllUserProfilesQueries()

    suspend fun replaceUserProfile(profileQuery: ProfileQuery)

    suspend fun updateProfileQueries(
        profileQueriesToAdd: List<ProfileQuery>,
        profileQueriesToUpdate: List<ProfileQuery>,
        profileQueryIdsToDelete: List<Long>,
    ): Boolean

    suspend fun getEmailForBroker(dataBroker: String): String

    suspend fun getEmailConfirmationLinkStatus(emailData: List<EmailData>): Map<EmailData, EmailConfirmationLinkFetchStatus>

    suspend fun deleteEmailData(emailData: List<EmailData>)

    data class BrokerJson(
        val fileName: String,
        val etag: String,
    )

    sealed class EmailConfirmationLinkFetchStatus(val statusString: String) {

        data class Ready(
            /**
             * This represents the data we receive from the backend where the key could be "link" or "verification_code"
             * And the value is the actual link or code.
             */
            val data: Map<String, String>,
            val emailReceivedAtMs: Long,
        ) : EmailConfirmationLinkFetchStatus(STATUS_READY)

        data object Pending : EmailConfirmationLinkFetchStatus(STATUS_PENDING)

        data class Unknown(
            val errorCode: String = "unknown_error",
        ) : EmailConfirmationLinkFetchStatus(STATUS_UNKNOWN)

        data class Error(
            val errorCode: String,
            val error: String,
        ) : EmailConfirmationLinkFetchStatus(STATUS_ERROR)

        companion object {
            const val STATUS_READY = "ready"
            const val STATUS_PENDING = "pending"
            const val STATUS_UNKNOWN = "unknown"
            const val STATUS_ERROR = "error"

            fun fromString(
                status: String,
                emailReceivedAtMs: Long = 0L,
                data: Map<String, String>? = null,
                errorCode: String? = null,
                error: String? = null,
            ): EmailConfirmationLinkFetchStatus = when (status.lowercase()) {
                STATUS_READY -> Ready(
                    data = data ?: emptyMap(),
                    emailReceivedAtMs = emailReceivedAtMs,
                )

                STATUS_PENDING -> Pending
                STATUS_UNKNOWN -> if (errorCode != null) Unknown(errorCode) else Unknown()
                STATUS_ERROR -> Error(errorCode ?: "unknown_error", error ?: "Unknown error")
                else -> Unknown()
            }
        }
    }
}

class RealPirRepository(
    private val dispatcherProvider: DispatcherProvider,
    private val pirDataStore: PirDataStore,
    private val currentTimeProvider: CurrentTimeProvider,
    private val databaseFactory: PirSecureStorageDatabaseFactory,
    private val dbpService: DbpService,
    private val pixelSender: PirPixelSender,
    appCoroutineScope: CoroutineScope,
) : PirRepository {

    private val database: Deferred<PirDatabase?> = appCoroutineScope.async(start = CoroutineStart.LAZY) {
        prepareDatabase()
    }

    private val addressCityStateAdapter by lazy { Moshi.Builder().build().adapter(AddressCityState::class.java) }

    override suspend fun isRepositoryAvailable(): Boolean = database.await() != null

    override suspend fun getCurrentMainEtag(): String? = pirDataStore.mainConfigEtag

    override suspend fun updateMainEtag(etag: String?) {
        pirDataStore.mainConfigEtag = etag
    }

    override suspend fun updateBrokerJsons(brokers: List<BrokerJson>) {
        withContext(dispatcherProvider.io()) {
            brokers
                .map {
                    BrokerJsonEtag(
                        fileName = it.fileName,
                        etag = it.etag,
                    )
                }.also {
                    brokerJsonDao()?.insertBrokerJsonEtags(it)
                }
        }
    }

    override suspend fun getAllLocalBrokerJsons(): List<BrokerJson> =
        withContext(dispatcherProvider.io()) {
            return@withContext brokerJsonDao()?.getAllBrokers()?.map {
                BrokerJson(
                    fileName = it.fileName,
                    etag = it.etag,
                )
            }.orEmpty()
        }

    override suspend fun getStoredBrokersCount(): Int =
        withContext(dispatcherProvider.io()) {
            return@withContext brokerJsonDao()?.getAllBrokersCount() ?: 0
        }

    override suspend fun getAllActiveBrokers(): List<String> =
        withContext(dispatcherProvider.io()) {
            return@withContext brokerDao()?.getAllActiveBrokers()?.map {
                it.name
            }.orEmpty()
        }

    override suspend fun getAllActiveBrokerObjects(): List<Broker> =
        withContext(dispatcherProvider.io()) {
            return@withContext brokerDao()?.getAllActiveBrokers()?.map {
                Broker(
                    name = it.name,
                    fileName = it.fileName,
                    url = it.url,
                    version = it.version,
                    parent = it.parent,
                    addedDatetime = it.addedDatetime,
                    removedAt = it.removedAt,
                )
            }.orEmpty()
        }

    override suspend fun getBrokerForName(name: String): Broker? =
        withContext(dispatcherProvider.io()) {
            return@withContext brokerDao()?.getBrokerDetails(name)?.let {
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

    override suspend fun getAllMirrorSitesForBroker(brokerName: String): List<MirrorSite> =
        brokerDao()?.getAllMirrorSitesForBroker(brokerName)?.map {
            MirrorSite(
                name = it.name,
                url = it.url,
                addedAt = it.addedAt,
                removedAt = it.removedAt,
                optOutUrl = it.optOutUrl,
                parentSite = it.parentSite,
            )
        }.orEmpty()

    override suspend fun getAllMirrorSites(): List<MirrorSite> =
        brokerDao()?.getAllMirrorSites()?.map {
            MirrorSite(
                name = it.name,
                url = it.url,
                addedAt = it.addedAt,
                removedAt = it.removedAt,
                optOutUrl = it.optOutUrl,
                parentSite = it.parentSite,
            )
        }.orEmpty()

    override suspend fun getAllBrokersForScan(): List<String> =
        withContext(dispatcherProvider.io()) {
            return@withContext brokerDao()?.getAllBrokersNamesWithScanSteps().orEmpty()
        }

    override suspend fun getAllBrokerOptOutUrls(): Map<String, String?> =
        withContext(dispatcherProvider.io()) {
            val brokerOptOuts = brokerDao()?.getAllBrokerOptOuts()
            return@withContext brokerOptOuts?.associate { it.brokerName to it.optOutUrl }.orEmpty()
        }

    override suspend fun getEtagForFilename(fileName: String): String =
        withContext(dispatcherProvider.io()) {
            return@withContext brokerJsonDao()?.getEtag(fileName).orEmpty()
        }

    override suspend fun updateBrokerData(
        fileName: String,
        broker: PirJsonBroker,
    ) {
        withContext(dispatcherProvider.io()) {
            brokerDao()?.upsert(
                broker =
                BrokerEntity(
                    name = broker.name,
                    fileName = fileName,
                    url = broker.url,
                    version = broker.version,
                    parent = broker.parent,
                    addedDatetime = broker.addedDatetime,
                    removedAt = broker.removedAt ?: 0L,
                ),
                brokerScan =
                BrokerScan(
                    brokerName = broker.name,
                    stepsJson = broker.steps.first { it.contains("\"stepType\":\"scan\"") },
                ),
                brokerOptOut =
                BrokerOptOut(
                    brokerName = broker.name,
                    stepsJson = broker.steps.first { it.contains("\"stepType\":\"optOut\"") },
                    optOutUrl = broker.optOutUrl,
                ),
                schedulingConfig =
                BrokerSchedulingConfigEntity(
                    brokerName = broker.name,
                    retryError = broker.schedulingConfig.retryError,
                    confirmOptOutScan = broker.schedulingConfig.confirmOptOutScan,
                    maintenanceScan = broker.schedulingConfig.maintenanceScan,
                    maxAttempts = broker.schedulingConfig.maxAttempts,
                ),
                mirrorSiteEntity =
                broker.mirrorSites.map {
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
            return@withContext brokerDao()?.getSchedulingConfig(brokerName)?.run {
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
            return@withContext brokerDao()?.getAllSchedulingConfigs()?.map {
                BrokerSchedulingConfig(
                    brokerName = it.brokerName,
                    retryErrorInMillis = TimeUnit.HOURS.toMillis(it.retryError.toLong()),
                    confirmOptOutScanInMillis = TimeUnit.HOURS.toMillis(it.confirmOptOutScan.toLong()),
                    maintenanceScanInMillis = TimeUnit.HOURS.toMillis(it.maintenanceScan.toLong()),
                    maxAttempts = it.maxAttempts ?: -1,
                )
            }.orEmpty()
        }

    override suspend fun getBrokerScanSteps(name: String): String? =
        withContext(dispatcherProvider.io()) {
            brokerDao()?.getScanJson(name)
        }

    override suspend fun getBrokerOptOutSteps(name: String): String? =
        withContext(dispatcherProvider.io()) {
            brokerDao()?.getOptOutJson(name)
        }

    override suspend fun getBrokersForOptOut(formOptOutOnly: Boolean): List<String> =
        withContext(dispatcherProvider.io()) {
            extractedProfileDao()
                ?.getAllExtractedProfiles()
                ?.map {
                    it.brokerName
                }?.distinct()
                ?.run {
                    if (formOptOutOnly) {
                        this.filter {
                            brokerDao()
                                ?.getOptOutJson(it)
                                ?.contains("\"optOutType\":\"formOptOut\"") == true
                        }
                    } else {
                        this
                    }
                }.orEmpty()
        }

    override suspend fun saveNewExtractedProfiles(extractedProfiles: List<ExtractedProfile>) {
        withContext(dispatcherProvider.io()) {
            if (extractedProfiles.isEmpty()) {
                return@withContext
            }

            val profileQueryId = extractedProfiles.first().profileQueryId
            val profileQuery = userProfileDao()?.getUserProfile(profileQueryId)
            if (profileQuery?.deprecated == true) {
                // we should not store any new extracted profiles for a deprecated user profile
                // also don't mark them as deprecated as we still want to show them on the UI
                return@withContext
            }

            extractedProfiles
                .map {
                    it.toStoredExtractedProfile()
                }.also {
                    extractedProfileDao()?.insertNewExtractedProfiles(it)
                }
        }
    }

    override suspend fun getExtractedProfiles(
        brokerName: String,
        profileQueryId: Long,
    ): List<ExtractedProfile> =
        withContext(dispatcherProvider.io()) {
            return@withContext extractedProfileDao()
                ?.getExtractedProfilesForBrokerAndProfile(
                    brokerName,
                    profileQueryId,
                )?.map {
                    it.toExtractedProfile()
                }.orEmpty()
        }

    override suspend fun getExtractedProfile(
        extractedProfileId: Long,
    ): ExtractedProfile? =
        withContext(dispatcherProvider.io()) {
            return@withContext extractedProfileDao()?.getExtractedProfile(extractedProfileId)?.toExtractedProfile()
        }

    override suspend fun getAllExtractedProfilesFlow(): Flow<List<ExtractedProfile>> =
        extractedProfileDao()?.getAllExtractedProfileFlow()?.map { list ->
            list.map {
                it.toExtractedProfile()
            }
        } ?: flowOf(emptyList())

    override suspend fun getAllExtractedProfiles(): List<ExtractedProfile> =
        withContext(dispatcherProvider.io()) {
            return@withContext extractedProfileDao()?.getAllExtractedProfiles()?.map {
                it.toExtractedProfile()
            }.orEmpty()
        }

    override suspend fun getUserProfileQuery(id: Long): ProfileQuery? =
        withContext(dispatcherProvider.io()) {
            userProfileDao()?.getUserProfile(id)?.toProfileQuery()
        }

    override suspend fun getAllUserProfileQueries(): List<ProfileQuery> =
        withContext(dispatcherProvider.io()) {
            userProfileDao()?.getAllUserProfiles()?.map {
                it.toProfileQuery()
            }.orEmpty()
        }

    override suspend fun getValidUserProfileQueries(): List<ProfileQuery> =
        withContext(dispatcherProvider.io()) {
            userProfileDao()?.getValidUserProfiles()?.map {
                it.toProfileQuery()
            }.orEmpty()
        }

    override suspend fun getUserProfileQueriesWithIds(ids: List<Long>): List<ProfileQuery> =
        withContext(dispatcherProvider.io()) {
            userProfileDao()?.getUserProfilesWithIds(ids)?.map {
                it.toProfileQuery()
            }.orEmpty()
        }

    override suspend fun deleteAllUserProfilesQueries() {
        withContext(dispatcherProvider.io()) {
            userProfileDao()?.deleteAllProfiles()
            extractedProfileDao()?.deleteAllExtractedProfiles()
        }
    }

    private fun UserProfile.toProfileQuery(): ProfileQuery =
        ProfileQuery(
            id = this.id,
            firstName = this.userName.firstName,
            lastName = this.userName.lastName,
            city = this.addresses.city,
            state = this.addresses.state,
            addresses =
            listOf(
                Address(
                    city = this.addresses.city,
                    state = this.addresses.state,
                ),
            ),
            birthYear = this.birthYear,
            fullName =
            this.userName.middleName?.let { middleName ->
                "${this.userName.firstName} $middleName ${this.userName.lastName}"
            } ?: "${this.userName.firstName} ${this.userName.lastName}",
            age = currentTimeProvider.localDateTimeNow().year - this.birthYear,
            deprecated = this.deprecated,
        )

    override suspend fun replaceUserProfile(profileQuery: ProfileQuery) {
        withContext(dispatcherProvider.io()) {
            userProfileDao()?.deleteAllProfiles()
            userProfileDao()?.insertUserProfile(profileQuery.toUserProfile())
        }
    }

    override suspend fun updateProfileQueries(
        profileQueriesToAdd: List<ProfileQuery>,
        profileQueriesToUpdate: List<ProfileQuery>,
        profileQueryIdsToDelete: List<Long>,
    ): Boolean = withContext(dispatcherProvider.io()) {
        try {
            userProfileDao()?.updateUserProfiles(
                profilesToAdd = profileQueriesToAdd.map { query -> query.toUserProfile() },
                profilesToUpdate = profileQueriesToUpdate.map { query -> query.toUserProfile() },
                profileIdsToDelete = profileQueryIdsToDelete,
            )
            true
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun getEmailForBroker(dataBroker: String): String =
        withContext(dispatcherProvider.io()) {
            return@withContext dbpService.getEmail(brokerDao()?.getBrokerDetails(dataBroker)!!.url).emailAddress
        }

    override suspend fun getEmailConfirmationLinkStatus(emailData: List<EmailData>): Map<EmailData, EmailConfirmationLinkFetchStatus> =
        withContext(dispatcherProvider.io()) {
            val batchedEmailData = emailData.chunked(EMAIL_DATA_BATCH_SIZE)
            logcat { "PIR-EMAIL-CONFIRMATION: total size to fetch: ${emailData.size}" }
            return@withContext batchedEmailData.map { emailDataSubList ->
                logcat { "PIR-EMAIL-CONFIRMATION: batch size to fetch: ${emailDataSubList.size}" }
                async {
                    // For more information https://github.com/duckduckgo/Android/pull/6847#discussion_r2395023539
                    dbpService.getEmailConfirmationLinkStatus(emailDataSubList.toRequest()).items.associate { response ->
                        val key =
                            EmailData(
                                email = response.email,
                                attemptId = response.attemptId,
                            )
                        val value = EmailConfirmationLinkFetchStatus.fromString(
                            status = response.status,
                            emailReceivedAtMs = TimeUnit.SECONDS.toMillis(response.emailReceivedAt),
                            data = response.data.associate {
                                it.name to it.value
                            },
                            errorCode = response.errorCode,
                            error = response.error,
                        )
                        key to value
                    }
                }
            }.awaitAll().reduce { acc, map -> acc + map }
        }

    override suspend fun deleteEmailData(emailData: List<EmailData>) =
        withContext(dispatcherProvider.io()) {
            logcat { "PIR-EMAIL-CONFIRMATION: total size to delete: ${emailData.size}" }
            emailData
                .chunked(EMAIL_DATA_BATCH_SIZE)
                .forEach { batch ->
                    logcat { "PIR-EMAIL-CONFIRMATION: batch size to delete: ${batch.size}" }
                    dbpService.deleteEmailData(batch.toRequest())
                }
            return@withContext
        }

    private fun List<EmailData>.toRequest(): PirEmailConfirmationDataRequest =
        PirEmailConfirmationDataRequest(
            items =
            this.map {
                PirEmailConfirmationDataRequest.RequestEmailData(
                    email = it.email,
                    attemptId = it.attemptId,
                )
            },
        )

    private fun StoredExtractedProfile.toExtractedProfile(): ExtractedProfile =
        ExtractedProfile(
            dbId = this.id,
            profileUrl = this.profileUrl,
            profileQueryId = this.profileQueryId,
            brokerName = this.brokerName,
            name = this.name,
            alternativeNames = this.alternativeNames,
            age = this.age,
            addresses =
            this.addresses.mapNotNull {
                addressCityStateAdapter.fromJson(it)
            },
            phoneNumbers = this.phoneNumbers,
            relatives = this.relatives,
            identifier = this.identifier,
            reportId = this.reportId,
            email = this.email,
            fullName = this.fullName,
            dateAddedInMillis = this.dateAddedInMillis,
            deprecated = this.deprecated,
        )

    private fun ExtractedProfile.toStoredExtractedProfile(): StoredExtractedProfile =
        StoredExtractedProfile(
            id = this.dbId,
            profileQueryId = this.profileQueryId,
            brokerName = this.brokerName,
            name = this.name,
            alternativeNames = this.alternativeNames,
            age = this.age,
            addresses =
            this.addresses.mapNotNull {
                addressCityStateAdapter.toJson(it)
            },
            phoneNumbers = this.phoneNumbers,
            relatives = this.relatives,
            reportId = this.reportId,
            email = this.email,
            fullName = this.fullName,
            profileUrl = this.profileUrl,
            identifier = this.identifier,
            dateAddedInMillis =
            if (this.dateAddedInMillis == 0L) {
                currentTimeProvider.currentTimeMillis()
            } else {
                this.dateAddedInMillis
            },
            deprecated = this.deprecated,
        )

    private fun ProfileQuery.toUserProfile(): UserProfile =
        UserProfile(
            id = this.id,
            userName =
            UserName(
                firstName = this.firstName,
                lastName = this.lastName,
                middleName = this.middleName,
            ),
            addresses =
            com.duckduckgo.pir.impl.store.db.Address(
                city = this.city,
                state = this.state,
            ),
            birthYear = this.birthYear,
            deprecated = this.deprecated,
        )

    private suspend fun prepareDatabase(): PirDatabase? {
        val database = databaseFactory.getDatabase()
        return if (database != null && database.databaseContentsAreReadable()) {
            database
        } else {
            pixelSender.reportSecureStorageUnavailable()
            logcat(ERROR) { "PIR-DB: PIR database is not readable" }
            null
        }
    }

    private fun PirDatabase.databaseContentsAreReadable(): Boolean {
        return kotlin.runCatching {
            // Try to read from the database to verify it's accessible
            brokerJsonDao().getAllBrokersCount()
            true
        }.getOrElse {
            logcat(ERROR) { "PIR-DB: Error reading from PIR database: ${it.message}" }
            false
        }
    }

    private suspend fun brokerJsonDao(): BrokerJsonDao? = database.await()?.brokerJsonDao()

    private suspend fun brokerDao(): BrokerDao? = database.await()?.brokerDao()

    private suspend fun extractedProfileDao(): ExtractedProfileDao? = database.await()?.extractedProfileDao()

    private suspend fun userProfileDao(): UserProfileDao? = database.await()?.userProfileDao()

    companion object {
        private const val EMAIL_DATA_BATCH_SIZE = 100
    }
}
