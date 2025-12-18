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

import com.duckduckgo.app.browser.api.DuckAiChatDeletionListener
import com.duckduckgo.app.browser.weblocalstorage.Domains
import com.duckduckgo.app.browser.weblocalstorage.DuckDuckGoWebLocalStorageManager
import com.duckduckgo.app.browser.weblocalstorage.MatchingRegex
import com.duckduckgo.app.browser.weblocalstorage.WebLocalStorageSettings
import com.duckduckgo.app.browser.weblocalstorage.WebLocalStorageSettingsJsonParser
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteEntity
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteRepository
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.feature.toggles.api.Toggle
import dagger.Lazy
import kotlinx.coroutines.test.runTest
import org.iq80.leveldb.DB
import org.iq80.leveldb.DBIterator
import org.iq80.leveldb.impl.Iq80DBFactory.bytes
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DuckDuckGoWebLocalStorageManagerTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val mockDB: DB = mock()
    private val mockIterator: DBIterator = mock()
    private val mockDatabaseProvider: Lazy<DB> = mock()
    private val mockWebLocalStorageSettingsJsonParser: WebLocalStorageSettingsJsonParser = mock()
    private val mockAndroidBrowserConfigFeature: AndroidBrowserConfigFeature = mock()
    private val mockWebLocalStorageToggle: Toggle = mock()
    private val mockFireproofWebsiteRepository: FireproofWebsiteRepository = mock()
    private val mockSettingsDataStore: SettingsDataStore = mock()
    private val mockDuckAiChatDeletionListener: DuckAiChatDeletionListener = mock()

    private lateinit var testee: DuckDuckGoWebLocalStorageManager

    @Before
    fun setup() = runTest {
        whenever(mockDatabaseProvider.get()).thenReturn(mockDB)
        whenever(mockAndroidBrowserConfigFeature.webLocalStorage()).thenReturn(mockWebLocalStorageToggle)
        whenever(mockWebLocalStorageToggle.getSettings()).thenReturn("settings")
        whenever(mockSettingsDataStore.clearDuckAiData).thenReturn(false)

        val domains = Domains(list = listOf("duckduckgo.com", "duck.ai"))
        val matchingRegex = MatchingRegex(
            list = listOf(
                "^_https://([a-zA-Z0-9.-]+\\.)?{domain}\u0000\u0001.+$",
                "^META:https://([a-zA-Z0-9.-]+\\.)?{domain}$",
                "^METAACCESS:https://([a-zA-Z0-9.-]+\\.)?{domain}$",
            ),
        )
        val keysToDelete = com.duckduckgo.app.browser.weblocalstorage.KeysToDelete(list = listOf("chat-history", "ai-conversations"))
        val webLocalStorageSettings = WebLocalStorageSettings(domains = domains, keysToDelete = keysToDelete, matchingRegex = matchingRegex)
        whenever(mockWebLocalStorageSettingsJsonParser.parseJson("settings")).thenReturn(webLocalStorageSettings)

        testee = DuckDuckGoWebLocalStorageManager(
            mockDatabaseProvider,
            mockAndroidBrowserConfigFeature,
            mockWebLocalStorageSettingsJsonParser,
            mockFireproofWebsiteRepository,
            coroutineRule.testDispatcherProvider,
            mockSettingsDataStore,
            duckAiChatDeletionListeners = object : PluginPoint<DuckAiChatDeletionListener> {
                override fun getPlugins(): Collection<DuckAiChatDeletionListener> {
                    return listOf(mockDuckAiChatDeletionListener)
                }
            },
        )
    }

    @Test
    fun whenClearWebLocalStorageThenIteratorSeeksToFirst() = runTest {
        whenever(mockDB.iterator()).thenReturn(mockIterator)

        testee.clearWebLocalStorage()

        verify(mockIterator).seekToFirst()
    }

    @Test
    fun whenClearWebLocalStorageThenDeletesNonAllowedKeys() = runTest {
        val key1 = bytes("_https://example.com\u0000\u0001key1")
        val key2 = bytes("_https://duckduckgo.com\u0000\u0001key2")
        val entry1 = createMockDBEntry(key1)
        val entry2 = createMockDBEntry(key2)

        whenever(mockDB.iterator()).thenReturn(mockIterator)
        whenever(mockIterator.hasNext()).thenReturn(true, true, false)
        whenever(mockIterator.next()).thenReturn(entry1, entry2)

        testee.clearWebLocalStorage()

        verify(mockDB).delete(key1)
        verify(mockDB, never()).delete(key2)
    }

    @Test
    fun whenClearWebLocalStorageThenDoesNotDeleteKeysWithValidSubdomains() = runTest {
        val key1 = bytes("_https://subdomain.duckduckgo.com\u0000\u0001key1")
        val key2 = bytes("_https://another.subdomain.duckduckgo.com\u0000\u0001key2")

        val entry1 = createMockDBEntry(key1)
        val entry2 = createMockDBEntry(key2)

        whenever(mockDB.iterator()).thenReturn(mockIterator)
        whenever(mockIterator.hasNext()).thenReturn(true, true, false)
        whenever(mockIterator.next()).thenReturn(entry1, entry2)

        testee.clearWebLocalStorage()

        verify(mockDB, never()).delete(key1)
        verify(mockDB, never()).delete(key2)
    }

    @Test
    fun whenClearWebLocalStorageThenDeletesKeysWithPartialDomainMatch() = runTest {
        val key1 = bytes("_https://notduckduckgo.com\u0000\u0001key1")
        val key2 = bytes("_https://duckduckgo.something.com\u0000\u0001key2")

        val entry1 = createMockDBEntry(key1)
        val entry2 = createMockDBEntry(key2)

        whenever(mockDB.iterator()).thenReturn(mockIterator)
        whenever(mockIterator.hasNext()).thenReturn(true, true, false)
        whenever(mockIterator.next()).thenReturn(entry1, entry2)

        testee.clearWebLocalStorage()

        verify(mockDB).delete(key1)
        verify(mockDB).delete(key2)
    }

    @Test
    fun whenClearWebLocalStorageThenDeletesKeysWithInvalidFormat() = runTest {
        val key1 = bytes("malformed-key")
        val key2 = bytes("_https://duckduckgo.com")
        val key3 = bytes("_https://duckduckgo.com\u0000\u0001")

        val entry1 = createMockDBEntry(key1)
        val entry2 = createMockDBEntry(key2)
        val entry3 = createMockDBEntry(key3)

        whenever(mockDB.iterator()).thenReturn(mockIterator)
        whenever(mockIterator.hasNext()).thenReturn(true, true, true, false)
        whenever(mockIterator.next()).thenReturn(entry1, entry2, entry3)

        testee.clearWebLocalStorage()

        verify(mockDB).delete(key1)
        verify(mockDB).delete(key2)
        verify(mockDB).delete(key3)
    }

    @Test
    fun whenClearWebLocalStorageThenIteratorIsClosed() = runTest {
        whenever(mockDB.iterator()).thenReturn(mockIterator)
        whenever(mockIterator.hasNext()).thenReturn(false)

        testee.clearWebLocalStorage()

        verify(mockIterator).close()
    }

    @Test
    fun whenClearWebLocalStorageThenDoNotClearLocalStorageForFireproofedDomains() = runTest {
        whenever(mockFireproofWebsiteRepository.fireproofWebsitesSync()).thenReturn(listOf(FireproofWebsiteEntity("example.com")))

        val key1 = bytes("_https://example.com\u0000\u0001key1")
        val key2 = bytes("_https://foo.com\u0000\u0001key2")
        val entry1 = createMockDBEntry(key1)
        val entry2 = createMockDBEntry(key2)

        whenever(mockDB.iterator()).thenReturn(mockIterator)
        whenever(mockIterator.hasNext()).thenReturn(true, true, false)
        whenever(mockIterator.next()).thenReturn(entry1, entry2)

        testee.clearWebLocalStorage()

        verify(mockFireproofWebsiteRepository).fireproofWebsitesSync()

        verify(mockDB, never()).delete(key1)
        verify(mockDB).delete(key2)
    }

    @Test
    fun whenClearWebLocalStorageAndDuckAiDataDeletedThenListenersNotified() = runTest {
        whenever(mockSettingsDataStore.clearDuckAiData).thenReturn(true)

        val key = bytes("_https://duckduckgo.com\u0000\u0001chat-history")
        val entry = createMockDBEntry(key)

        whenever(mockDB.iterator()).thenReturn(mockIterator)
        whenever(mockIterator.hasNext()).thenReturn(true, false)
        whenever(mockIterator.next()).thenReturn(entry)

        testee.clearWebLocalStorage()

        verify(mockDuckAiChatDeletionListener).onDuckAiChatsDeleted()
    }

    @Test
    fun whenClearWebLocalStorageAndClearDuckAiDataSettingFalseThenListenersNotNotified() = runTest {
        whenever(mockSettingsDataStore.clearDuckAiData).thenReturn(false)

        val key = bytes("_https://duckduckgo.com\u0000\u0001chat-history")
        val entry = createMockDBEntry(key)

        whenever(mockDB.iterator()).thenReturn(mockIterator)
        whenever(mockIterator.hasNext()).thenReturn(true, false)
        whenever(mockIterator.next()).thenReturn(entry)

        testee.clearWebLocalStorage()

        verify(mockDuckAiChatDeletionListener, never()).onDuckAiChatsDeleted()
    }

    @Test
    fun whenClearWebLocalStorageWithShouldClearDataTrueThenDeletesNonAllowedKeys() = runTest {
        val key1 = bytes("_https://example.com\u0000\u0001key1")
        val key2 = bytes("_https://duckduckgo.com\u0000\u0001chat-history")
        val entry1 = createMockDBEntry(key1)
        val entry2 = createMockDBEntry(key2)

        whenever(mockDB.iterator()).thenReturn(mockIterator)
        whenever(mockIterator.hasNext()).thenReturn(true, true, false)
        whenever(mockIterator.next()).thenReturn(entry1, entry2)

        testee.clearWebLocalStorage(shouldClearBrowserData = true, shouldClearDuckAiData = false)

        verify(mockDB).delete(key1)
        verify(mockDB, never()).delete(key2)
    }

    @Test
    fun whenClearWebLocalStorageWithShouldClearDataFalseThenDoesNotDeleteNonAllowedKeys() = runTest {
        val key1 = bytes("_https://example.com\u0000\u0001key1")
        val key2 = bytes("_https://foo.com\u0000\u0001key2")
        val key3 = bytes("_https://duckduckgo.com\u0000\u0001chat-history")
        val entry1 = createMockDBEntry(key1)
        val entry2 = createMockDBEntry(key2)
        val entry3 = createMockDBEntry(key3)

        whenever(mockDB.iterator()).thenReturn(mockIterator)
        whenever(mockIterator.hasNext()).thenReturn(true, true, true, false)
        whenever(mockIterator.next()).thenReturn(entry1, entry2, entry3)

        testee.clearWebLocalStorage(shouldClearBrowserData = false, shouldClearDuckAiData = false)

        verify(mockDB, never()).delete(key1)
        verify(mockDB, never()).delete(key2)
        verify(mockDB, never()).delete(key3)
    }

    @Test
    fun whenClearWebLocalStorageWithShouldClearChatsTrueThenDeletesDuckDuckGoChatKeys() = runTest {
        val key1 = bytes("_https://duckduckgo.com\u0000\u0001chat-history")
        val key2 = bytes("_https://duckduckgo.com\u0000\u0001regular-key")
        val entry1 = createMockDBEntry(key1)
        val entry2 = createMockDBEntry(key2)

        whenever(mockDB.iterator()).thenReturn(mockIterator)
        whenever(mockIterator.hasNext()).thenReturn(true, true, false)
        whenever(mockIterator.next()).thenReturn(entry1, entry2)

        testee.clearWebLocalStorage(shouldClearBrowserData = false, shouldClearDuckAiData = true)

        verify(mockDB).delete(key1)
        verify(mockDB, never()).delete(key2)
    }

    @Test
    fun whenClearWebLocalStorageWithShouldClearChatsTrueThenDeletesDuckAiChatKeys() = runTest {
        val key1 = bytes("_https://duck.ai\u0000\u0001ai-conversations")
        val key2 = bytes("_https://duck.ai\u0000\u0001regular-key")
        val entry1 = createMockDBEntry(key1)
        val entry2 = createMockDBEntry(key2)

        whenever(mockDB.iterator()).thenReturn(mockIterator)
        whenever(mockIterator.hasNext()).thenReturn(true, true, false)
        whenever(mockIterator.next()).thenReturn(entry1, entry2)

        testee.clearWebLocalStorage(shouldClearBrowserData = false, shouldClearDuckAiData = true)

        verify(mockDB).delete(key1)
        verify(mockDB, never()).delete(key2)
    }

    @Test
    fun whenClearWebLocalStorageWithShouldClearChatsFalseThenDoesNotDeleteDuckDuckGoChatKeys() = runTest {
        val key1 = bytes("_https://duckduckgo.com\u0000\u0001chat-history")
        val key2 = bytes("_https://duckduckgo.com\u0000\u0001ai-conversations")
        val entry1 = createMockDBEntry(key1)
        val entry2 = createMockDBEntry(key2)

        whenever(mockDB.iterator()).thenReturn(mockIterator)
        whenever(mockIterator.hasNext()).thenReturn(true, true, false)
        whenever(mockIterator.next()).thenReturn(entry1, entry2)

        testee.clearWebLocalStorage(shouldClearBrowserData = false, shouldClearDuckAiData = false)

        verify(mockDB, never()).delete(key1)
        verify(mockDB, never()).delete(key2)
    }

    @Test
    fun whenClearWebLocalStorageWithBothTrueThenDeletesBothDataAndChatKeys() = runTest {
        val key1 = bytes("_https://example.com\u0000\u0001key1")
        val key2 = bytes("_https://duckduckgo.com\u0000\u0001chat-history")
        val key3 = bytes("_https://duckduckgo.com\u0000\u0001regular-key")
        val entry1 = createMockDBEntry(key1)
        val entry2 = createMockDBEntry(key2)
        val entry3 = createMockDBEntry(key3)

        whenever(mockDB.iterator()).thenReturn(mockIterator)
        whenever(mockIterator.hasNext()).thenReturn(true, true, true, false)
        whenever(mockIterator.next()).thenReturn(entry1, entry2, entry3)

        testee.clearWebLocalStorage(shouldClearBrowserData = true, shouldClearDuckAiData = true)

        verify(mockDB).delete(key1)
        verify(mockDB).delete(key2)
        verify(mockDB, never()).delete(key3)
    }

    @Test
    fun whenClearWebLocalStorageWithBothFalseThenDoesNotDeleteAnything() = runTest {
        val key1 = bytes("_https://example.com\u0000\u0001key1")
        val key2 = bytes("_https://duckduckgo.com\u0000\u0001chat-history")
        val entry1 = createMockDBEntry(key1)
        val entry2 = createMockDBEntry(key2)

        whenever(mockDB.iterator()).thenReturn(mockIterator)
        whenever(mockIterator.hasNext()).thenReturn(true, true, false)
        whenever(mockIterator.next()).thenReturn(entry1, entry2)

        testee.clearWebLocalStorage(shouldClearBrowserData = false, shouldClearDuckAiData = false)

        verify(mockDB, never()).delete(key1)
        verify(mockDB, never()).delete(key2)
    }

    @Test
    fun whenClearWebLocalStorageWithShouldClearDataTrueThenRespectsFireproofedDomains() = runTest {
        whenever(mockFireproofWebsiteRepository.fireproofWebsitesSync()).thenReturn(listOf(FireproofWebsiteEntity("example.com")))

        val key1 = bytes("_https://example.com\u0000\u0001key1")
        val key2 = bytes("_https://foo.com\u0000\u0001key2")
        val entry1 = createMockDBEntry(key1)
        val entry2 = createMockDBEntry(key2)

        whenever(mockDB.iterator()).thenReturn(mockIterator)
        whenever(mockIterator.hasNext()).thenReturn(true, true, false)
        whenever(mockIterator.next()).thenReturn(entry1, entry2)

        testee.clearWebLocalStorage(shouldClearBrowserData = true, shouldClearDuckAiData = false)

        verify(mockDB, never()).delete(key1)
        verify(mockDB).delete(key2)
    }

    @Test
    fun whenClearWebLocalStorageWithShouldClearChatsTrueThenOnlyDeletesChatKeysForDuckDuckGoDomains() = runTest {
        val key1 = bytes("_https://example.com\u0000\u0001chat-history")
        val key2 = bytes("_https://duckduckgo.com\u0000\u0001chat-history")
        val entry1 = createMockDBEntry(key1)
        val entry2 = createMockDBEntry(key2)

        whenever(mockDB.iterator()).thenReturn(mockIterator)
        whenever(mockIterator.hasNext()).thenReturn(true, true, false)
        whenever(mockIterator.next()).thenReturn(entry1, entry2)

        testee.clearWebLocalStorage(shouldClearBrowserData = false, shouldClearDuckAiData = true)

        verify(mockDB, never()).delete(key1)
        verify(mockDB).delete(key2)
    }

    @Test
    fun whenClearWebLocalStorageWithParametersThenIteratorSeeksToFirst() = runTest {
        whenever(mockDB.iterator()).thenReturn(mockIterator)

        testee.clearWebLocalStorage(shouldClearBrowserData = true, shouldClearDuckAiData = true)

        verify(mockIterator).seekToFirst()
    }

    @Test
    fun whenClearWebLocalStorageWithParametersThenIteratorIsClosed() = runTest {
        whenever(mockDB.iterator()).thenReturn(mockIterator)
        whenever(mockIterator.hasNext()).thenReturn(false)

        testee.clearWebLocalStorage(shouldClearBrowserData = true, shouldClearDuckAiData = false)

        verify(mockIterator).close()
    }

    @Test
    fun whenClearWebLocalStorageWithShouldClearDuckAiDataTrueAndDataDeletedThenListenersNotified() = runTest {
        val key = bytes("_https://duckduckgo.com\u0000\u0001chat-history")
        val entry = createMockDBEntry(key)

        whenever(mockDB.iterator()).thenReturn(mockIterator)
        whenever(mockIterator.hasNext()).thenReturn(true, false)
        whenever(mockIterator.next()).thenReturn(entry)

        testee.clearWebLocalStorage(shouldClearBrowserData = false, shouldClearDuckAiData = true)

        verify(mockDuckAiChatDeletionListener).onDuckAiChatsDeleted()
    }

    @Test
    fun whenClearWebLocalStorageWithShouldClearDuckAiDataFalseThenListenersNotNotified() = runTest {
        val key = bytes("_https://duckduckgo.com\u0000\u0001chat-history")
        val entry = createMockDBEntry(key)

        whenever(mockDB.iterator()).thenReturn(mockIterator)
        whenever(mockIterator.hasNext()).thenReturn(true, false)
        whenever(mockIterator.next()).thenReturn(entry)

        testee.clearWebLocalStorage(shouldClearBrowserData = false, shouldClearDuckAiData = false)

        verify(mockDuckAiChatDeletionListener, never()).onDuckAiChatsDeleted()
    }

    @Test
    fun whenClearWebLocalStorageWithShouldClearDuckAiDataTrueButNoDataDeletedThenListenersNotNotified() = runTest {
        val key = bytes("_https://duckduckgo.com\u0000\u0001regular-key") // Not a chat key
        val entry = createMockDBEntry(key)

        whenever(mockDB.iterator()).thenReturn(mockIterator)
        whenever(mockIterator.hasNext()).thenReturn(true, false)
        whenever(mockIterator.next()).thenReturn(entry)

        testee.clearWebLocalStorage(shouldClearBrowserData = false, shouldClearDuckAiData = true)

        verify(mockDuckAiChatDeletionListener, never()).onDuckAiChatsDeleted()
    }

    private fun createMockDBEntry(key: ByteArray): MutableMap.MutableEntry<ByteArray, ByteArray> {
        return object : MutableMap.MutableEntry<ByteArray, ByteArray> {
            override val key: ByteArray = key
            override val value: ByteArray = byteArrayOf()
            override fun setValue(newValue: ByteArray): ByteArray = byteArrayOf()
        }
    }
}
