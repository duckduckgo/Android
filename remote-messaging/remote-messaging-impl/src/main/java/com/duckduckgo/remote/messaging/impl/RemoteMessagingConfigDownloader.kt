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

import com.duckduckgo.remote.messaging.impl.network.RemoteMessagingService
import logcat.LogPriority.ERROR
import logcat.LogPriority.VERBOSE
import logcat.logcat

interface RemoteMessagingConfigDownloader {
    suspend fun download(): Boolean
}

class RealRemoteMessagingConfigDownloader constructor(
    private val remoteConfig: RemoteMessagingService,
    private val remoteMessagingConfigProcessor: RemoteMessagingConfigProcessor,
) : RemoteMessagingConfigDownloader {
    override suspend fun download(): Boolean {
        val response = kotlin.runCatching {
            logcat(VERBOSE) { "RMF: downloading config" }
            remoteConfig.config()
        }.onSuccess {
            remoteMessagingConfigProcessor.process(it)
        }.onFailure {
            logcat(ERROR) { "RMF: error at RealRemoteMessagingConfigDownloader, ${it.localizedMessage}" }
        }

        return response.isSuccess
    }
}
