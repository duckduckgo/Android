/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.networkprotection.internal.network

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.networkprotection.impl.configuration.Server
import com.duckduckgo.networkprotection.impl.configuration.WgServerDebugProvider
import com.duckduckgo.networkprotection.store.remote_config.NetPEgressServer
import com.duckduckgo.networkprotection.store.remote_config.NetPServerRepository
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

@ContributesBinding(
    scope = AppScope::class,
    rank = ContributesBinding.RANK_HIGHEST, // binding for internal build wins
)
class WgServerInternalProvider @Inject constructor(
    private val netPServerRepository: NetPServerRepository,
) : WgServerDebugProvider {
    override suspend fun getSelectedServerName(): String? {
        return netPServerRepository.getSelectedServer()?.name
    }

    override suspend fun clearSelectedServerName() {
        netPServerRepository.setSelectedServer(null)
    }

    override suspend fun cacheServers(servers: List<Server>) {
        servers.map { server ->
            NetPEgressServer(
                name = server.name,
                publicKey = server.publicKey,
                port = server.port,
                hostnames = server.hostnames,
                ips = server.ips,
                countryCode = server.attributes["country"] as String,
                city = server.attributes["city"] as String,
            )
        }.let {
            netPServerRepository.storeServers(it)
        }
    }
}
