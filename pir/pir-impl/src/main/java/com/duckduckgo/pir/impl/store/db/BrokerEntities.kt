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
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(tableName = "pir_broker_json_etag")
data class BrokerJsonEtag(
    @PrimaryKey val fileName: String,
    val etag: String,
)

@Entity(tableName = "pir_broker_details")
data class Broker(
    @PrimaryKey val name: String,
    val fileName: String,
    val url: String,
    val version: String,
    val parent: String?,
    val addedDatetime: Long,
    val removedAt: Long,
)

@Entity(
    tableName = "pir_broker_scan",
    foreignKeys = [
        ForeignKey(
            entity = Broker::class,
            parentColumns = ["name"],
            childColumns = ["brokerName"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class BrokerScan(
    @PrimaryKey val brokerName: String,
    val stepsJson: String?,
)

@Entity(
    tableName = "pir_broker_opt_out",
    foreignKeys = [
        ForeignKey(
            entity = Broker::class,
            parentColumns = ["name"],
            childColumns = ["brokerName"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class BrokerOptOut(
    @PrimaryKey val brokerName: String,
    val stepsJson: String,
    val optOutUrl: String?,
)

@Entity(
    tableName = "pir_broker_scheduling_config",
    foreignKeys = [
        ForeignKey(
            entity = Broker::class,
            parentColumns = ["name"],
            childColumns = ["brokerName"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class BrokerSchedulingConfigEntity(
    @PrimaryKey val brokerName: String,
    val retryError: Int,
    val confirmOptOutScan: Int,
    val maintenanceScan: Int,
    val maxAttempts: Int?,
)
