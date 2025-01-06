/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.browser

import com.duckduckgo.app.browser.localstorage.Domains
import com.duckduckgo.app.browser.localstorage.DuckDuckGoWebLocalStorageManager
import com.duckduckgo.app.browser.localstorage.LocalStorageSettings
import com.duckduckgo.app.browser.localstorage.LocalStorageSettingsJsonParser
import com.duckduckgo.app.browser.localstorage.MatchingRegex
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.feature.toggles.api.Toggle
import javax.inject.Provider
import kotlinx.coroutines.test.runTest
import org.iq80.leveldb.DB
import org.iq80.leveldb.DBIterator
import org.iq80.leveldb.impl.Iq80DBFactory.bytes
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DuckDuckGoWebLocalStorageManagerTest {

    private val mockDB: DB = mock()
    private val mockIterator: DBIterator = mock()
    private val mockDatabaseProvider: Provider<DB> = mock()
    private val mockLocalStorageSettingsJsonParser: LocalStorageSettingsJsonParser = mock()
    private val mockAndroidBrowserConfigFeature: AndroidBrowserConfigFeature = mock()
    private val mockToggle: Toggle = mock()

    private val testee = DuckDuckGoWebLocalStorageManager(mockDatabaseProvider, mockAndroidBrowserConfigFeature, mockLocalStorageSettingsJsonParser)

    @Before
    fun setup() = runTest {
        whenever(mockDatabaseProvider.get()).thenReturn(mockDB)
        whenever(mockAndroidBrowserConfigFeature.webLocalStorage()).thenReturn(mockToggle)
        whenever(mockToggle.getSettings()).thenReturn("settings")

        val domains = Domains(list = listOf("duckduckgo.com"))
        val matchingRegex = MatchingRegex(
            list = listOf(
                "^_https://([a-zA-Z0-9.-]+\\.)?{domain}\u0000\u0001.+$",
                "^META:https://([a-zA-Z0-9.-]+\\.)?{domain}$",
                "^METAACCESS:https://([a-zA-Z0-9.-]+\\.)?{domain}$",
            ),
        )
        val localStorageSettings = LocalStorageSettings(domains = domains, matchingRegex = matchingRegex)
        whenever(mockLocalStorageSettingsJsonParser.parseJson("settings")).thenReturn(localStorageSettings)
    }

    @Test
    fun whenClearLocalStorageThenIteratorSeeksToFirst() {
        whenever(mockDB.iterator()).thenReturn(mockIterator)

        testee.clearLocalStorage()

        verify(mockIterator).seekToFirst()
    }

    @Test
    fun whenClearLocalStorageThenDeletesNonAllowedKeys() {
        val key1 = bytes("_https://example.com\u0000\u0001key1")
        val key2 = bytes("_https://duckduckgo.com\u0000\u0001key2")
        val entry1 = createMockDBEntry(key1)
        val entry2 = createMockDBEntry(key2)

        whenever(mockDB.iterator()).thenReturn(mockIterator)
        whenever(mockIterator.hasNext()).thenReturn(true, true, false)
        whenever(mockIterator.next()).thenReturn(entry1, entry2)

        testee.clearLocalStorage()

        verify(mockDB).delete(key1)
        verify(mockDB, never()).delete(key2)
    }

    @Test
    fun whenClearLocalStorageThenDoesNotDeleteKeysWithValidSubdomains() {
        val key1 = bytes("_https://subdomain.duckduckgo.com\u0000\u0001key1")
        val key2 = bytes("_https://another.subdomain.duckduckgo.com\u0000\u0001key2")

        val entry1 = createMockDBEntry(key1)
        val entry2 = createMockDBEntry(key2)

        whenever(mockDB.iterator()).thenReturn(mockIterator)
        whenever(mockIterator.hasNext()).thenReturn(true, true, false)
        whenever(mockIterator.next()).thenReturn(entry1, entry2)

        testee.clearLocalStorage()

        verify(mockDB, never()).delete(key1)
        verify(mockDB, never()).delete(key2)
    }

    @Test
    fun whenClearLocalStorageThenDeletesKeysWithPartialDomainMatch() {
        val key1 = bytes("_https://notduckduckgo.com\u0000\u0001key1")
        val key2 = bytes("_https://duckduckgo.something.com\u0000\u0001key2")

        val entry1 = createMockDBEntry(key1)
        val entry2 = createMockDBEntry(key2)

        whenever(mockDB.iterator()).thenReturn(mockIterator)
        whenever(mockIterator.hasNext()).thenReturn(true, true, false)
        whenever(mockIterator.next()).thenReturn(entry1, entry2)

        testee.clearLocalStorage()

        verify(mockDB).delete(key1)
        verify(mockDB).delete(key2)
    }

    @Test
    fun whenClearLocalStorageThenDeletesKeysWithInvalidFormat() {
        val key1 = bytes("malformed-key")
        val key2 = bytes("_https://duckduckgo.com")
        val key3 = bytes("_https://duckduckgo.com\u0000\u0001")

        val entry1 = createMockDBEntry(key1)
        val entry2 = createMockDBEntry(key2)
        val entry3 = createMockDBEntry(key3)

        whenever(mockDB.iterator()).thenReturn(mockIterator)
        whenever(mockIterator.hasNext()).thenReturn(true, true, true, false)
        whenever(mockIterator.next()).thenReturn(entry1, entry2, entry3)

        testee.clearLocalStorage()

        verify(mockDB).delete(key1)
        verify(mockDB).delete(key2)
        verify(mockDB).delete(key3)
    }

    @Test
    fun whenClearLocalStorageThenDBIsClosed() {
        whenever(mockDB.iterator()).thenReturn(mockIterator)
        whenever(mockIterator.hasNext()).thenReturn(false)

        testee.clearLocalStorage()

        verify(mockDB).close()
    }

    private fun createMockDBEntry(key: ByteArray): MutableMap.MutableEntry<ByteArray, ByteArray> {
        return object : MutableMap.MutableEntry<ByteArray, ByteArray> {
            override val key: ByteArray = key
            override val value: ByteArray = byteArrayOf()
            override fun setValue(newValue: ByteArray): ByteArray = byteArrayOf()
        }
    }
}
