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

package com.duckduckgo.serp.logos.impl.store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class FavouriteSerpEasterEggLogoPrefsDataStoreTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    private val temporaryFolder = TemporaryFolder.builder().assureDeletion().build().also { it.create() }

    private val testDataStore: DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = coroutineTestRule.testScope,
            produceFile = { temporaryFolder.newFile("test_favourite_serp_logo.preferences_pb") },
        )

    private val testee = FavouriteSerpLogoPrefsDataStore(testDataStore)

    @Test
    fun whenNoFavouriteSetThenFavouriteLogoIsNull() = runTest {
        testee.favouriteSerpEasterEggLogoUrlFlow.test {
            assertNull(awaitItem())
        }
    }

    @Test
    fun whenFavouriteSetThenFavouriteLogoReturnsUrl() = runTest {
        val expectedUrl = "https://duckduckgo.com/assets/logo/easter-egg.png"

        testee.favouriteSerpEasterEggLogoUrlFlow.test {
            assertNull(awaitItem())

            testee.setFavouriteLogo(expectedUrl)

            assertEquals(expectedUrl, awaitItem())
        }
    }

    @Test
    fun whenFavouriteSetToNullThenFavouriteLogoIsNull() = runTest {
        testee.favouriteSerpEasterEggLogoUrlFlow.test {
            assertNull(awaitItem())

            testee.setFavouriteLogo("https://duckduckgo.com/assets/logo/easter-egg.png")
            awaitItem()

            testee.setFavouriteLogo(null)

            assertNull(awaitItem())
        }
    }

    @Test
    fun whenFavouriteClearedThenFavouriteLogoIsNull() = runTest {
        testee.favouriteSerpEasterEggLogoUrlFlow.test {
            assertNull(awaitItem())

            testee.setFavouriteLogo("https://duckduckgo.com/assets/logo/easter-egg.png")
            awaitItem()

            testee.clearFavouriteLogo()

            assertNull(awaitItem())
        }
    }

    @Test
    fun whenFavouriteUpdatedThenFavouriteLogoReturnsNewUrl() = runTest {
        val firstUrl = "https://duckduckgo.com/assets/logo/first.png"
        val secondUrl = "https://duckduckgo.com/assets/logo/second.png"

        testee.favouriteSerpEasterEggLogoUrlFlow.test {
            assertNull(awaitItem())

            testee.setFavouriteLogo(firstUrl)
            assertEquals(firstUrl, awaitItem())

            testee.setFavouriteLogo(secondUrl)
            assertEquals(secondUrl, awaitItem())
        }
    }
}
