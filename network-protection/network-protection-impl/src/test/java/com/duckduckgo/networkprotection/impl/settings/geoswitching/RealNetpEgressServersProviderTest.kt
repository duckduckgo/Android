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

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.networkprotection.impl.configuration.FakeWgVpnControllerService
import com.duckduckgo.networkprotection.impl.settings.geoswitching.NetpEgressServersProvider.PreferredLocation
import com.duckduckgo.networkprotection.impl.settings.geoswitching.NetpEgressServersProvider.ServerLocation
import com.duckduckgo.networkprotection.store.NetPGeoswitchingRepository
import com.duckduckgo.networkprotection.store.NetPGeoswitchingRepository.UserPreferredLocation
import com.duckduckgo.networkprotection.store.db.NetPGeoswitchingLocation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class RealNetpEgressServersProviderTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private lateinit var testee: RealNetpEgressServersProvider
    private val wgVpnControllerService = FakeWgVpnControllerService()

    private lateinit var netPGeoswitchingRepository: NetPGeoswitchingRepository

    @Before
    fun setUp() {
        netPGeoswitchingRepository = FakeNetPGeoswitchingRepository()
        testee = RealNetpEgressServersProvider(
            coroutineRule.testDispatcherProvider,
            netPGeoswitchingRepository,
        )
    }

    @Test
    fun whenDownloadDateThenParseAndReplaceStoredLocations() = runTest {
        assertNull(testee.updateServerLocationsAndReturnPreferred(wgVpnControllerService.getEligibleLocations()))
        val expectedResult = listOf(
            NetPGeoswitchingLocation(
                countryCode = "nl",
                countryName = "Netherlands",
                cities = listOf("Rotterdam"),
            ),
            NetPGeoswitchingLocation(
                countryCode = "us",
                countryName = "United States",
                cities = listOf("Newark", "El Segundo", "Des Moines"),
            ),
            NetPGeoswitchingLocation(
                countryCode = "se",
                countryName = "Sweden",
                cities = listOf("Gothenburg", "Malmo", "Stockholm"),
            ),
        )
        assertEquals(expectedResult, netPGeoswitchingRepository.getLocations())
    }

    @Test
    fun whenUpdateLocationsUpdateUserPreferredIfNotPresent() = runTest {
        netPGeoswitchingRepository.setUserPreferredLocation(
            UserPreferredLocation(
                countryCode = "se",
                cityName = "Gothenburg",
            ),
        )

        assertEquals(
            PreferredLocation(countryCode = "se", cityName = "Gothenburg"),
            testee.updateServerLocationsAndReturnPreferred(wgVpnControllerService.getEligibleLocations()),
        )
        val expectedResult = listOf(
            NetPGeoswitchingLocation(
                countryCode = "nl",
                countryName = "Netherlands",
                cities = listOf("Rotterdam"),
            ),
            NetPGeoswitchingLocation(
                countryCode = "us",
                countryName = "United States",
                cities = listOf("Newark", "El Segundo", "Des Moines"),
            ),
            NetPGeoswitchingLocation(
                countryCode = "se",
                countryName = "Sweden",
                cities = listOf("Gothenburg", "Malmo", "Stockholm"),
            ),
        )
        assertEquals(expectedResult, netPGeoswitchingRepository.getLocations())
    }

    @Test
    fun whenUpdateLocationsUpdateUserPreferredIfNotPresentGoneCity() = runTest {
        netPGeoswitchingRepository.setUserPreferredLocation(
            UserPreferredLocation(
                countryCode = "se",
                cityName = "wrong city",
            ),
        )

        assertEquals(
            PreferredLocation(countryCode = "se"),
            testee.updateServerLocationsAndReturnPreferred(wgVpnControllerService.getEligibleLocations()),
        )
        val expectedResult = listOf(
            NetPGeoswitchingLocation(
                countryCode = "nl",
                countryName = "Netherlands",
                cities = listOf("Rotterdam"),
            ),
            NetPGeoswitchingLocation(
                countryCode = "us",
                countryName = "United States",
                cities = listOf("Newark", "El Segundo", "Des Moines"),
            ),
            NetPGeoswitchingLocation(
                countryCode = "se",
                countryName = "Sweden",
                cities = listOf("Gothenburg", "Malmo", "Stockholm"),
            ),
        )
        assertEquals(expectedResult, netPGeoswitchingRepository.getLocations())
    }

    @Test
    fun whenUpdateLocationsUpdateUserPreferredIfNotPresentGoneCountry() = runTest {
        netPGeoswitchingRepository.setUserPreferredLocation(
            UserPreferredLocation(
                countryCode = "zz",
                cityName = "Malmo",
            ),
        )

        assertNull(testee.updateServerLocationsAndReturnPreferred(wgVpnControllerService.getEligibleLocations()))
        val expectedResult = listOf(
            NetPGeoswitchingLocation(
                countryCode = "nl",
                countryName = "Netherlands",
                cities = listOf("Rotterdam"),
            ),
            NetPGeoswitchingLocation(
                countryCode = "us",
                countryName = "United States",
                cities = listOf("Newark", "El Segundo", "Des Moines"),
            ),
            NetPGeoswitchingLocation(
                countryCode = "se",
                countryName = "Sweden",
                cities = listOf("Gothenburg", "Malmo", "Stockholm"),
            ),
        )
        assertEquals(expectedResult, netPGeoswitchingRepository.getLocations())
    }

    @Test
    fun whenUpdateLocationsUpdateUserPreferredIfNotPresentGoneCountryAndCity() = runTest {
        netPGeoswitchingRepository.setUserPreferredLocation(
            UserPreferredLocation(
                countryCode = "zz",
                cityName = "wrong country",
            ),
        )

        assertNull(testee.updateServerLocationsAndReturnPreferred(wgVpnControllerService.getEligibleLocations()))
        val expectedResult = listOf(
            NetPGeoswitchingLocation(
                countryCode = "nl",
                countryName = "Netherlands",
                cities = listOf("Rotterdam"),
            ),
            NetPGeoswitchingLocation(
                countryCode = "us",
                countryName = "United States",
                cities = listOf("Newark", "El Segundo", "Des Moines"),
            ),
            NetPGeoswitchingLocation(
                countryCode = "se",
                countryName = "Sweden",
                cities = listOf("Gothenburg", "Malmo", "Stockholm"),
            ),
        )
        assertEquals(expectedResult, netPGeoswitchingRepository.getLocations())
    }

    @Test
    fun whenUpdateLocationsKeepUserPreferredIfPresent() = runTest {
        netPGeoswitchingRepository.setUserPreferredLocation(
            UserPreferredLocation(
                countryCode = "se",
                cityName = "Gothenburg",
            ),
        )

        assertEquals(
            PreferredLocation(countryCode = "se", cityName = "Gothenburg"),
            testee.updateServerLocationsAndReturnPreferred(wgVpnControllerService.getEligibleLocations()),
        )
        val expectedResult = listOf(
            NetPGeoswitchingLocation(
                countryCode = "nl",
                countryName = "Netherlands",
                cities = listOf("Rotterdam"),
            ),
            NetPGeoswitchingLocation(
                countryCode = "us",
                countryName = "United States",
                cities = listOf("Newark", "El Segundo", "Des Moines"),
            ),
            NetPGeoswitchingLocation(
                countryCode = "se",
                countryName = "Sweden",
                cities = listOf("Gothenburg", "Malmo", "Stockholm"),
            ),
        )
        assertEquals(expectedResult, netPGeoswitchingRepository.getLocations())
    }

    @Test
    fun whenGetDownloadedDataThenReturnDataFromRepository() = runTest {
        netPGeoswitchingRepository.replaceLocations(
            listOf(
                NetPGeoswitchingLocation(
                    countryCode = "se",
                    countryName = "Sweden",
                    cities = listOf("Gothenburg", "Malmo", "Stockholm"),
                ),
            ),
        )

        assertEquals(
            listOf(
                ServerLocation(
                    countryCode = "se",
                    countryName = "Sweden",
                    cities = listOf("Gothenburg", "Malmo", "Stockholm"),
                ),
            ),
            testee.getServerLocations(),
        )
    }
}
