/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.remote.messaging.store

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.threeten.bp.LocalDateTime

@Entity(tableName = "remote_messaging")
data class RemoteMessagingConfig(
    @PrimaryKey
    val id: Int = 1,
    val version: Long,
    val invalidate: Boolean = false,
    val evaluationTimestamp: String = databaseTimestampFormatter().format(LocalDateTime.now())
)

internal fun RemoteMessagingConfig.expired(): Boolean {
    val yesterday = databaseTimestampFormatter().format(LocalDateTime.now().minusDays(1L))
    return this.evaluationTimestamp < yesterday
}

internal fun RemoteMessagingConfig.invalidated(): Boolean {
    return this.invalidate
}
