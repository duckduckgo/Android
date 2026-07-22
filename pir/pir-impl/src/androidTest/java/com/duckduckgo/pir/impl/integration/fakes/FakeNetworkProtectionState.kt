/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.pir.impl.integration.fakes

import com.duckduckgo.networkprotection.api.NetworkProtectionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class FakeNetworkProtectionState : NetworkProtectionState {
    override suspend fun isOnboarded(): Boolean = false
    override suspend fun isEnabled(): Boolean = false
    override suspend fun isRunning(): Boolean = false
    override fun start() {}
    override fun restart() {}
    override fun clearVPNConfigurationAndRestart() {}
    override suspend fun stop() {}
    override fun clearVPNConfigurationAndStop() {}
    override fun serverLocation(): String? = null
    override fun getConnectionStateFlow(): Flow<NetworkProtectionState.ConnectionState> = flowOf()
    override suspend fun getExcludedApps(): List<String> = emptyList()
}
