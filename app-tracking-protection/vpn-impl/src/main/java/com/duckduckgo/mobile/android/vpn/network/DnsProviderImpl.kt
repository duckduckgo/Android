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

package com.duckduckgo.mobile.android.vpn.network

import android.content.Context
import com.duckduckgo.common.utils.extensions.getPrivateDnsServerName
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.vpn.network.util.getSystemActiveNetworkDefaultDns
import com.duckduckgo.mobile.android.vpn.network.util.getSystemActiveNetworkSearchDomain
import com.squareup.anvil.annotations.ContributesBinding
import java.net.InetAddress
import javax.inject.Inject

@ContributesBinding(VpnScope::class)
class DnsProviderImpl @Inject constructor(
    private val context: Context,
) : DnsProvider {
    override fun getSystemDns(): List<InetAddress> {
        return runCatching { context.getSystemActiveNetworkDefaultDns() }.getOrDefault(emptyList())
            .map { InetAddress.getByName(it) }
    }

    override fun getSearchDomains(): String? {
        return runCatching { context.getSystemActiveNetworkSearchDomain() }.getOrNull()
    }

    override fun getPrivateDns(): List<InetAddress> {
        return runCatching {
            context.getPrivateDnsServerName()?.let { InetAddress.getAllByName(it).toList() } ?: emptyList()
        }.getOrDefault(emptyList())
    }
}
