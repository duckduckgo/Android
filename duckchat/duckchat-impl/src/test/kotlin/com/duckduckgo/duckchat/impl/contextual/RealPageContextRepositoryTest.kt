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

package com.duckduckgo.duckchat.impl.contextual

import app.cash.turbine.test
import com.duckduckgo.duckchat.impl.store.DuckChatDataStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class RealPageContextRepositoryTest {

    private val updates = MutableSharedFlow<PageContextData?>(extraBufferCapacity = 1)
    private val dataStore: DuckChatDataStore = mock()
    private lateinit var repository: RealPageContextRepository

    @Before
    fun setup() = runTest {
        whenever(dataStore.observeDuckChatPageContext()).thenReturn(updates)
        doAnswer { invocation ->
            val tabId = invocation.getArgument<String>(0)
            val serialized = invocation.getArgument<String>(1)
            updates.tryEmit(PageContextData(tabId, serialized, System.currentTimeMillis(), isCleared = false))
            Unit
        }.whenever(dataStore).setDuckChatPageContext(any(), any())

        doAnswer { invocation ->
            val tabId = invocation.getArgument<String>(0)
            updates.tryEmit(PageContextData(tabId, "", System.currentTimeMillis(), isCleared = true))
            Unit
        }.whenever(dataStore).clearDuckChatPageContext(any())

        repository = RealPageContextRepository(dataStore)
    }

    @Test
    fun flowEmitsUpdatesForTab() = runTest {
        repository.getPageContext("tab-1").test {
            repository.update("tab-1", "data-1")
            val updated = expectMostRecentItem()

            assertNotNull(updated)
            assertEquals("data-1", updated!!.serializedPageData)
            assertEquals("tab-1", updated.tabId)
            assertFalse(updated.isCleared)
        }
    }

    @Test
    fun flowEmitsNullAfterClear() = runTest {
        repository.getPageContext("tab-1").test {
            repository.update("tab-1", "data-1")
            repository.clear("tab-1")

            val cleared = expectMostRecentItem()

            assertTrue(cleared!!.isCleared)
            assertEquals("tab-1", cleared.tabId)
            assertEquals(true, cleared.isCleared)
        }
    }

    @Test
    fun flowIsolationAcrossTabs() = runTest {
        repository.getPageContext("tab-1").test {
            repository.update("tab-1", "data-1")
            repository.update("tab-2", "data-2")

            val updated = expectMostRecentItem()

            assertEquals("data-2", updated!!.serializedPageData)
        }
    }
}
