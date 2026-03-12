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

package com.duckduckgo.duckchat.impl.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import com.squareup.moshi.Moshi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class RealDuckChatContextualDataStoreTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private lateinit var testDataStore: DataStore<Preferences>
    private lateinit var testee: DuckChatContextualDataStore

    @Before
    fun setup() {
        testDataStore =
            PreferenceDataStoreFactory.create(
                scope = coroutineRule.testScope,
                produceFile = { context.preferencesDataStoreFile("duck_chat_context_store_${UUID.randomUUID()}") },
            )

        testee =
            RealDuckChatContextualDataStore(
                testDataStore,
                coroutineRule.testScope,
                coroutineRule.testDispatcherProvider,
                Moshi.Builder().build(),
            )
    }

    @Test
    fun whenPersistTabChatUrlThenItCanBeRetrieved() = runTest {
        val tabId = "tab-id"
        val url = "https://example.com"

        testee.persistTabChatUrl(tabId, url)

        assertEquals(url, testee.getTabChatUrl(tabId))
    }

    @Test
    fun whenPersistMultipleTabChatUrlsThenEachCanBeRetrieved() = runTest {
        val tabOne = "tab-1"
        val urlOne = "https://example.com/one"
        val tabTwo = "tab-2"
        val urlTwo = "https://example.com/two"

        testee.persistTabChatUrl(tabOne, urlOne)
        testee.persistTabChatUrl(tabTwo, urlTwo)

        assertEquals(urlOne, testee.getTabChatUrl(tabOne))
        assertEquals(urlTwo, testee.getTabChatUrl(tabTwo))
    }

    @Test
    fun whenClearTabChatUrlThenOnlyThatEntryIsRemoved() = runTest {
        val tabToClear = "tab-to-clear"
        val remainingTab = "tab-to-keep"

        testee.persistTabChatUrl(tabToClear, "https://example.com/clear")
        testee.persistTabChatUrl(remainingTab, "https://example.com/keep")

        testee.clearTabChatUrl(tabToClear)
        coroutineRule.testDispatcher.scheduler.advanceUntilIdle()

        assertNull(testee.getTabChatUrl(tabToClear))
        assertEquals("https://example.com/keep", testee.getTabChatUrl(remainingTab))
    }

    @Test
    fun whenPersistTabClosedTimestampThenItCanBeRetrieved() = runTest {
        val tabId = "tab-id"
        val timestamp = 123_456L

        testee.persistTabClosedTimestamp(tabId, timestamp)

        assertEquals(timestamp, testee.getTabClosedTimestamp(tabId))
    }

    @Test
    fun whenClearTabClosedTimestampThenOnlyThatEntryIsRemoved() = runTest {
        val tabToClear = "tab-to-clear"
        val remainingTab = "tab-to-keep"

        testee.persistTabClosedTimestamp(tabToClear, 111L)
        testee.persistTabClosedTimestamp(remainingTab, 222L)

        testee.clearTabClosedTimestamp(tabToClear)
        coroutineRule.testDispatcher.scheduler.advanceUntilIdle()

        assertNull(testee.getTabClosedTimestamp(tabToClear))
        assertEquals(222L, testee.getTabClosedTimestamp(remainingTab))
    }

    @Test
    fun whenClearAllThenAllEntriesAreRemoved() = runTest {
        testee.persistTabChatUrl("tab-1", "https://example.com/one")
        testee.persistTabChatUrl("tab-2", "https://example.com/two")
        testee.persistTabClosedTimestamp("tab-1", 111L)
        testee.persistTabClosedTimestamp("tab-2", 222L)

        testee.clearAll()
        coroutineRule.testDispatcher.scheduler.advanceUntilIdle()

        assertNull(testee.getTabChatUrl("tab-1"))
        assertNull(testee.getTabChatUrl("tab-2"))
        assertNull(testee.getTabClosedTimestamp("tab-1"))
        assertNull(testee.getTabClosedTimestamp("tab-2"))
    }
}
