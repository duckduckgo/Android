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

package com.duckduckgo.remote.messaging.api

import kotlinx.coroutines.flow.Flow

interface RemoteMessagingRepository {
    fun activeMessage(message: RemoteMessage?)
    fun messageFlow(): Flow<RemoteMessage?>
    suspend fun dismissMessage(id: String)
    fun dismissedMessages(): List<String>
    fun didShow(id: String): Boolean
    fun markAsShown(remoteMessage: RemoteMessage)
}
