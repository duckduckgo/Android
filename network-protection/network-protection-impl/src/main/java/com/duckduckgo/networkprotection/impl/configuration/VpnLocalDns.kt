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

import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.networkprotection.impl.VpnRemoteFeatures
import com.squareup.anvil.annotations.ContributesTo
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn
import java.net.InetAddress
import javax.inject.Qualifier
import logcat.asLog
import logcat.logcat
import okhttp3.Dns

interface VpnLocalDns : Dns

internal const val CONTROLLER_NETP_DUCKDUCKGO_COM = "controller.netp.duckduckgo.com"
private const val VPN_EUN_CONTROLLER = "20.93.77.32"
private const val VPN_USE_CONTROLLER = "20.253.26.112"

private class VpnLocalDnsImpl(
    private val vpnRemoteFeatures: VpnRemoteFeatures,
    moshi: Moshi,
    private val defaultDns: Dns,
) : VpnLocalDns {

    private val adapter = moshi.adapter(LocalDnsSettings::class.java)

    /**
     * String is the domain
     * DnsEntry is the DNS entry corresponding to the domain
     */
    private val localDomains: Map<String, List<DnsEntry>> by lazy {
        getRemoteDnsEntries()
    }

    private val fallbackDomains: Map<String, List<DnsEntry>> = mapOf(
        CONTROLLER_NETP_DUCKDUCKGO_COM to listOf(
            DnsEntry(VPN_USE_CONTROLLER, "use"),
            DnsEntry(VPN_EUN_CONTROLLER, "eun"),
        ),
    )

    override fun lookup(hostname: String): List<InetAddress> {
        logcat { "Lookup for $hostname" }
        if (vpnRemoteFeatures.localVpnControllerDns().isEnabled() == false) {
            return defaultDns.lookup(hostname)
        }

        return try {
            defaultDns.lookup(hostname)
        } catch (t: Throwable) {
            val localResolution = localDomains[hostname] ?: fallbackDomains[hostname]
            localResolution?.let { entries ->
                logcat { "Hardcoded DNS for $hostname" }

                entries.map { InetAddress.getByName(it.address) }
            } ?: throw t
        }
    }

    private fun getRemoteDnsEntries(): Map<String, List<DnsEntry>> {
        vpnRemoteFeatures.localVpnControllerDns().getSettings()?.let { settings ->
            return try {
                adapter.fromJson(settings)?.domains
            } catch (t: Throwable) {
                logcat { "Error parsing localDNS settings: ${t.asLog()}" }
                null
            }.orEmpty()
        }

        return emptyMap()
    }

    /*
    "settings": {
        "domains": {
            "controller.netp.duckduckgo.com": [
                {
                    "address": "1.2.3.4",
                    "region": "use"
                },
                {
                    "address": "1.2.2.2",
                    "region": "eun"
                }
            ]
        }
    }
     */

    private data class LocalDnsSettings(
        val domains: Map<String, List<DnsEntry>>,
    )

    private data class DnsEntry(
        val address: String,
        val region: String,
    )
}

@ContributesTo(VpnScope::class)
@Module
object VpnLocalDnsModule {
    @Retention(AnnotationRetention.BINARY)
    @Qualifier
    private annotation class InternalApi

    @Provides
    @SingleInstanceIn(VpnScope::class)
    fun provideVpnLocalDns(
        vpnRemoteFeatures: VpnRemoteFeatures,
        moshi: Moshi,
        @InternalApi defaultDns: Dns,
    ): VpnLocalDns {
        return VpnLocalDnsImpl(vpnRemoteFeatures, moshi, defaultDns)
    }

    @Provides
    @InternalApi
    fun provideDefaultDns(): Dns {
        return Dns.SYSTEM
    }
}
