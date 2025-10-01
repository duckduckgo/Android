/*
 * Copyright (c) 2022 DuckDuckGo
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

import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.isInternalBuild
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.networkprotection.impl.configuration.WgServerApi.Mode
import com.duckduckgo.networkprotection.impl.configuration.WgServerApi.Mode.FailureRecovery
import com.duckduckgo.networkprotection.impl.configuration.WgServerApi.WgServerData
import com.duckduckgo.networkprotection.impl.settings.geoswitching.NetpEgressServersProvider
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import logcat.logcat

interface WgServerApi {
    data class WgServerData(
        val serverName: String,
        val publicKey: String,
        val publicEndpoint: String,
        val address: String,
        val location: String?,
        val gateway: String,
    )

    suspend fun registerPublicKey(
        publicKey: String,
        mode: Mode? = null,
    ): WgServerData?

    sealed class Mode {
        data class FailureRecovery(val currentServer: String) : Mode() {
            override fun toString(): String {
                return "failureRecovery"
            }
        }
    }
}

@ContributesBinding(VpnScope::class)
class RealWgServerApi @Inject constructor(
    private val wgVpnControllerService: WgVpnControllerService,
    private val serverDebugProvider: WgServerDebugProvider,
    private val netNetpEgressServersProvider: NetpEgressServersProvider,
    private val appBuildConfig: AppBuildConfig,
) : WgServerApi {

    override suspend fun registerPublicKey(
        publicKey: String,
        mode: Mode?,
    ): WgServerData? {
        // This bit of code gets all possible egress servers which should be order by proximity, caches them for internal builds and then
        // returns the closest one or null if list is empty
        val selectedServer = if (appBuildConfig.isInternalBuild()) {
            wgVpnControllerService.getServers().map { it.server }
                .also { fetchedServers ->
                    logcat { "Fetched servers ${fetchedServers.map { it.name }}" }
                    serverDebugProvider.cacheServers(fetchedServers)
                }
                .map { it.name }
                .firstOrNull { serverName ->
                    serverDebugProvider.getSelectedServerName()?.let { userSelectedServer ->
                        serverName == userSelectedServer
                    } ?: false
                }
        } else {
            null
        }

        val userPreferredLocation = netNetpEgressServersProvider.updateServerLocationsAndReturnPreferred(
            wgVpnControllerService.getEligibleLocations(),
        )
        val registerKeyBody = if (mode is FailureRecovery) {
            RegisterKeyBody(publicKey = publicKey, server = mode.currentServer, mode = mode.toString())
        } else if (selectedServer != null) {
            RegisterKeyBody(publicKey = publicKey, server = selectedServer)
        } else if (userPreferredLocation != null) {
            if (userPreferredLocation.cityName != null) {
                RegisterKeyBody(
                    publicKey = publicKey,
                    country = userPreferredLocation.countryCode,
                    city = userPreferredLocation.cityName,
                )
            } else {
                RegisterKeyBody(publicKey = publicKey, country = userPreferredLocation.countryCode)
            }
        } else {
            RegisterKeyBody(publicKey = publicKey, server = "*")
        }

        return wgVpnControllerService.registerKey(registerKeyBody)
            .run {
                logcat { "Register key in $selectedServer" }
                logcat { "Register key returned ${this.map { it.server.name }}" }
                val server = this.firstOrNull()?.toWgServerData()
                logcat { "Selected Egress server is $server" }
                server
            }
    }

    private fun EligibleServerInfo.toWgServerData(): WgServerData = WgServerData(
        serverName = server.name,
        publicKey = server.publicKey,
        publicEndpoint = server.extractPublicEndpoint(),
        address = allowedIPs.first(),
        gateway = server.internalIp,
        location = server.attributes.extractLocation(),
    )

    private fun Server.extractPublicEndpoint(): String {
        return if (ips.isNotEmpty()) {
            ips[0]
        } else {
            hostnames[0]
        } + ":" + port
    }

    private fun Map<String, Any?>.extractLocation(): String? {
        val serverAttributes = ServerAttributes(this)

        return if (serverAttributes.country != null && serverAttributes.city != null) {
            "${serverAttributes.city}, ${serverAttributes.country!!.uppercase()}"
        } else {
            null
        }
    }

    private data class ServerAttributes(val map: Map<String, Any?>) {
        // withDefault wraps the map to return null for missing keys
        private val attributes = map.withDefault { null }

        val city: String? by attributes
        val country: String? by attributes
    }
}

interface WgServerDebugProvider {
    suspend fun getSelectedServerName(): String? = null

    suspend fun clearSelectedServerName() {
        /* noop */
    }

    suspend fun cacheServers(servers: List<Server>) {
        /* noop */
    }
}

// Contribute just the default dummy implementation
@ContributesBinding(AppScope::class)
class WgServerDebugProviderImpl @Inject constructor() : WgServerDebugProvider
