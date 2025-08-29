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

package com.duckduckgo.pir.impl.scheduling

import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pir.impl.models.AddressCityState
import com.duckduckgo.pir.impl.models.ExtractedProfile
import com.duckduckgo.pir.impl.models.ProfileQuery
import com.duckduckgo.pir.impl.models.scheduling.JobRecord
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.OptOutJobRecord
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.OptOutJobRecord.OptOutJobStatus
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.ScanJobRecord
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.ScanJobRecord.ScanJobStatus
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.ScanJobRecord.ScanJobStatus.ERROR
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.ScanJobRecord.ScanJobStatus.MATCHES_FOUND
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.ScanJobRecord.ScanJobStatus.NO_MATCH_FOUND
import com.duckduckgo.pir.impl.store.PirRepository
import com.duckduckgo.pir.impl.store.PirSchedulingRepository
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlinx.coroutines.withContext
import logcat.logcat

/**
 * This class contains logic for updating any existing [JobRecord]
 */
interface JobRecordUpdater {
    /**
     * Updates the [ScanJobRecord] associated with the given [brokerName] and [profileQueryId].
     *
     * This method should be called when [ExtractedProfile] instances are found for a specific
     * broker and profile query combination. It updates the corresponding [ScanJobRecord]'s
     * status and also sets the latest scan time.
     *
     * @param brokerName The name of the broker associated with the scan job.
     * @param profileQueryId The ID of the [ProfileQuery] related to the scan job.
     */
    suspend fun updateScanMatchesFound(
        brokerName: String,
        profileQueryId: Long,
    )

    /**
     * Updates the [ScanJobRecord] associated with the given [brokerName] and [profileQueryId].
     *
     * This method should be called when [ExtractedProfile] instances are NOT found for a specific
     * broker and profile query combination. It updates the corresponding [ScanJobRecord]'s
     * status and also sets the latest scan time.
     *
     * @param brokerName The name of the broker associated with the scan job.
     * @param profileQueryId The ID of the [ProfileQuery] related to the scan job.
     */
    suspend fun updateScanNoMatchFound(
        brokerName: String,
        profileQueryId: Long,
    )

    /**
     * Updates the [ScanJobRecord] associated with the given [brokerName] and [profileQueryId].
     *
     * This method should be called when the scan attempt has failed to complete due to an error.
     * It updates the corresponding [ScanJobRecord]'s status and also sets the latest scan time.
     *
     * @param brokerName The name of the broker associated with the scan job.
     * @param profileQueryId The ID of the [ProfileQuery] related to the scan job.
     */
    suspend fun updateScanError(
        brokerName: String,
        profileQueryId: Long,
    )

    /**
     * This method compares the [newExtractedProfiles] from the ones currently stored locally and
     * associated to [brokerName] and [profileQueryId]. For every stored [ExtractedProfile] that is
     * not part of the [newExtractedProfiles], we mark the status of the associated [OptOutJobRecord]
     * to requested.
     *
     * This method should be called before we store [newExtractedProfiles] locally.
     *
     * @param newExtractedProfiles Newly [ExtractedProfile]s for the [brokerName] and [profileQueryId]
     * @param brokerName The name of the broker associated with the scan job.
     * @param profileQueryId The ID of the [ProfileQuery] related to the scan job.
     */
    suspend fun markRemovedOptOutJobRecords(
        newExtractedProfiles: List<ExtractedProfile>,
        brokerName: String,
        profileQueryId: Long,
    )

    /**
     * Updates the [OptOutJobRecord] associated with a given [extractedProfileId].
     *
     * This method should be called when the opt-out attempt has been STARTED.
     * It increments the [OptOutJobRecord.attemptCount] and also updates the
     * [OptOutJobRecord.lastOptOutAttemptDateInMillis].
     *
     * @param extractedProfileId The id stored in our database for the [ExtractedProfile]
     */
    suspend fun markOptOutAsAttempted(
        extractedProfileId: Long,
    )

    /**
     * Updates the [OptOutJobRecord] associated with a given [extractedProfileId].
     *
     * This method should be called when the opt-out attempt has been successfully completed.
     * It updates the corresponding all necessary attributes to mark the [OptOutJobRecord] as
     * requested.
     *
     * @param extractedProfileId The id stored in our database for the [ExtractedProfile]
     */
    suspend fun updateOptOutRequested(
        extractedProfileId: Long,
    )

    /**
     * Updates the [OptOutJobRecord] associated with a given [extractedProfileId].
     *
     * This method should be called when the opt-out attempt has failed.
     * It updates the corresponding all necessary attributes to mark the [OptOutJobRecord] as
     * a failure.
     *
     * @param extractedProfileId The id stored in our database for the [ExtractedProfile]
     */
    suspend fun updateOptOutError(
        extractedProfileId: Long,
    )
}

@ContributesBinding(AppScope::class)
class RealJobRecordUpdater @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    private val currentTimeProvider: CurrentTimeProvider,
    private val schedulingRepository: PirSchedulingRepository,
    private val repository: PirRepository,
) : JobRecordUpdater {
    override suspend fun updateScanNoMatchFound(
        brokerName: String,
        profileQueryId: Long,
    ) {
        updateScanJobRecord(brokerName, profileQueryId, NO_MATCH_FOUND)
    }

    override suspend fun updateScanMatchesFound(
        brokerName: String,
        profileQueryId: Long,
    ) {
        updateScanJobRecord(brokerName, profileQueryId, MATCHES_FOUND)
    }

    override suspend fun updateScanError(
        brokerName: String,
        profileQueryId: Long,
    ) {
        updateScanJobRecord(brokerName, profileQueryId, ERROR)
    }

    private suspend fun updateScanJobRecord(
        brokerName: String,
        profileQueryId: Long,
        status: ScanJobStatus,
    ) {
        logcat { "PIR-JOB-RECORD: Updating ScanJobRecord for $brokerName and $profileQueryId to $status" }
        schedulingRepository.updateScanJobRecordStatus(
            newStatus = status,
            newLastScanDateMillis = currentTimeProvider.currentTimeMillis(),
            brokerName = brokerName,
            profileQueryId = profileQueryId,
        )
    }

    override suspend fun markOptOutAsAttempted(extractedProfileId: Long) {
        withContext(dispatcherProvider.io()) {
            schedulingRepository.getValidOptOutJobRecord(extractedProfileId)?.also {
                schedulingRepository.saveOptOutJobRecord(
                    it.copy(
                        attemptCount = it.attemptCount + 1,
                        lastOptOutAttemptDateInMillis = currentTimeProvider.currentTimeMillis(),
                    ).also {
                        logcat { "PIR-JOB-RECORD: Updating OptOutRecord for $extractedProfileId to $it" }
                    },
                )
            }
        }
    }

    override suspend fun markRemovedOptOutJobRecords(
        newExtractedProfiles: List<ExtractedProfile>,
        brokerName: String,
        profileQueryId: Long,
    ) {
        withContext(dispatcherProvider.io()) {
            val currentTimeMillis = currentTimeProvider.currentTimeMillis()
            val storedExtractedProfiles =
                repository.getExtractedProfiles(brokerName, profileQueryId)

            if (storedExtractedProfiles.isNotEmpty()) {
                val newKeys = newExtractedProfiles.asSequence()
                    .map { it.toKey() }
                    .toHashSet()

                logcat { "PIR-JOB-RECORD: New Profiles ${newExtractedProfiles.size} : $newExtractedProfiles" }
                logcat { "PIR-JOB-RECORD: Stored Profiles ${storedExtractedProfiles.size} : $storedExtractedProfiles" }

                val removedExtractedProfiles = storedExtractedProfiles.asSequence()
                    .filter { it.toKey() !in newKeys }
                    .toList()

                logcat { "PIR-JOB-RECORD: Removed Profiles $removedExtractedProfiles" }

                removedExtractedProfiles.forEach {
                    updateOptOutJobRecordAsRemoved(
                        it.dbId,
                        currentTimeMillis,
                    )
                }
            }
        }
    }

    private suspend fun updateOptOutJobRecordAsRemoved(
        extractedProfileId: Long,
        removedDateInMillis: Long,
    ) {
        withContext(dispatcherProvider.io()) {
            schedulingRepository.getValidOptOutJobRecord(extractedProfileId)?.also {
                schedulingRepository.saveOptOutJobRecord(
                    it.copy(
                        status = OptOutJobStatus.REMOVED,
                        optOutRemovedDateInMillis = removedDateInMillis,
                    ).also {
                        logcat { "PIR-JOB-RECORD: Updating OptOutRecord for $extractedProfileId to $it" }
                    },
                )
            }
        }
    }

    override suspend fun updateOptOutRequested(
        extractedProfileId: Long,
    ) {
        withContext(dispatcherProvider.io()) {
            schedulingRepository.getValidOptOutJobRecord(extractedProfileId)?.also {
                schedulingRepository.saveOptOutJobRecord(
                    it.copy(
                        status = OptOutJobStatus.REQUESTED,
                        optOutRequestedDateInMillis = currentTimeProvider.currentTimeMillis(),
                    ).also {
                        logcat { "PIR-JOB-RECORD: Updating OptOutRecord for $extractedProfileId to $it" }
                    },
                )
            }
        }
    }

    override suspend fun updateOptOutError(
        extractedProfileId: Long,
    ) {
        withContext(dispatcherProvider.io()) {
            schedulingRepository.getValidOptOutJobRecord(extractedProfileId)?.also {
                schedulingRepository.saveOptOutJobRecord(
                    it.copy(
                        status = OptOutJobStatus.ERROR,
                    ).also {
                        logcat { "PIR-JOB-RECORD: Updating OptOutRecord for $extractedProfileId to $it" }
                    },
                )
            }
        }
    }

    private data class ExtractedProfileComparisonKey(
        val profileQueryId: Long,
        val brokerName: String,
        val name: String,
        val alternativeNames: List<String>,
        val age: String,
        val addresses: List<AddressCityState>,
        val phoneNumbers: List<String>,
        val relatives: List<String>,
        val reportId: String,
        val email: String,
        val fullName: String,
        val profileUrl: String,
        val identifier: String,
    )

    private fun ExtractedProfile.toKey(): ExtractedProfileComparisonKey {
        return ExtractedProfileComparisonKey(
            profileQueryId = profileQueryId,
            brokerName = brokerName,
            name = name,
            alternativeNames = alternativeNames,
            age = age,
            addresses = addresses,
            phoneNumbers = phoneNumbers,
            relatives = relatives,
            reportId = reportId,
            email = email,
            fullName = fullName,
            profileUrl = profileUrl,
            identifier = identifier,
        )
    }
}
