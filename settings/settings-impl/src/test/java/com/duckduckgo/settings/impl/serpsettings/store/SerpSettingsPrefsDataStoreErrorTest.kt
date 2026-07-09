/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.settings.impl.serpsettings.store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.settings.impl.serpsettings.pixel.SerpSettingsPixelName
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.io.IOException

class SerpSettingsPrefsDataStoreErrorTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val pixel: Pixel = mock()

    private fun storeWith(dataStore: DataStore<Preferences>): SerpSettingsDataStore =
        SerpSettingsPrefsDataStore(store = dataStore, pixel = pixel)

    @Test
    fun whenReadThrowsThenReadErrorPixelFiredAndReturnsNull() = runTest {
        val throwingStore = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow { throw IOException("boom") }
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                throw IOException("unused")
        }

        val result = storeWith(throwingStore).getSerpSettings()

        assertNull(result)
        verify(pixel).fire(SerpSettingsPixelName.SERP_SETTINGS_KEYVALUE_STORE_READ_ERROR_COUNT)
        verify(pixel).fire(SerpSettingsPixelName.SERP_SETTINGS_KEYVALUE_STORE_READ_ERROR_DAILY, type = Pixel.PixelType.Daily())
    }

    @Test
    fun whenWriteThrowsThenWriteErrorPixelFired() = runTest {
        val throwingStore = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow { throw IOException("unused") }
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                throw IOException("boom")
        }

        storeWith(throwingStore).setSerpSettings("""{"kbe":"3"}""")

        verify(pixel).fire(SerpSettingsPixelName.SERP_SETTINGS_KEYVALUE_STORE_WRITE_ERROR_COUNT)
        verify(pixel).fire(SerpSettingsPixelName.SERP_SETTINGS_KEYVALUE_STORE_WRITE_ERROR_DAILY, type = Pixel.PixelType.Daily())
    }

    @Test
    fun whenObserveThrowsThenReadErrorPixelFiredAndEmitsNull() = runTest {
        val throwingStore = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow { throw IOException("boom") }
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                throw IOException("unused")
        }

        val emitted = storeWith(throwingStore).observeSerpSettings().first()

        assertNull(emitted)
        verify(pixel).fire(SerpSettingsPixelName.SERP_SETTINGS_KEYVALUE_STORE_READ_ERROR_COUNT)
        verify(pixel).fire(SerpSettingsPixelName.SERP_SETTINGS_KEYVALUE_STORE_READ_ERROR_DAILY, type = Pixel.PixelType.Daily())
    }

    @Test
    fun whenMultipleObserveCollectorsAndReadKeepsFailingThenReadErrorPixelFiredOnce() = runTest {
        val throwingStore = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow { throw IOException("boom") }
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                throw IOException("unused")
        }
        // Same store instance (as DI provides a singleton), multiple collectors: the read-error latch must
        // collapse the fan-out to a single count/daily pair.
        val store = storeWith(throwingStore)

        store.observeSerpSettings().first()
        store.observeSerpSettings().first()

        verify(pixel, times(1)).fire(SerpSettingsPixelName.SERP_SETTINGS_KEYVALUE_STORE_READ_ERROR_COUNT)
        verify(pixel, times(1)).fire(SerpSettingsPixelName.SERP_SETTINGS_KEYVALUE_STORE_READ_ERROR_DAILY, type = Pixel.PixelType.Daily())
    }
}
