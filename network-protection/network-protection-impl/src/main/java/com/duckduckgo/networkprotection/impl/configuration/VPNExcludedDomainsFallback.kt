/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.networkprotection.impl.configuration

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.networkprotection.impl.configuration.VPNExcludedDomainsFallback.DnsEntry
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface VPNExcludedDomainsFallback {
    fun getFallback(): Map<String, List<DnsEntry>>

    data class DnsEntry(
        val address: String,
        val region: String,
    )
}

@ContributesBinding(AppScope::class)
class RealVPNExcludedDomainsFallback @Inject constructor() : VPNExcludedDomainsFallback {
    override fun getFallback(): Map<String, List<DnsEntry>> = mapOf(
        CONTROLLER_NETP_DUCKDUCKGO_COM to listOf(
            DnsEntry(VPN_USE_CONTROLLER, "use"),
            DnsEntry(VPN_EUN_CONTROLLER, "eun"),
        ),
        AUTH_DUCKDUCKGO_COM to listOf(
            DnsEntry(AUTH_EUN, "eun"),
            DnsEntry(AUTH_EUW, "euw"),
        ),
    )

    companion object {
        private const val CONTROLLER_NETP_DUCKDUCKGO_COM = "controller.netp.duckduckgo.com"
        private const val VPN_EUN_CONTROLLER = "20.93.77.32"
        private const val VPN_USE_CONTROLLER = "20.253.26.112"
        private const val AUTH_DUCKDUCKGO_COM = "quack.duckduckgo.com"
        private const val AUTH_EUN = "52.146.152.248"
        private const val AUTH_EUW = "4.175.55.40"
    }
}
