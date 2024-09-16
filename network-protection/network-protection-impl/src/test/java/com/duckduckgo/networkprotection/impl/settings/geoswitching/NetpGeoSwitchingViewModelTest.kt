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

import androidx.lifecycle.LifecycleOwner
import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.duckduckgo.networkprotection.impl.configuration.WgServerDebugProvider
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixels
import com.duckduckgo.networkprotection.impl.settings.geoswitching.NetpGeoSwitchingViewModel.CountryItem
import com.duckduckgo.networkprotection.store.NetPGeoswitchingRepository
import com.duckduckgo.networkprotection.store.NetPGeoswitchingRepository.UserPreferredLocation
import com.duckduckgo.networkprotection.store.db.NetPGeoswitchingLocation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

class NetpGeoSwitchingViewModelTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()
    private lateinit var testee: NetpGeoSwitchingViewModel

    @Mock
    private lateinit var mockLifecycleOwner: LifecycleOwner

    @Mock
    private lateinit var wgServerDebugProvider: WgServerDebugProvider

    @Mock
    private lateinit var networkProtectionState: NetworkProtectionState

    @Mock
    private lateinit var networkProtectionPixels: NetworkProtectionPixels
    private val fakeContentProvider = FakeNetpEgressServersProvider()
    private val fakeRepository = FakeNetPGeoswitchingRepository()

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        testee = NetpGeoSwitchingViewModel(
            fakeContentProvider,
            fakeRepository,
            coroutineRule.testDispatcherProvider,
            wgServerDebugProvider,
            networkProtectionState,
            networkProtectionPixels,
        )
    }

    @Test
    fun whenViewModelIsInitializedThenViewStateShouldEmitParsedList() = runTest {
        fakeRepository.setUserPreferredLocation(UserPreferredLocation())

        testee.onStart(mockLifecycleOwner)
        testee.viewState().test {
            expectMostRecentItem().also {
                assertEquals(2, it.items.size)
                assertEquals(
                    it.items[0],
                    CountryItem(
                        countryCode = "gb",
                        countryEmoji = "🇬🇧",
                        countryName = "UK",
                        cities = emptyList(),
                    ),
                )
                assertEquals(
                    it.items[1],
                    CountryItem(
                        countryCode = "us",
                        countryEmoji = "🇺🇸",
                        countryName = "United States",
                        cities = listOf("Atlanta", "Chicago", "El Segundo", "Newark"),
                    ),
                )
            }
        }
    }

    @Test
    fun whenProviderHasNoDownloadedDataThenViewStateShouldOnlyContainNearestAvailable() = runTest {
        fakeRepository.setUserPreferredLocation(UserPreferredLocation())

        val mockProvider = mock(NetpEgressServersProvider::class.java)
        testee = NetpGeoSwitchingViewModel(
            mockProvider,
            fakeRepository,
            coroutineRule.testDispatcherProvider,
            wgServerDebugProvider,
            networkProtectionState,
            networkProtectionPixels,
        )

        whenever(mockProvider.getServerLocations()).thenReturn(emptyList())

        testee.onCreate(mockLifecycleOwner)
        testee.viewState().test {
            expectMostRecentItem().also {
                assertEquals(0, it.items.size)
            }
        }
    }

    @Test
    fun whenChosenPreferredCountryAndCityAreChangedThenUpdateStoredCountryAndResetCity() = runTest {
        fakeRepository.setUserPreferredLocation(UserPreferredLocation("us", "Newark"))

        testee.onCountrySelected("uk")

        fakeRepository.getUserPreferredLocation().let {
            assertEquals("uk", it.countryCode)
            assertNull(it.cityName)
        }
        verify(wgServerDebugProvider).clearSelectedServerName()
    }

    @Test
    fun whenChosenPreferredCountryAndCityAreSameThenStoredDataShouldBeSame() = runTest {
        fakeRepository.setUserPreferredLocation(UserPreferredLocation("us", "Newark"))

        testee.onCountrySelected("us")

        fakeRepository.getUserPreferredLocation().let {
            assertEquals("us", it.countryCode)
            assertEquals("Newark", it.cityName)
        }
        verifyNoInteractions(wgServerDebugProvider)
    }

    @Test
    fun whenNearestAvailableCountrySelectedThenStoredDataShouldBeNull() = runTest {
        fakeRepository.setUserPreferredLocation(UserPreferredLocation("us", "Newark"))

        testee.onNearestAvailableCountrySelected()

        fakeRepository.getUserPreferredLocation().let {
            assertNull(it.countryCode)
            assertNull(it.cityName)
        }
        verify(wgServerDebugProvider).clearSelectedServerName()
    }

    @Test
    fun whenNetPIsNotEnabledThenDoNotRestart() = runTest {
        whenever(networkProtectionState.isEnabled()).thenReturn(false)

        testee.onStart(mockLifecycleOwner)
        testee.onStop(mockLifecycleOwner)

        verify(networkProtectionState).isEnabled()
        verifyNoMoreInteractions(networkProtectionState)
    }

    @Test
    fun whenNetPIsEnabledButNoChangeInPreferredLocationThenDoNotRestart() = runTest {
        fakeRepository.setUserPreferredLocation(UserPreferredLocation(countryCode = "us"))
        whenever(networkProtectionState.isEnabled()).thenReturn(true)

        testee.onStart(mockLifecycleOwner)
        testee.onCountrySelected("us")
        testee.onStop(mockLifecycleOwner)

        verify(networkProtectionState).isEnabled()
        verifyNoMoreInteractions(networkProtectionState)
    }

    @Test
    fun whenNetPIsEnabledAndPreferredCountryChangedThenRestart() = runTest {
        whenever(networkProtectionState.isEnabled()).thenReturn(true)

        testee.onStart(mockLifecycleOwner)
        testee.onCountrySelected("us")
        testee.onStop(mockLifecycleOwner)

        verify(networkProtectionState).clearVPNConfigurationAndRestart()
    }

    @Test
    fun whenNetPIsEnabledAndPreferredCityChangedThenRestart() = runTest {
        fakeRepository.setUserPreferredLocation(UserPreferredLocation(countryCode = "us", cityName = "El Segundo"))
        whenever(networkProtectionState.isEnabled()).thenReturn(true)

        testee.onStart(mockLifecycleOwner)
        fakeRepository.setUserPreferredLocation(UserPreferredLocation(countryCode = "us", cityName = "Newark"))
        testee.onStop(mockLifecycleOwner)

        verify(networkProtectionState).clearVPNConfigurationAndRestart()
    }

    @Test
    fun whenOnCreateIsCalledThenEmitImpressionPixels() {
        testee.onCreate(mockLifecycleOwner)

        verify(networkProtectionPixels).reportGeoswitchingScreenShown()
    }

    @Test
    fun whenNoCountriesAvailableThenEmitNoLocationsPixel() = runTest {
        val mockProvider = mock(NetpEgressServersProvider::class.java)
        testee = NetpGeoSwitchingViewModel(
            mockProvider,
            fakeRepository,
            coroutineRule.testDispatcherProvider,
            wgServerDebugProvider,
            networkProtectionState,
            networkProtectionPixels,
        )
        whenever(mockProvider.getServerLocations()).thenReturn(emptyList())

        testee.onStart(mockLifecycleOwner)

        verify(networkProtectionPixels).reportGeoswitchingNoLocations()
    }

    @Test
    fun whenNetpIsNotEnabledAndPreferredLocationChangedToNearestThenEmitPixelForNearest() = runTest {
        fakeRepository.setUserPreferredLocation(UserPreferredLocation(countryCode = "us", cityName = "El Segundo"))
        whenever(networkProtectionState.isEnabled()).thenReturn(false)
        testee.onStart(mockLifecycleOwner)

        fakeRepository.setUserPreferredLocation(UserPreferredLocation())
        testee.onStop(mockLifecycleOwner)

        verify(networkProtectionPixels).reportPreferredLocationSetToNearest()
    }

    @Test
    fun whenNetpIsEnabledAndPreferredLocationChangedToNearestThenEmitPixelForNearest() = runTest {
        fakeRepository.setUserPreferredLocation(UserPreferredLocation(countryCode = "us", cityName = "El Segundo"))
        whenever(networkProtectionState.isEnabled()).thenReturn(true)
        testee.onStart(mockLifecycleOwner)

        fakeRepository.setUserPreferredLocation(UserPreferredLocation())
        testee.onStop(mockLifecycleOwner)

        verify(networkProtectionPixels).reportPreferredLocationSetToNearest()
    }

    @Test
    fun whenNetpIsNotEnabledAndPreferredLocationChangedToCustomThenEmitPixelForCustom() = runTest {
        fakeRepository.setUserPreferredLocation(UserPreferredLocation())
        whenever(networkProtectionState.isEnabled()).thenReturn(false)
        testee.onStart(mockLifecycleOwner)

        fakeRepository.setUserPreferredLocation(UserPreferredLocation(countryCode = "us"))
        testee.onStop(mockLifecycleOwner)

        verify(networkProtectionPixels).reportPreferredLocationSetToCustom()
    }

    @Test
    fun whenNetpEnabledAndPreferredLocationChangedToCustomThenEmitPixelForCustom() = runTest {
        fakeRepository.setUserPreferredLocation(UserPreferredLocation())
        whenever(networkProtectionState.isEnabled()).thenReturn(true)
        testee.onStart(mockLifecycleOwner)

        fakeRepository.setUserPreferredLocation(UserPreferredLocation(countryCode = "us", cityName = "El Segundo"))
        testee.onStop(mockLifecycleOwner)

        verify(networkProtectionPixels).reportPreferredLocationSetToCustom()
    }

    @Test
    fun whenNetPIsEnabledButNoChangeInCustomPreferredLocationThenEmitNoPixels() = runTest {
        fakeRepository.setUserPreferredLocation(UserPreferredLocation(countryCode = "us"))
        whenever(networkProtectionState.isEnabled()).thenReturn(true)

        testee.onStart(mockLifecycleOwner)
        testee.onCountrySelected("us")
        testee.onStop(mockLifecycleOwner)

        verifyNoMoreInteractions(networkProtectionPixels)
    }

    @Test
    fun whenNetPIsEnabledButNoChangeInDefaultPreferredLocationThenEmitNoPixels() = runTest {
        whenever(networkProtectionState.isEnabled()).thenReturn(true)

        testee.onStart(mockLifecycleOwner)
        testee.onStop(mockLifecycleOwner)

        verifyNoMoreInteractions(networkProtectionPixels)
    }
}

class FakeNetPGeoswitchingRepository : NetPGeoswitchingRepository {
    private var _userPreferredCountry: String? = null
    private var _userPreferredCity: String? = null
    private var locations: List<NetPGeoswitchingLocation> = emptyList()

    override suspend fun getUserPreferredLocation(): UserPreferredLocation = UserPreferredLocation(_userPreferredCountry, _userPreferredCity)

    override suspend fun setUserPreferredLocation(userPreferredLocation: UserPreferredLocation) {
        _userPreferredCountry = userPreferredLocation.countryCode
        _userPreferredCity = userPreferredLocation.cityName
    }

    override fun getLocations(): List<NetPGeoswitchingLocation> = locations

    override fun getLocationsFlow(): Flow<List<NetPGeoswitchingLocation>> = TODO()

    override fun replaceLocations(locations: List<NetPGeoswitchingLocation>) {
        this.locations = locations
    }
}
