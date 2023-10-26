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

package com.duckduckgo.networkprotection.impl.settings.geoswitching

import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.networkprotection.impl.configuration.WgVpnControllerService
import com.duckduckgo.networkprotection.impl.di.ProtectedVpnControllerService
import com.duckduckgo.networkprotection.impl.settings.geoswitching.NetpEgressServersProvider.ServerLocation
import com.duckduckgo.networkprotection.store.NetPGeoswitchingRepository
import com.duckduckgo.networkprotection.store.db.NetPGeoswitchingLocation
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlinx.coroutines.withContext

interface NetpEgressServersProvider {
    suspend fun downloadServerLocations()
    suspend fun getServerLocations(): List<ServerLocation>

    data class ServerLocation(
        val countryCode: String,
        val countryName: String,
        val cities: List<String>,
    )
}

@ContributesBinding(AppScope::class)
class RealNetpEgressServersProvider @Inject constructor(
    @ProtectedVpnControllerService private val wgVpnControllerService: WgVpnControllerService,
    private val dispatcherProvider: DispatcherProvider,
    private val netPGeoswitchingRepository: NetPGeoswitchingRepository,
) : NetpEgressServersProvider {
    override suspend fun downloadServerLocations() {
        withContext(dispatcherProvider.io()) {
            parseServersToServerLocations().map {
                NetPGeoswitchingLocation(
                    countryCode = it.countryCode,
                    countryName = it.countryName,
                    cities = it.cities,
                )
            }.also {
                netPGeoswitchingRepository.replaceLocations(it)
            }
        }
    }

    override suspend fun getServerLocations(): List<ServerLocation> = withContext(dispatcherProvider.io()) {
        netPGeoswitchingRepository.getLocations().map {
            ServerLocation(
                countryCode = it.countryCode,
                countryName = it.countryName,
                cities = it.cities,
            )
        }
    }

    private suspend fun parseServersToServerLocations(): List<ServerLocation> {
        val locations = mutableMapOf<String, MutableSet<String>>()

        wgVpnControllerService.getServers().forEach {
            if (it.server.attributes["country"] == null || it.server.attributes["city"] == null) {
                return@forEach
            }
            val countryCode = it.server.attributes["country"] as String
            val city = it.server.attributes["city"] as String

            if (locations.containsKey(countryCode)) {
                locations[countryCode]?.add(city)
            } else {
                locations[countryCode] = mutableSetOf(city)
            }
        }

        return locations.map {
            ServerLocation(
                countryCode = it.key,
                countryName = getDisplayableCountry(it.key),
                cities = it.value.toList().sorted(),
            )
        }.toList().sortedBy { it.countryName }
    }
}
