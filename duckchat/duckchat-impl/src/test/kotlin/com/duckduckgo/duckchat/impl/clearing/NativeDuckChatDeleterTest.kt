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

package com.duckduckgo.duckchat.impl.clearing

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.store.impl.DuckAiChatStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class NativeDuckChatDeleterTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val store: DuckAiChatStore = mock()
    private lateinit var deleter: NativeDuckChatDeleter

    @Before
    fun setup() {
        deleter = NativeDuckChatDeleter(store)
    }

    @Test
    fun `deleteChat returns true when store deletes successfully`() = runTest {
        whenever(store.deleteChat("chat-1")).thenReturn(true)
        assertTrue(deleter.deleteChat("chat-1"))
    }

    @Test
    fun `deleteChat returns false when chat not found`() = runTest {
        whenever(store.deleteChat("missing")).thenReturn(false)
        assertFalse(deleter.deleteChat("missing"))
    }

    @Test
    fun `deleteChat delegates to store`() = runTest {
        whenever(store.deleteChat("chat-1")).thenReturn(true)
        deleter.deleteChat("chat-1")
        verify(store).deleteChat("chat-1")
    }
}
