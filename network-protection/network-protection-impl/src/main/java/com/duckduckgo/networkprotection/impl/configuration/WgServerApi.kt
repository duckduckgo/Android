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

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.networkprotection.impl.configuration.WgServerApi.WgServerData
import com.duckduckgo.networkprotection.impl.di.UnprotectedVpnControllerService
import com.duckduckgo.networkprotection.impl.settings.geoswitching.NetpEgressServersProvider
import com.duckduckgo.networkprotection.impl.store.NetworkProtectionRepository
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
        val allowedIPs: String = "0.0.0.0/0,::0/0",
    )

    suspend fun registerPublicKey(publicKey: String): WgServerData?
}

@ContributesBinding(VpnScope::class)
class RealWgServerApi @Inject constructor(
    @UnprotectedVpnControllerService private val wgVpnControllerService: WgVpnControllerService,
    private val serverDebugProvider: WgServerDebugProvider,
    private val netNetpEgressServersProvider: NetpEgressServersProvider,
    private val netpRepository: NetworkProtectionRepository,
) : WgServerApi {

    override suspend fun registerPublicKey(publicKey: String): WgServerData? {
        val registerKeyBody = try {
            // This bit of code gets all possible egress servers which should be order by proximity, caches them for internal builds and then
            // returns the closest one or null if list is empty
            val selectedServer = wgVpnControllerService.getServers().map { it.server }
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

            logcat { "Register key in $selectedServer" }
            val userPreferredLocation = netNetpEgressServersProvider.updateServerLocationsAndReturnPreferred()
            if (selectedServer != null) {
                RegisterKeyBody(publicKey = publicKey, server = selectedServer)
            } else if (userPreferredLocation != null) {
                if (userPreferredLocation.cityName != null) {
                    RegisterKeyBody(publicKey = publicKey, country = userPreferredLocation.countryCode, city = userPreferredLocation.cityName)
                } else {
                    RegisterKeyBody(publicKey = publicKey, country = userPreferredLocation.countryCode)
                }
            } else {
                RegisterKeyBody(publicKey = publicKey, server = "*")
            }
        } catch (e: Exception) {
            RegisterKeyBody(publicKey = publicKey, server = "*")
        }

        return wgVpnControllerService.registerKey(registerKeyBody)
            .run {
                if (isSuccessful) {
                    logcat { "Register key returned ${this.body()?.map { it.server.name }}" }
                    val server = this.body()?.firstOrNull()?.toWgServerData()
                    logcat { "Selected Egress server is $server" }
                    netpRepository.disabledDueToAccessRevoked = false
                    server
                } else {
                    netpRepository.disabledDueToAccessRevoked = this.code() == 403
                    null
                }
            }
    }

    private fun EligibleServerInfo.toWgServerData(): WgServerData = WgServerData(
        serverName = server.name,
        publicKey = server.publicKey,
        publicEndpoint = server.extractPublicEndpoint(),
        address = allowedIPs.joinToString(","),
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

    suspend fun clearSelectedServerName() { /* noop */
    }

    suspend fun cacheServers(servers: List<Server>) { /* noop */
    }
}

// Contribute just the default dummy implementation
@ContributesBinding(AppScope::class)
class WgServerDebugProviderImpl @Inject constructor() : WgServerDebugProvider
