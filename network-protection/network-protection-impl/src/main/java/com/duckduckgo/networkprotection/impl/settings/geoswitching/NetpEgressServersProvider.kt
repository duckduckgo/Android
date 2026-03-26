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

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.networkprotection.impl.configuration.EligibleLocation
import com.duckduckgo.networkprotection.impl.settings.geoswitching.NetpEgressServersProvider.PreferredLocation
import com.duckduckgo.networkprotection.impl.settings.geoswitching.NetpEgressServersProvider.ServerLocation
import com.duckduckgo.networkprotection.store.NetPGeoswitchingRepository
import com.duckduckgo.networkprotection.store.db.NetPGeoswitchingLocation
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface NetpEgressServersProvider {
    suspend fun updateServerLocationsAndReturnPreferred(eligibleLocations: List<EligibleLocation>): PreferredLocation?
    suspend fun getServerLocations(): List<ServerLocation>

    data class ServerLocation(
        val countryCode: String,
        val countryName: String,
        val cities: List<String>,
    )

    data class PreferredLocation(
        val countryCode: String,
        val cityName: String? = null,
    )
}

@ContributesBinding(AppScope::class)
class RealNetpEgressServersProvider @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    private val netPGeoswitchingRepository: NetPGeoswitchingRepository,
) : NetpEgressServersProvider {
    override suspend fun updateServerLocationsAndReturnPreferred(eligibleLocations: List<EligibleLocation>): PreferredLocation? = withContext(
        dispatcherProvider.io(),
    ) {
        val serverLocations = eligibleLocations
            .map { location ->
                NetPGeoswitchingLocation(
                    countryCode = location.country,
                    countryName = getDisplayableCountry(location.country),
                    cities = location.cities.map { it.name },
                )
            }.toList()
        netPGeoswitchingRepository.replaceLocations(serverLocations)
        val (selectedCountry, selectedCityName) = netPGeoswitchingRepository.getUserPreferredLocation()

        if (selectedCountry != null) {
            if (selectedCityName != null) {
                val isPresent = serverLocations.asSequence()
                    .filter { it.countryCode == selectedCountry }
                    .flatMap { it.cities }
                    .contains(selectedCityName)

                if (isPresent) {
                    // previously selected server location still exists in updated server list
                    return@withContext PreferredLocation(selectedCountry, selectedCityName)
                } else {
                    val isCountryPresent = serverLocations.asSequence().map { it.countryCode }.contains(selectedCountry)
                    if (isCountryPresent) {
                        // previously selected server city location is no longer in updated server list
                        return@withContext PreferredLocation(selectedCountry)
                    } else {
                        return@withContext null
                    }
                }
            } else {
                val isPresent = serverLocations.map { it.countryCode }.contains(selectedCountry)
                if (isPresent) {
                    // previously selected server location still exists in updated server list
                    return@withContext PreferredLocation(selectedCountry)
                } else {
                    // previously selected server location is no longer in updated server list
                    return@withContext null
                }
            }
        } else {
            // previously selected server location is no longer in updated server list
            return@withContext null
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
}
