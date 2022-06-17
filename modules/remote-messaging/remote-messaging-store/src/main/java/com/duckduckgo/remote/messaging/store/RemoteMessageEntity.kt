/*
 * Copyright (c) 2022 DuckDuckGo
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

@Entity(tableName = "remote_message")
data class RemoteMessageEntity(
    @PrimaryKey val id: String,
    val message: String,
    val status: Status,
    val shown: Boolean = false
) {
    enum class Status {
        SCHEDULED,
        DISMISSED,
        DONE
    }
}
