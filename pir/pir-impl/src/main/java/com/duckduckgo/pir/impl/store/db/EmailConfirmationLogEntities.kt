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

@Entity(tableName = "pir_email_confirmation_log")
data class PirEmailConfirmationLog(
    @PrimaryKey val eventTimeInMillis: Long,
    val eventType: EmailConfirmationEventType,
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
