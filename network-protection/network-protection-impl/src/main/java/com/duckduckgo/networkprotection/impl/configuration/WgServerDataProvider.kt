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
import com.duckduckgo.networkprotection.impl.configuration.WgServerDataProvider.WgServerData
import com.squareup.anvil.annotations.ContributesBinding
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.abs
import logcat.logcat

interface WgServerDataProvider {
    data class WgServerData(
        val publicKey: String,
        val publicEndpoint: String,
        val address: String,
        val location: String?,
        val allowedIPs: String = "0.0.0.0/0,::0/0",
    )

    suspend fun get(publicKey: String): WgServerData?
}

@ContributesBinding(VpnScope::class)
class RealWgServerDataProvider @Inject constructor(
    private val wgVpnControllerService: WgVpnControllerService,
    private val timezoneProvider: DeviceTimezoneProvider,
    private val appBuildConfig: AppBuildConfig,
    private val serverDebugProvider: PluginPoint<WgServerDebugProvider>,
) : WgServerDataProvider {

    override suspend fun get(publicKey: String): WgServerData? {
        return wgVpnControllerService.getServers()
            .also {
                if (appBuildConfig.isInternalBuild()) {
                    assert(serverDebugProvider.getPlugins().size <= 1) { "Only one debug server provider can be registered" }
                    serverDebugProvider.getPlugins().firstOrNull()?.let { provider ->
                        provider.storeEligibleServers(it.map { it.server })
                    }
                }
            }
            .run {
                val selectedServer = this.selectClosestServer()
                logcat { "Closest server is: ${selectedServer.server.name}" }
                wgVpnControllerService.registerKey(
                    RegisterKeyBody(
                        publicKey = publicKey,
                        server = selectedServer.server.name,
                    ),
                ).firstOrNull()?.toWgServerData()
            }
    }

    private fun EligibleServerInfo.toWgServerData(): WgServerData = WgServerData(
        publicKey = server.publicKey,
        publicEndpoint = server.extractPublicEndpoint(),
        address = allowedIPs.joinToString(","),
        location = server.attributes.extractLocation(),
    )

    private fun Server.extractPublicEndpoint(): String {
        return if (ips.isNotEmpty()) {
            ips[0]
        } else {
            hostnames[0]
        } + ":" + port
    }

    private fun Map<String, String>.extractLocation(): String? {
        val country = this[SERVER_ATTR_COUNTRY]
        val city = this[SERVER_ATTR_CITY]?.lowercase()?.capitalizeFirstLetter()

        return if (country != null && city != null) {
            "$city, ${country.getDisplayableCountry()}"
        } else {
            null
        }
    }

    private fun String.getDisplayableCountry(): String = Locale("", this).displayCountry.lowercase().capitalizeFirstLetter()

    private suspend fun List<RegisteredServerInfo>.selectClosestServer(): RegisteredServerInfo {
        fun Server.extractRegion(): String = this.name.split(".")[1]

        assert(this.isNotEmpty()) { "List of RegisteredServerInfo can't ge empty" }

        if (appBuildConfig.isInternalBuild()) {
            assert(serverDebugProvider.getPlugins().size <= 1) { "Only one debug server provider can be registered" }
            firstOrNull { it.server.name == serverDebugProvider.getPlugins().firstOrNull()?.getSelectedServerName() }
        } else {
            null
        }?.let { return it }

        val eligibleRegionsIntersection = SERVER_TIMEZONES.filter { region -> this.map { it.server.extractRegion() }.contains(region.region) }.toSet()
        val selectedServer = timezoneProvider.getTimeZone().findClosestRegion(eligibleRegionsIntersection)
        // Return the selected closest server or fallback to just the first element
        return (this.firstOrNull { it.server.name.contains(selectedServer.region) } ?: this[0]).also { selected ->
            logcat { "Selected closest server: $selected" }
        }
    }

    private fun TimeZone.findClosestRegion(servers: Set<ServerRegions>): ServerRegions {
        val serverTimezones = servers.sortedBy { it.offsetBoundary }.map { it.offsetBoundary }
        var min = Int.MAX_VALUE.toLong()
        val offset = rawOffset / TimeUnit.HOURS.toMillis(1)
        var closest = offset

        serverTimezones.forEach { tz ->
            val diff = abs(tz - offset)
            if (diff < min) {
                min = diff
                closest = tz
            }
        }

        return SERVER_TIMEZONES.find { it.offsetBoundary == closest } ?: SERVER_TIMEZONES[1] // USE by default
    }

    companion object {
        private const val SERVER_ATTR_CITY = "city"
        private const val SERVER_ATTR_COUNTRY = "country"
        private val SERVER_TIMEZONES = listOf(
            ServerRegions("euw", 0L),
            ServerRegions("use", -5L),
            ServerRegions("usc", -7L),
            ServerRegions("usw", -8L),
        )
    }
}

internal data class ServerRegions(val region: String, val offsetBoundary: Long)

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
