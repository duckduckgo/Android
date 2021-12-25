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

package com.duckduckgo.app.remotemessage.impl

import com.duckduckgo.app.remotemessage.impl.messages.RemoteConfig
import com.duckduckgo.remote.messaging.impl.RemoteMessagingConfigJsonParser
import com.duckduckgo.remote.messaging.store.RemoteMessagingConfig
import com.duckduckgo.remote.messaging.store.RemoteMessagingConfigRepository
import timber.log.Timber

interface RemoteMessagingConfigProcessor {
    suspend fun process(jsonRemoteMessagingConfig: JsonRemoteMessagingConfig): RemoteConfig
}

class RealRemoteMessagingConfigProcessor(
    private val remoteMessagingConfigJsonMapper: RemoteMessagingConfigJsonParser,
    private val remoteMessagingConfigRepository: RemoteMessagingConfigRepository
) : RemoteMessagingConfigProcessor {

    override suspend fun process(jsonRemoteMessagingConfig: JsonRemoteMessagingConfig): RemoteConfig {
        Timber.i("RMF: process ${jsonRemoteMessagingConfig.version}")
        val currentVersion = remoteMessagingConfigRepository.get().version
        val newVersion = jsonRemoteMessagingConfig.version

        // if (currentVersion != newVersion) {
        if (true) {
            val config = remoteMessagingConfigJsonMapper.map(jsonRemoteMessagingConfig)
            remoteMessagingConfigRepository.insert(RemoteMessagingConfig(version = jsonRemoteMessagingConfig.version))
        } else {
            Timber.i("RMF: skip, same version")
        }

        return RemoteConfig(emptyList(), emptyMap())
    }
}
