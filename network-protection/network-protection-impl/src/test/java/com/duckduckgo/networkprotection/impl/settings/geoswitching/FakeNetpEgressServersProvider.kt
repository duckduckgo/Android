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

import com.duckduckgo.networkprotection.impl.configuration.EligibleLocation
import com.duckduckgo.networkprotection.impl.settings.geoswitching.NetpEgressServersProvider.PreferredLocation
import com.duckduckgo.networkprotection.impl.settings.geoswitching.NetpEgressServersProvider.ServerLocation

class FakeNetpEgressServersProvider : NetpEgressServersProvider {
    override suspend fun updateServerLocationsAndReturnPreferred(eligibleLocations: List<EligibleLocation>): PreferredLocation? {
        TODO("Not yet implemented")
    }

    override suspend fun getServerLocations(): List<ServerLocation> {
        return listOf(
            ServerLocation(
                countryCode = "us",
                countryName = "United States",
                cities = listOf("El Segundo", "Chicago", "Atlanta", "Newark"),
            ),
            ServerLocation(
                countryCode = "gb",
                countryName = "UK",
                cities = emptyList(),
            ),
        )
    }
}
