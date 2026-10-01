/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.serp.logos.impl.ui

import android.annotation.SuppressLint
import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.serp.logos.impl.store.FavouriteSerpLogoDataStore
import com.duckduckgo.serp.logos.impl.ui.SerpEasterEggLogoViewModel.Command.CloseScreen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@SuppressLint("DenyListedApi")
@RunWith(JUnit4::class)
class SerpEasterEggLogoViewModelTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    private lateinit var fakeFavouriteSerpLogoDataStore: FakeFavouriteSerpLogoDataStore

    private lateinit var testee: SerpEasterEggLogoViewModel

    @Before
    fun setUp() {
        fakeFavouriteSerpLogoDataStore = FakeFavouriteSerpLogoDataStore()
    }

    private fun createViewModel(): SerpEasterEggLogoViewModel {
        return SerpEasterEggLogoViewModel(
            fakeFavouriteSerpLogoDataStore,
        )
    }

    @Test
    fun whenLogoUrlMatchesStoredFavouriteThenIsFavouriteIsTrue() = runTest {
        val logoUrl = "https://duckduckgo.com/assets/logo/easter-egg.png"
        fakeFavouriteSerpLogoDataStore.favouriteLogoValue = logoUrl
        testee = createViewModel()

        testee.setLogoUrl(logoUrl)

        val viewState = testee.viewState.first { it.logoUrl.isNotEmpty() }
        assertTrue(viewState.isFavourite)
        assertEquals(logoUrl, viewState.logoUrl)
    }

    @Test
    fun whenLogoUrlDiffersFromStoredFavouriteThenIsFavouriteIsFalse() = runTest {
        val logoUrl = "https://duckduckgo.com/assets/logo/easter-egg.png"
        val storedFavourite = "https://duckduckgo.com/assets/logo/different.png"
        fakeFavouriteSerpLogoDataStore.favouriteLogoValue = storedFavourite
        testee = createViewModel()

        testee.setLogoUrl(logoUrl)

        val viewState = testee.viewState.first { it.logoUrl.isNotEmpty() }
        assertFalse(viewState.isFavourite)
    }

    @Test
    fun whenNoFavouriteStoredThenIsFavouriteIsFalse() = runTest {
        val logoUrl = "https://duckduckgo.com/assets/logo/easter-egg.png"
        fakeFavouriteSerpLogoDataStore.favouriteLogoValue = null
        testee = createViewModel()

        testee.setLogoUrl(logoUrl)

        val viewState = testee.viewState.first { it.logoUrl.isNotEmpty() }
        assertFalse(viewState.isFavourite)
    }

    @Test
    fun whenFavouritePreferenceChangesThenIsFavouriteUpdates() = runTest {
        val logoUrl = "https://duckduckgo.com/assets/logo/easter-egg.png"
        testee = createViewModel()

        testee.setLogoUrl(logoUrl)

        assertFalse(testee.viewState.first { it.logoUrl == logoUrl }.isFavourite)
        fakeFavouriteSerpLogoDataStore.favouriteLogoValue = logoUrl
        assertTrue(testee.viewState.first { it.isFavourite }.isFavourite)
        fakeFavouriteSerpLogoDataStore.favouriteLogoValue = null
        assertFalse(testee.viewState.first { it.logoUrl == logoUrl && !it.isFavourite }.isFavourite)
    }

    @Test
    fun whenFavouriteButtonClickedAndNotFavouriteThenSetsFavourite() = runTest {
        val logoUrl = "https://duckduckgo.com/assets/logo/easter-egg.png"
        fakeFavouriteSerpLogoDataStore.favouriteLogoValue = null
        testee = createViewModel()

        testee.setLogoUrl(logoUrl)
        testee.onFavouriteButtonClicked()

        assertEquals(logoUrl, fakeFavouriteSerpLogoDataStore.favouriteLogoValue)
    }

    @Test
    fun whenFavouriteButtonClickedAndIsFavouriteThenClearsFavourite() = runTest {
        val logoUrl = "https://duckduckgo.com/assets/logo/easter-egg.png"
        fakeFavouriteSerpLogoDataStore.favouriteLogoValue = logoUrl
        testee = createViewModel()

        testee.setLogoUrl(logoUrl)
        testee.viewState.first { it.isFavourite }
        testee.onFavouriteButtonClicked()

        assertEquals(null, fakeFavouriteSerpLogoDataStore.favouriteLogoValue)
    }

    @Test
    fun whenBackgroundClickedThenCloseScreenCommandEmitted() = runTest {
        testee = createViewModel()

        testee.commands.test {
            testee.onBackgroundClicked()

            assertEquals(CloseScreen, awaitItem())
        }
    }

    @Test
    fun whenFavouriteButtonClickedAndNotFavouriteThenCloseScreenCommandEmitted() = runTest {
        val logoUrl = "https://duckduckgo.com/assets/logo/easter-egg.png"
        fakeFavouriteSerpLogoDataStore.favouriteLogoValue = null
        testee = createViewModel()

        testee.commands.test {
            testee.setLogoUrl(logoUrl)
            testee.onFavouriteButtonClicked()

            assertEquals(CloseScreen, awaitItem())
        }
    }

    @Test
    fun whenFavouriteButtonClickedAndIsFavouriteThenCloseScreenCommandEmitted() = runTest {
        val logoUrl = "https://duckduckgo.com/assets/logo/easter-egg.png"
        fakeFavouriteSerpLogoDataStore.favouriteLogoValue = logoUrl
        testee = createViewModel()

        testee.commands.test {
            testee.setLogoUrl(logoUrl)
            testee.onFavouriteButtonClicked()

            assertEquals(CloseScreen, awaitItem())
        }
    }
}

private class FakeFavouriteSerpLogoDataStore : FavouriteSerpLogoDataStore {

    private val _favouriteLogo = MutableStateFlow<String?>(null)

    var favouriteLogoValue: String?
        get() = _favouriteLogo.value
        set(value) {
            _favouriteLogo.value = value
        }

    override val favouriteSerpEasterEggLogoUrlFlow: Flow<String?> = _favouriteLogo

    override suspend fun setFavouriteLogo(url: String?) {
        _favouriteLogo.value = url
    }

    override suspend fun clearFavouriteLogo() {
        _favouriteLogo.value = null
    }
}
