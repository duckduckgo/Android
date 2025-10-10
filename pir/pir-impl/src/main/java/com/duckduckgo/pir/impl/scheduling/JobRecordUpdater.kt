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
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.EmailConfirmationJobRecord
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.EmailConfirmationJobRecord.EmailData
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
import kotlinx.coroutines.withContext
import logcat.logcat
import javax.inject.Inject

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
     * This method should be called before we store [newExtractedProfiles] locally.
     *
     * @param newExtractedProfiles Newly found [ExtractedProfile]s for the [brokerName] and [profileQueryId]
     * @param brokerName The name of the broker associated with the scan job.
     * @param profileQueryId The ID of the [ProfileQuery] related to the scan job.
     */
    suspend fun updateScanMatchesFound(
        newExtractedProfiles: List<ExtractedProfile>,
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
     * to removed.
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
    suspend fun markOptOutAsAttempted(extractedProfileId: Long)

    /**
     * Updates the [OptOutJobRecord] associated with a given [extractedProfileId].
     *
     * This method should be called when the opt-out attempt has been successfully completed.
     * It updates the corresponding all necessary attributes to mark the [OptOutJobRecord] as
     * requested.
     *
     * @param extractedProfileId The id stored in our database for the [ExtractedProfile]
     */
    suspend fun updateOptOutRequested(extractedProfileId: Long)

    /**
     * Updates the [OptOutJobRecord] associated with a given [extractedProfileId].
     *
     * This method should be called when the opt-out attempt has failed.
     * It updates the corresponding all necessary attributes to mark the [OptOutJobRecord] as
     * a failure.
     *
     * @param extractedProfileId The id stored in our database for the [ExtractedProfile]
     */
    suspend fun updateOptOutError(extractedProfileId: Long)

    /**
     * Updates the [OptOutJobRecord] associated with the given [extractedProfileId].
     *
     * This method should be called when the opt-out attempt requires email confirmation.
     * Ir updates the [OptOutJobStatus] accordingly and also creates a corresponding [EmailConfirmationJobRecord].
     *
     * @param extractedProfileId The id stored in our database for the [ExtractedProfile]
     * @param profileQueryId  The ID of the [ProfileQuery] related to the scan job.
     * @param brokerName The name of the broker associated with the scan job.
     * @param email Email used during the opt-out flow, where the link is expected to be sent.
     * @param attemptId  Locally generated ID to identify this opt out attempt.
     */
    suspend fun markOptOutAsWaitingForEmailConfirmation(
        profileQueryId: Long,
        extractedProfileId: Long,
        brokerName: String,
        email: String,
        attemptId: String,
    ): EmailConfirmationJobRecord

    /**
     * Updates the [EmailConfirmationJobRecord] when the fetch of the email confirmation link has failed.
     * We delete the corresponding [EmailConfirmationJobRecord] and mark the associated [OptOutJobRecord] as ERROR
     *
     * @param extractedProfileId Id of the record to be updated
     */
    suspend fun markEmailConfirmationLinkFetchFailed(extractedProfileId: Long)

    /**
     * Updates the [EmailConfirmationJobRecord] when the fetch of the email confirmation link has been attempted.
     * This should be called before the actual fetch is attempted.
     *
     * @param extractedProfileId Id of the record to be updated
     */
    suspend fun recordEmailConfirmationFetchAttempt(extractedProfileId: Long): EmailConfirmationJobRecord?

    /**
     * Updates the [EmailConfirmationJobRecord] when the email confirmation link has been fetched successfully.
     *
     * @param extractedProfileId Id of the record to be updated
     * @param link The fetched email confirmation link
     */
    suspend fun markEmailConfirmationWithLink(
        extractedProfileId: Long,
        link: String,
    ): EmailConfirmationJobRecord?

    /**
     * Updates the [EmailConfirmationJobRecord] when the succeeding email confirmation steps has been attempted.
     *
     * @param extractedProfileId Id of the record to be updated
     */
    suspend fun recordEmailConfirmationAttempt(extractedProfileId: Long): EmailConfirmationJobRecord?

    /**
     * Updates the [EmailConfirmationJobRecord] when email confirmation attempts have been maxed out.
     * This method deletes the corresponding [EmailConfirmationJobRecord] and marks the associated [OptOutJobRecord]
     * as [ERROR]
     *
     * @param extractedProfileId Id of the record to be updated
     */
    suspend fun recordEmailConfirmationAttemptMaxed(extractedProfileId: Long)

    /**
     * Updates the [EmailConfirmationJobRecord] when email confirmation attempt has been successfully completed.
     * This method deletes the corresponding [EmailConfirmationJobRecord] and marks the associated [OptOutJobRecord]
     * as [OptOutJobStatus.REQUESTED]
     *
     * @param extractedProfileId Id of the record to be updated
     */
    suspend fun recordEmailConfirmationCompleted(extractedProfileId: Long)

    suspend fun recordEmailConfirmationFailed(
        extractedProfileId: Long,
        lastActionId: String,
    ): EmailConfirmationJobRecord?

    /**
     * Removes all [ScanJobRecord], [OptOutJobRecord] and [EmailConfirmationJobRecord] associated with the given [profileQueryId].
     * Any job records that are associated with brokers in [brokersToExclude] will be retained.
     *
     * This method should be called when a [ProfileQuery] is deleted or set to deprecated, to ensure that
     * no stale job records remain in the system or get picked up. However, we still want to retain job records
     * for brokers that have associated [ExtractedProfile] instances, as those profiles still need to be managed.
     *
     * @param profileQueryId The ID of the [ProfileQuery] whose associated job records should be removed.
     * @param brokersToExclude List of broker names for which records should not be deleted. Job records associated with these brokers will be retained.
     */
    suspend fun removeJobRecordsForProfile(
        profileQueryId: Long,
        brokersToExclude: List<String>,
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
        // if the profile query this scan belongs to is deprecated and no matches were found,
        // also mark it as deprecated so it doesn't get picked up again
        val shouldBeMarkedDeprecated = repository.getUserProfileQuery(profileQueryId)?.deprecated == true

        updateScanJobRecord(
            brokerName = brokerName,
            profileQueryId = profileQueryId,
            status = NO_MATCH_FOUND,
            deprecated = shouldBeMarkedDeprecated,
        )
    }

    override suspend fun updateScanMatchesFound(
        newExtractedProfiles: List<ExtractedProfile>,
        brokerName: String,
        profileQueryId: Long,
    ) {
        val profileQuery = repository.getUserProfileQuery(profileQueryId)
        if (profileQuery?.deprecated != true) {
            updateScanJobRecord(brokerName, profileQueryId, MATCHES_FOUND)
            return
        }

        // special handling for deprecated profile queries as scans should only run to confirm that previously found profiles have been removed
        // once that is confirmed, we can mark the scan job as deprecated so it doesn't get picked up again
        val storedExtractedProfiles =
            repository.getExtractedProfiles(brokerName, profileQueryId)

        if (storedExtractedProfiles.isNotEmpty()) {
            val newKeys =
                newExtractedProfiles
                    .asSequence()
                    .map { it.toKey() }
                    .toHashSet()

            val removedExtractedProfiles =
                storedExtractedProfiles
                    .asSequence()
                    .filter { it.toKey() !in newKeys }
                    .toList()

            // if all previously stored extracted profiles have been removed, we can mark the scan job record as deprecated
            // since we do not store new extracted profiles for deprecated profile queries
            val shouldBeMarkedAsDeprecated = removedExtractedProfiles.size == storedExtractedProfiles.size
            updateScanJobRecord(
                brokerName = brokerName,
                profileQueryId = profileQueryId,
                status = MATCHES_FOUND,
                deprecated = shouldBeMarkedAsDeprecated,
            )
        }
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
        deprecated: Boolean = false,
    ) {
        logcat { "PIR-JOB-RECORD: Updating ScanJobRecord for $brokerName and $profileQueryId to $status" }
        schedulingRepository.updateScanJobRecordStatus(
            newStatus = status,
            newLastScanDateMillis = currentTimeProvider.currentTimeMillis(),
            brokerName = brokerName,
            profileQueryId = profileQueryId,
            deprecated = deprecated,
        )
    }

    override suspend fun markOptOutAsAttempted(extractedProfileId: Long) {
        withContext(dispatcherProvider.io()) {
            schedulingRepository.getValidOptOutJobRecord(extractedProfileId)?.also {
                schedulingRepository.saveOptOutJobRecord(
                    it
                        .copy(
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
                val newKeys =
                    newExtractedProfiles
                        .asSequence()
                        .map { it.toKey() }
                        .toHashSet()

                logcat { "PIR-JOB-RECORD: New Profiles ${newExtractedProfiles.size} : $newExtractedProfiles" }
                logcat { "PIR-JOB-RECORD: Stored Profiles ${storedExtractedProfiles.size} : $storedExtractedProfiles" }

                val removedExtractedProfiles =
                    storedExtractedProfiles
                        .asSequence()
                        .filter { it.toKey() !in newKeys }
                        .toList()

                logcat { "PIR-JOB-RECORD: Removed Profiles $removedExtractedProfiles" }

                val profileQuery = repository.getUserProfileQuery(profileQueryId)
                removedExtractedProfiles.forEach { extractedProfile ->
                    updateOptOutJobRecordAsRemoved(
                        profileQuery = profileQuery,
                        extractedProfileId = extractedProfile.dbId,
                        removedDateInMillis = currentTimeMillis,
                    )
                }
            }
        }
    }

    private suspend fun updateOptOutJobRecordAsRemoved(
        profileQuery: ProfileQuery?,
        extractedProfileId: Long,
        removedDateInMillis: Long,
    ) {
        withContext(dispatcherProvider.io()) {
            schedulingRepository.getValidOptOutJobRecord(extractedProfileId)?.also {
                schedulingRepository.saveOptOutJobRecord(
                    it
                        .copy(
                            status = OptOutJobStatus.REMOVED,
                            optOutRemovedDateInMillis = removedDateInMillis,
                            // we've confirmed that the extracted profile for a deprecated profile query has been removed
                            // and need to mark the opt out job record as deprecated to not pick it up again for maintenance scans
                            deprecated = profileQuery?.deprecated == true,
                        ).also {
                            logcat { "PIR-JOB-RECORD: Updating OptOutRecord for $extractedProfileId to $it" }
                        },
                )
            }
        }
    }

    override suspend fun updateOptOutRequested(extractedProfileId: Long) {
        withContext(dispatcherProvider.io()) {
            schedulingRepository.getValidOptOutJobRecord(extractedProfileId)?.also {
                schedulingRepository.saveOptOutJobRecord(
                    it
                        .copy(
                            status = OptOutJobStatus.REQUESTED,
                            optOutRequestedDateInMillis = currentTimeProvider.currentTimeMillis(),
                        ).also {
                            logcat { "PIR-JOB-RECORD: Updating OptOutRecord for $extractedProfileId to $it" }
                        },
                )
            }
        }
    }

    override suspend fun updateOptOutError(extractedProfileId: Long) {
        withContext(dispatcherProvider.io()) {
            schedulingRepository.getValidOptOutJobRecord(extractedProfileId)?.also {
                schedulingRepository.saveOptOutJobRecord(
                    it
                        .copy(
                            status = OptOutJobStatus.ERROR,
                        ).also {
                            logcat { "PIR-JOB-RECORD: Updating OptOutRecord for $extractedProfileId to $it" }
                        },
                )
            }
        }
    }

    override suspend fun markOptOutAsWaitingForEmailConfirmation(
        profileQueryId: Long,
        extractedProfileId: Long,
        brokerName: String,
        email: String,
        attemptId: String,
    ): EmailConfirmationJobRecord = withContext(dispatcherProvider.io()) {
        val newRecord = EmailConfirmationJobRecord(
            userProfileId = profileQueryId,
            extractedProfileId = extractedProfileId,
            brokerName = brokerName,
            emailData =
            EmailData(
                email = email,
                attemptId = attemptId,
            ),
        )
        schedulingRepository.saveEmailConfirmationJobRecord(newRecord)

        schedulingRepository.getValidOptOutJobRecord(extractedProfileId)?.also {
            schedulingRepository.saveOptOutJobRecord(
                it
                    .copy(
                        status = OptOutJobStatus.PENDING_EMAIL_CONFIRMATION,
                    ).also {
                        logcat { "PIR-JOB-RECORD: Updating OptOutRecord for $extractedProfileId to $it" }
                    },
            )
        }
        return@withContext newRecord
    }

    override suspend fun removeJobRecordsForProfile(
        profileQueryId: Long,
        brokersToExclude: List<String>,
    ) {
        withContext(dispatcherProvider.io()) {
            schedulingRepository.deleteJobRecordsForProfile(profileQueryId, brokersToExclude)
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

    private fun ExtractedProfile.toKey(): ExtractedProfileComparisonKey =
        ExtractedProfileComparisonKey(
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

    override suspend fun markEmailConfirmationLinkFetchFailed(extractedProfileId: Long) {
        schedulingRepository.deleteEmailConfirmationJobRecord(extractedProfileId)
        updateOptOutError(extractedProfileId)
    }

    override suspend fun recordEmailConfirmationFetchAttempt(extractedProfileId: Long): EmailConfirmationJobRecord? {
        val currentRecord = schedulingRepository.getEmailConfirmationJob(extractedProfileId) ?: return null
        val newRecord = currentRecord.copy(
            linkFetchData =
            currentRecord.linkFetchData.copy(
                linkFetchAttemptCount = currentRecord.linkFetchData.linkFetchAttemptCount + 1,
                lastLinkFetchDateInMillis = currentTimeProvider.currentTimeMillis(),
            ),
        )

        schedulingRepository.saveEmailConfirmationJobRecord(newRecord)
        logcat { "PIR-JOB-RECORD: Updating EmailConfirmation for $currentRecord to $newRecord" }
        return newRecord
    }

    override suspend fun markEmailConfirmationWithLink(
        extractedProfileId: Long,
        link: String,
    ): EmailConfirmationJobRecord? {
        val currentRecord = schedulingRepository.getEmailConfirmationJob(extractedProfileId) ?: return null
        val newRecord = currentRecord.copy(
            linkFetchData =
            currentRecord.linkFetchData.copy(
                emailConfirmationLink = link,
            ),
        )
        schedulingRepository.saveEmailConfirmationJobRecord(newRecord)
        logcat { "PIR-JOB-RECORD: Updating EmailConfirmation for $currentRecord to $newRecord" }
        return newRecord
    }

    override suspend fun recordEmailConfirmationAttempt(extractedProfileId: Long): EmailConfirmationJobRecord? {
        val currentRecord = schedulingRepository.getEmailConfirmationJob(extractedProfileId) ?: return null

        val newRecord = currentRecord.copy(
            jobAttemptData = currentRecord.jobAttemptData.copy(
                jobAttemptCount = currentRecord.jobAttemptData.jobAttemptCount + 1,
                lastJobAttemptDateInMillis = currentTimeProvider.currentTimeMillis(),
            ),
        )
        schedulingRepository.saveEmailConfirmationJobRecord(newRecord)
        logcat { "PIR-JOB-RECORD: Updating EmailConfirmation for $currentRecord to $newRecord" }
        return newRecord
    }

    override suspend fun recordEmailConfirmationAttemptMaxed(extractedProfileId: Long) {
        schedulingRepository.deleteEmailConfirmationJobRecord(extractedProfileId)
        updateOptOutError(extractedProfileId)
    }

    override suspend fun recordEmailConfirmationCompleted(extractedProfileId: Long) {
        schedulingRepository.deleteEmailConfirmationJobRecord(extractedProfileId)
        updateOptOutRequested(extractedProfileId)
    }

    override suspend fun recordEmailConfirmationFailed(
        extractedProfileId: Long,
        lastActionId: String,
    ): EmailConfirmationJobRecord? {
        val currentRecord = schedulingRepository.getEmailConfirmationJob(extractedProfileId) ?: return null
        val newRecord = currentRecord.copy(
            jobAttemptData = currentRecord.jobAttemptData.copy(
                lastJobAttemptActionId = lastActionId,
            ),
        )
        schedulingRepository.saveEmailConfirmationJobRecord(newRecord)
        logcat { "PIR-JOB-RECORD: Updating EmailConfirmation for $currentRecord to $newRecord" }
        return newRecord
    }
}
