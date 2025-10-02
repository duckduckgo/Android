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

package com.duckduckgo.pir.impl.store.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
    tableName = "pir_scan_job_record",
    primaryKeys = ["brokerName", "userProfileId"],
)
data class ScanJobRecordEntity(
    val brokerName: String,
    val userProfileId: Long,
    val status: String,
    val lastScanDateInMillis: Long? = null,
)

@Entity(tableName = "pir_optout_job_record")
data class OptOutJobRecordEntity(
    @PrimaryKey val extractedProfileId: Long,
    val brokerName: String,
    val userProfileId: Long,
    val status: String,
    val attemptCount: Int = 0,
    val lastOptOutAttemptDate: Long? = null,
    val optOutRequestedDate: Long = 0L,
    val optOutRemovedDate: Long = 0L,
)

@Entity(tableName = "pir_email_confirmation_job_record")
data class EmailConfirmationJobRecordEntity(
    @PrimaryKey val extractedProfileId: Long,
    val brokerName: String,
    val userProfileId: Long,
    val email: String,
    val attemptId: String,
    val dateCreatedInMillis: Long,
    val emailConfirmationLink: String = "",
    val linkFetchAttemptCount: Int = 0,
    val lastLinkFetchDateInMillis: Long = 0L,
    val jobAttemptCount: Int = 0,
    val lastJobAttemptDateInMillis: Long = 0L,
    val deprecated: Boolean = false,
)
