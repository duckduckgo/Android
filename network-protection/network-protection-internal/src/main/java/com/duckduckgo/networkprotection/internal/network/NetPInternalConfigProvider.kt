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
import com.duckduckgo.networkprotection.impl.config.NetPConfigProvider
import com.duckduckgo.networkprotection.internal.feature.NetPInternalFeatureToggles
import com.squareup.anvil.annotations.ContributesBinding
import java.net.InetAddress
import javax.inject.Inject

@ContributesBinding(
    scope = VpnScope::class,
    priority = ContributesBinding.Priority.HIGHEST,
)
class NetPInternalConfigProvider @Inject constructor(
    private val mtuInternalProvider: NetPInternalMtuProvider,
    private val exclusionListProvider: NetPInternalExclusionListProvider,
    private val netPInternalFeatureToggles: NetPInternalFeatureToggles,
) : NetPConfigProvider {
    private val defaultConfig = object : NetPConfigProvider {}

    override fun mtu(): Int {
        return mtuInternalProvider.getMtu()
    }

    override fun exclusionList(): Set<String> {
        return mutableSetOf<String>().apply {
            addAll(defaultConfig.exclusionList())
            addAll(exclusionListProvider.getExclusionList())
        }.toSet()
    }

    override fun dns(): Set<InetAddress> {
        return if (netPInternalFeatureToggles.dnsLeakProtection().isEnabled()) {
            InetAddress.getAllByName("one.one.one.one").toSet()
        } else {
            emptySet()
        }
    }
}
