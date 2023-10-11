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

import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.networkprotection.impl.settings.geoswitching.GeoSwitchingContentProvider.AvailableCountry
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface GeoSwitchingContentProvider {
    fun getContent(): List<AvailableCountry>

    data class AvailableCountry(
        val countryCode: String,
        val cities: List<String>
    )
}

@ContributesBinding(ActivityScope::class)
class MockGeoSwitchingContentProvider @Inject constructor() : GeoSwitchingContentProvider {
    override fun getContent(): List<AvailableCountry> {
        return listOf(
            AvailableCountry(
                countryCode = "uk",
                cities = emptyList(),
            ),
            AvailableCountry(
                countryCode = "fr",
                cities = emptyList(),
            ),
            AvailableCountry(
                countryCode = "ca",
                cities = listOf("Montreal", "Toronto"),
            ),
            AvailableCountry(
                countryCode = "es",
                cities = emptyList(),
            ),
            AvailableCountry(
                countryCode = "de",
                cities = listOf("Berlin", "Munich"),
            ),
            AvailableCountry(
                countryCode = "us",
                cities = listOf("Chicago", "El Segundo", "Newark", "Atlanta"),
            ),
        )
    }
}
