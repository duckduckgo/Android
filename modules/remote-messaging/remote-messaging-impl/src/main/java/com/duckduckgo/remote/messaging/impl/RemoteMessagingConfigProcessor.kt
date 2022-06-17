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

package com.duckduckgo.remote.messaging.impl

import com.duckduckgo.remote.messaging.api.RemoteMessagingRepository
import com.duckduckgo.remote.messaging.impl.mappers.RemoteMessagingConfigJsonMapper
import com.duckduckgo.remote.messaging.impl.models.JsonRemoteMessagingConfig
import com.duckduckgo.remote.messaging.store.RemoteMessagingConfig
import com.duckduckgo.remote.messaging.store.RemoteMessagingConfigRepository
import com.duckduckgo.remote.messaging.store.expired
import com.duckduckgo.remote.messaging.store.invalidated
import timber.log.Timber

interface RemoteMessagingConfigProcessor {
    suspend fun process(jsonRemoteMessagingConfig: JsonRemoteMessagingConfig)
}

class RealRemoteMessagingConfigProcessor(
    private val remoteMessagingConfigJsonMapper: RemoteMessagingConfigJsonMapper,
    private val remoteMessagingConfigRepository: RemoteMessagingConfigRepository,
    private val remoteMessagingRepository: RemoteMessagingRepository,
    private val remoteMessagingConfigMatcher: RemoteMessagingConfigMatcher
) : RemoteMessagingConfigProcessor {

    override suspend fun process(jsonRemoteMessagingConfig: JsonRemoteMessagingConfig) {
        Timber.v("RMF: process ${jsonRemoteMessagingConfig.version}")
        val currentConfig = remoteMessagingConfigRepository.get()
        val currentVersion = currentConfig.version
        val newVersion = jsonRemoteMessagingConfig.version

        val isNewVersion = currentVersion != newVersion
        val shouldProcess = currentConfig.invalidated() || currentConfig.expired()

        if (isNewVersion || shouldProcess) {
            val config = remoteMessagingConfigJsonMapper.map(jsonRemoteMessagingConfig)
            val message = remoteMessagingConfigMatcher.evaluate(config)
            remoteMessagingConfigRepository.insert(RemoteMessagingConfig(version = jsonRemoteMessagingConfig.version))
            remoteMessagingRepository.activeMessage(message)
        } else {
            Timber.v("RMF: skip")
        }
    }
}
