/*
 * Copyright (c) 2024 DuckDuckGo
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

interface RemoteMessageModel {

    fun getActiveMessage(): RemoteMessage?

    fun getActiveMessages(): Flow<RemoteMessage?>

    suspend fun onMessageShown(remoteMessage: RemoteMessage)

    suspend fun onMessageDismissed(remoteMessage: RemoteMessage)

    suspend fun onPrimaryActionClicked(remoteMessage: RemoteMessage): Action?

    suspend fun onSecondaryActionClicked(remoteMessage: RemoteMessage): Action?

    fun onActionClicked(remoteMessage: RemoteMessage): Action?
}
