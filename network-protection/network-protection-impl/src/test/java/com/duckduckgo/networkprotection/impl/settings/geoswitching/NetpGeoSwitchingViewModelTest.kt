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

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.turbine.test
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.networkprotection.impl.settings.geoswitching.GeoswitchingListItem.CountryItem
import com.duckduckgo.networkprotection.impl.settings.geoswitching.GeoswitchingListItem.DividerItem
import com.duckduckgo.networkprotection.impl.settings.geoswitching.GeoswitchingListItem.HeaderItem
import com.duckduckgo.networkprotection.impl.settings.geoswitching.GeoswitchingListItem.RecommendedItem
import com.duckduckgo.networkprotection.store.NetPGeoswitchingRepository
import com.duckduckgo.networkprotection.store.NetPGeoswitchingRepository.UserPreferredLocation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class NetpGeoSwitchingViewModelTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var testee: NetpGeoSwitchingViewModel
    private val fakeContentProvider = FakeGeoSwitchingContentProvider()
    private val fakeRepository = FakeNetPGeoswitchingRepository()

    @Before
    fun setUp() {
        testee = NetpGeoSwitchingViewModel(
            fakeContentProvider,
            fakeRepository,
            coroutineRule.testDispatcherProvider,
        )
    }

    @Test
    fun whenViewModelIsInitializedThenViewStateShouldEmitParsedList() = runTest {
        testee.initialize(context)
        testee.viewState().test {
            expectMostRecentItem().also {
                assertEquals(6, it.items.size)
                assertTrue(it.items[0] is HeaderItem)
                assertTrue(it.items[1] is RecommendedItem)
                assertTrue(it.items[2] is DividerItem)
                assertTrue(it.items[3] is HeaderItem)
                assertTrue(it.items[4] is CountryItem)
                assertEquals(
                    it.items[4],
                    CountryItem(
                        countryCode = "uk",
                        countryEmoji = "🇬🇧",
                        countryTitle = "UK",
                        countrySubtitle = null,
                        cities = emptyList(),
                    ),
                )
                assertTrue(it.items[5] is CountryItem)
                assertEquals(
                    it.items[5],
                    CountryItem(
                        countryCode = "us",
                        countryEmoji = "🇺🇸",
                        countryTitle = "United States",
                        countrySubtitle = "4 cities",
                        cities = listOf("Chicago", "El Segundo", "Newark", "Atlanta"),
                    ),
                )
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
    }

    @Test
    fun whenChosenPreferredCountryAndCityAreSameThenStoredDataShouldBeSame() = runTest {
        fakeRepository.setUserPreferredLocation(UserPreferredLocation("us", "Newark"))

        testee.onCountrySelected("us")

        fakeRepository.getUserPreferredLocation().let {
            assertEquals("us", it.countryCode)
            assertEquals("Newark", it.cityName)
        }
    }

    @Test
    fun whenNearestAvailableCountrySelectedThenStoredDataShouldBeNull() = runTest {
        fakeRepository.setUserPreferredLocation(UserPreferredLocation("us", "Newark"))

        testee.onNearestAvailableCountrySelected()

        fakeRepository.getUserPreferredLocation().let {
            assertNull(it.countryCode)
            assertNull(it.cityName)
        }
    }
}

class FakeNetPGeoswitchingRepository : NetPGeoswitchingRepository {
    private var _userPreferredCountry: String? = null
    private var _userPreferredCity: String? = null
    override suspend fun getUserPreferredLocation(): UserPreferredLocation = UserPreferredLocation(_userPreferredCountry, _userPreferredCity)

    override suspend fun setUserPreferredLocation(userPreferredLocation: UserPreferredLocation) {
        _userPreferredCountry = userPreferredLocation.countryCode
        _userPreferredCity = userPreferredLocation.cityName
    }
}
