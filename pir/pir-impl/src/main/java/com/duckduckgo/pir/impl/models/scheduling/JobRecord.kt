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

package com.duckduckgo.pir.impl.models.scheduling

/**
 * This class represents a  record for Job that PIR could execute.
 * This will be used to keep track of the current Job's status and other relevant information that could help us determine
 * when a Job should be executed / re-executed again.
 *
 * @property brokerName A unique ID representing the broker linked to this record.
 * @property userProfileId A unique ID representing the ProfileQuery linked to this record.
 */
sealed class JobRecord(
    open val brokerName: String,
    open val userProfileId: Long,
) {
    /**
     * This class represents a job record for opt-out jobs.
     * This contains all information necessary to tell us when the opt-out job should be executed.
     *
     * @property extractedProfileId A unique ID representing the ExtractedProfile linked
     * to this record.
     * @property status current status of the Job
     * @property attemptCount Total number of attempts made to run this job
     * @property lastOptOutAttemptDateInMillis Date (in milliseconds) of the last time the
     * opt-out job has been completed.
     * @property optOutRequestedDateInMillis Date (in milliseconds) when the opt-out request
     * was successfully requested.
     * @property optOutRemovedDateInMillis Date (in milliseconds) when the record was confirmed
     * to be removed from the broker.
     */
    data class OptOutJobRecord(
        override val brokerName: String,
        override val userProfileId: Long,
        val extractedProfileId: Long,
        val status: OptOutJobStatus = OptOutJobStatus.NOT_EXECUTED,
        val attemptCount: Int = 0,
        val lastOptOutAttemptDateInMillis: Long = 0L,
        val optOutRequestedDateInMillis: Long = 0L,
        val optOutRemovedDateInMillis: Long = 0L,
    ) : JobRecord(brokerName, userProfileId) {
        enum class OptOutJobStatus {
            /** Opt-out has not been executed yet and should be executed when possible */
            NOT_EXECUTED,

            /** The opt out job has successfully sent a request for removal. */
            REQUESTED,

            /** The profile has been successfully removed. */
            REMOVED,

            /** The opt out job has failed to send a request for removal. */
            ERROR,

            /** The job is now invalid and should NOT be executed anymore. */
            INVALID,

            /** The job is waiting for email confirmation to complete before we can move it to [REQUESTED]. */
            PENDING_EMAIL_CONFIRMATION,
        }
    }

    /**
     * This class represents a job record for scan jobs.
     * This contains all information necessary to tell us when the scan job should be executed.
     *
     * @property status current status of the Job
     * @property lastScanDateInMillis Date (in milliseconds) of the last time the
     * scan job has been completed.
     */
    data class ScanJobRecord(
        override val brokerName: String,
        override val userProfileId: Long,
        val status: ScanJobStatus = ScanJobStatus.NOT_EXECUTED,
        val lastScanDateInMillis: Long = 0L,
    ) : JobRecord(brokerName, userProfileId) {
        enum class ScanJobStatus {
            /** Scan has not been executed yet and should be executed when possible */
            NOT_EXECUTED,

            /** Scan is completed and no extractedProfiles found */
            NO_MATCH_FOUND,

            /** Scan is completed and extractedProfiles were found */
            MATCHES_FOUND,

            /** Error encountered during the last scan job run */
            ERROR,

            /** The job is now invalid and should NOT be executed anymore. */
            INVALID,
        }
    }

    data class EmailConfirmationJobRecord(
        override val brokerName: String,
        override val userProfileId: Long,
        val extractedProfileId: Long,
        val emailData: EmailData,
        val linkFetchData: LinkFetchData = LinkFetchData(),
        val jobAttemptData: JobAttemptData = JobAttemptData(),
        val dateCreatedInMillis: Long,
        val deprecated: Boolean = false,
    ) : JobRecord(brokerName, userProfileId) {
        data class EmailData(
            val email: String,
            val attemptId: String,
        )

        data class LinkFetchData(
            val emailConfirmationLink: String = "",
            val linkFetchAttemptCount: Int = 0,
            val lastLinkFetchDateInMillis: Long = 0L,
        )

        data class JobAttemptData(
            val jobAttemptCount: Int = 0,
            val lastJobAttemptDateInMillis: Long = 0L,
        )
    }
}
