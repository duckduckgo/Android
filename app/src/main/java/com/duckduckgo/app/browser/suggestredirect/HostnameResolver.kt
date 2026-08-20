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

package com.duckduckgo.app.browser.suggestredirect

import android.net.ConnectivityManager
import com.duckduckgo.app.browser.suggestredirect.DnsLookup.LookupResult.Failure
import com.duckduckgo.app.browser.suggestredirect.DnsLookup.LookupResult.Success
import com.duckduckgo.common.utils.DispatcherProvider
import com.wireguard.config.InetAddresses
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration

interface HostnameResolver {
    /**
     * @return
     * - `true` when [hostname] resolves to at least one IP address on the active
     * network within the lookup timeout.
     * - `false` on failure, timeout, no connectivity or syntactically invalid
     * hostnames (including IPv4/IPv6 literals).
     */
    suspend fun resolves(hostname: String): Boolean

    companion object {
        const val LOOKUP_TIMEOUT_MS = 2_000L
    }
}

class HostnameResolverImpl(
    private val lookupTimeout: Duration,
    private val connectivityManager: ConnectivityManager,
    private val dispatcherProvider: DispatcherProvider,
    private val dnsLookup: DnsLookup,
) : HostnameResolver {
    override suspend fun resolves(hostname: String): Boolean {
        // isHostname() accepts all-numeric labels, so IPv4 literals must be rejected separately.
        if (!InetAddresses.isHostname(hostname) || IPV4_LITERAL.matches(hostname)) {
            return false
        }

        val network = withContext(dispatcherProvider.io()) {
            connectivityManager.activeNetwork
        } ?: return false

        val result = withTimeoutOrNull(lookupTimeout) {
            dnsLookup.lookup(network, hostname)
        } ?: return false

        return when (result) {
            is Success -> result.addresses.isNotEmpty()
            is Failure -> false
        }
    }

    private companion object {
        val IPV4_LITERAL = Regex("""\d{1,3}(\.\d{1,3}){3}""")
    }
}
