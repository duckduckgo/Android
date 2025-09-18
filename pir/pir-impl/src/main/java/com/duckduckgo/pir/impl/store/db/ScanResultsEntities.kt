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

/**
 * Contains the sites that have been scanned.
 * Scanned means that the scan flow has been started and completed for the broker.
 */
@Entity(
    tableName = "pir_scan_complete_brokers",
    primaryKeys = ["brokerName", "profileQueryId"],
)
data class ScanCompletedBroker(
    val brokerName: String,
    val profileQueryId: Long,
    val startTimeInMillis: Long,
    val endTimeInMillis: Long,
    val isSuccess: Boolean,
)
