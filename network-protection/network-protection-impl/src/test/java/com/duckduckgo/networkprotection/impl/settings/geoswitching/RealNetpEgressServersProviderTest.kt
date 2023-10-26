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

import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.networkprotection.impl.configuration.FakeWgVpnControllerService
import com.duckduckgo.networkprotection.impl.settings.geoswitching.NetpEgressServersProvider.ServerLocation
import com.duckduckgo.networkprotection.store.NetPGeoswitchingRepository
import com.duckduckgo.networkprotection.store.db.NetPGeoswitchingLocation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class RealNetpEgressServersProviderTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private lateinit var testee: RealNetpEgressServersProvider
    private var wgVpnControllerService = FakeWgVpnControllerService()

    @Mock
    private lateinit var netPGeoswitchingRepository: NetPGeoswitchingRepository

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        testee = RealNetpEgressServersProvider(
            wgVpnControllerService,
            coroutineRule.testDispatcherProvider,
            netPGeoswitchingRepository,
        )
    }

    @Test
    fun whenDownloadDateThenParseAndReplaceStoredLocations() = runTest {
        testee.downloadServerLocations()
        val expectedResult = listOf(
            NetPGeoswitchingLocation(
                countryCode = "nl",
                countryName = "Netherlands",
                cities = listOf("Rotterdam"),
            ),
            NetPGeoswitchingLocation(
                countryCode = "us",
                countryName = "United States",
                cities = listOf("Des Moines", "El Segundo", "Newark"),
            ),
        )
        verify(netPGeoswitchingRepository).replaceLocations(expectedResult)
    }

    @Test
    fun whenGetDownloadedDataThenReturnDataFromRepository() = runTest {
        whenever(netPGeoswitchingRepository.getLocations()).thenReturn(
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
