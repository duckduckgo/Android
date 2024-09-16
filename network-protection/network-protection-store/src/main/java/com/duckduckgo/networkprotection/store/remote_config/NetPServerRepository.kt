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

package com.duckduckgo.networkprotection.store.remote_config

class NetPServerRepository constructor(
    private val serversDao: NetPServersDao,
) {
    suspend fun getServerNames(): Set<String> {
        return serversDao.getServers().map { it.name }.toSet()
    }

    suspend fun getSelectedServer(): NetPEgressServer? {
        return serversDao.getSelectedServer()?.egressServer
    }

    suspend fun setSelectedServer(name: String?) {
        if (name.isNullOrBlank()) {
            serversDao.clearSelectedServer()
        } else {
            serversDao.selectServer(name)
        }
    }

    suspend fun storeServers(servers: List<NetPEgressServer>) {
        serversDao.clearAll()
        serversDao.insertAll(servers)
        serversDao.getSelectedServer()?.egressServerName?.name?.let { selectedServer ->
            // clear selected server if not in the list anymore
            servers.map { it.name }.firstOrNull { it == selectedServer } ?: serversDao.clearSelectedServer()
        }
    }
}
