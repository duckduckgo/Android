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

/**
 * This table is used to log significant events in the email confirmation process.
 * It helps in monitoring, debugging, and analyzing the email confirmation workflow.
 * DO not use for any business logic
 */
@Entity(tableName = "pir_email_confirmation_log")
data class PirEmailConfirmationLog(
    @PrimaryKey val eventTimeInMillis: Long,
    val eventType: EmailConfirmationEventType,
    /**
     * This can be used to store additional information about the event, such as:
     * - [EmailConfirmationEventType.LINK_FETCH_ATTEMPT]Total of links attempted in the fetch event
     * - [EmailConfirmationEventType.LINK_FETCH_READY]Total fetch links that resulted to ready
     * - [EmailConfirmationEventType.LINK_FETCH_ERROR]Total fetch links that resulted to error
     * - [EmailConfirmationEventType.EMAIL_CONFIRMATION_MAXED_OUT]
     *      Total email confirmation jobs that were maxed out in the run
     * - [EmailConfirmationEventType.EMAIL_CONFIRMATION_ATTEMPT]
     *      Total number of email confirmation attempts in the run
     * - [EmailConfirmationEventType.EMAIL_CONFIRMATION_SUCCESS]
     *      Name of the broker for which the email confirmation was successfully completed.
     * - [EmailConfirmationEventType.EMAIL_CONFIRMATION_FAILED]
     *      Name of the broker for which the email confirmation was failed.
     */
    val value: String,
)

enum class EmailConfirmationEventType {
    LINK_FETCH_ATTEMPT,
    LINK_FETCH_READY,
    LINK_FETCH_ERROR,
    EMAIL_CONFIRMATION_MAXED_OUT,
    EMAIL_CONFIRMATION_ATTEMPT,
    EMAIL_CONFIRMATION_SUCCESS,
    EMAIL_CONFIRMATION_FAILED,
}
