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

import com.duckduckgo.anvil.annotations.ContributesPluginPoint
import com.duckduckgo.app.global.extensions.capitalizeFirstLetter
import com.duckduckgo.app.global.plugins.PluginPoint
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.isInternalBuild
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.networkprotection.impl.configuration.WgServerApi.WgServerData
import com.squareup.anvil.annotations.ContributesBinding
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.abs
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
    private val wgVpnControllerService: WgVpnControllerService,
    private val timezoneProvider: DeviceTimezoneProvider,
    private val appBuildConfig: AppBuildConfig,
    private val serverDebugProvider: PluginPoint<WgServerDebugProvider>,
) : WgServerApi {

    override suspend fun registerPublicKey(publicKey: String): WgServerData? {
        return wgVpnControllerService.getServers()
            .also {
                if (appBuildConfig.isInternalBuild()) {
                    assert(serverDebugProvider.getPlugins().size <= 1) { "Only one debug server provider can be registered" }
                    serverDebugProvider.getPlugins().firstOrNull()?.let { provider ->
                        provider.storeEligibleServers(it.map { it.server })
                    }
                }
            }
            .filter {
                if (appBuildConfig.isInternalBuild()) {
                    serverDebugProvider.getPlugins().firstOrNull()?.let { provider ->
                        provider.getSelectedServerName()?.let { selectedServer ->
                            it.server.name == selectedServer
                        } ?: true
                    } ?: true
                } else {
                    true
                }
            }
            .run {
                val selectedServer = this.findClosestServer(timezoneProvider.getTimeZone())
                logcat { "Closest server is: ${selectedServer.server.name}" }
                wgVpnControllerService.registerKey(
                    RegisterKeyBody(
                        publicKey = publicKey,
                        server = selectedServer.server.name,
                    ),
                ).firstOrNull()?.toWgServerData().also {
                    logcat { "Registered public key to server: $it" }
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
            "${serverAttributes.city}, ${serverAttributes.country!!.getDisplayableCountry()}"
        } else {
            null
        }
    }

    private fun String.getDisplayableCountry(): String = Locale("", this).displayCountry.lowercase().capitalizeFirstLetter()

    private fun Collection<RegisteredServerInfo>.findClosestServer(timeZone: TimeZone): RegisteredServerInfo {
        val serverAttributes = this.map { ServerAttributes(it.server.attributes) }.sortedBy { it.tzOffset }
        var min = Int.MAX_VALUE.toLong()
        val offset = TimeUnit.MILLISECONDS.toSeconds(timeZone.rawOffset.toLong())
        var closest = offset

        serverAttributes.forEach { attrs ->
            val diff = abs(attrs.tzOffset - offset)
            if (diff < min) {
                min = diff
                closest = attrs.tzOffset
            }
        }

        return this.firstOrNull { ServerAttributes(it.server.attributes).tzOffset == closest } ?: this.first()
    }

    private data class ServerAttributes(val map: Map<String, Any?>) {
        // withDefault wraps the map to return null for missing keys
        private val attributes = map.withDefault { null }

        val city: String? by attributes
        val country: String? by attributes
        val tzOffset: Long by attributes
    }
}

interface DeviceTimezoneProvider {
    fun getTimeZone(): TimeZone
}

@ContributesBinding(VpnScope::class)
class SystemDeviceTimezoneProvider @Inject constructor() : DeviceTimezoneProvider {
    override fun getTimeZone(): TimeZone = TimeZone.getDefault()
}

@ContributesPluginPoint(VpnScope::class)
interface WgServerDebugProvider {
    suspend fun getSelectedServerName(): String?

    suspend fun storeEligibleServers(servers: List<Server>)
}
