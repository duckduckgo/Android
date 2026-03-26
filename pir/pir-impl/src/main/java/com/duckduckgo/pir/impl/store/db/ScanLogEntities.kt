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

@Entity(tableName = "pir_events_log")
data class PirEventLog(
    @PrimaryKey val eventTimeInMillis: Long,
    val eventType: EventType,
)

enum class EventType {
    MANUAL_SCAN_STARTED,
    MANUAL_SCAN_COMPLETED,
    SCHEDULED_SCAN_SCHEDULED,
    SCHEDULED_SCAN_STARTED,
    SCHEDULED_SCAN_COMPLETED,
    MANUAL_OPTOUT_STARTED,
    MANUAL_OPTOUT_COMPLETED,
    EMAIL_CONFIRMATION_STARTED,
    EMAIL_CONFIRMATION_COMPLETED,
}

@Entity(tableName = "pir_broker_scan_log")
data class PirBrokerScanLog(
    @PrimaryKey val eventTimeInMillis: Long,
    val brokerName: String,
    val eventType: BrokerScanEventType,
)

enum class BrokerScanEventType {
    BROKER_STARTED,
    BROKER_SUCCESS,
    BROKER_ERROR,
}
