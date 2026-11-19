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

import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.networkprotection.impl.config.NetPDefaultConfigProvider
import com.duckduckgo.networkprotection.impl.config.RealNetPDefaultConfigProvider
import dev.zacsweers.metro.ContributesBinding
import java.net.InetAddress
import javax.inject.Inject

@ContributesBinding(
    scope = VpnScope::class,
    priority = Int.MAX_VALUE,
)
class NetPInternalDefaultConfigProvider @Inject constructor(
    private val realNetPConfigProvider: RealNetPDefaultConfigProvider,
    private val mtuInternalProvider: NetPInternalMtuProvider,
    private val exclusionListProvider: NetPInternalExclusionListProvider,
) : NetPDefaultConfigProvider {

    override fun mtu(): Int {
        return mtuInternalProvider.getMtu()
    }

    override suspend fun exclusionList(): Set<String> {
        return mutableSetOf<String>().apply {
            addAll(realNetPConfigProvider.exclusionList())
            addAll(exclusionListProvider.getExclusionList())
        }.toSet()
    }

    override fun fallbackDns(): Set<InetAddress> {
        return realNetPConfigProvider.fallbackDns()
    }

    override suspend fun routes(): Map<String, Int> {
        return realNetPConfigProvider.routes()
    }
}
