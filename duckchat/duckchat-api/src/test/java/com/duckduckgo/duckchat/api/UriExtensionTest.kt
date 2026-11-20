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

package com.duckduckgo.duckchat.api

import android.net.Uri
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class UriExtensionTest {

    private val duckChat: DuckChat = mock<DuckChat>().also {
        whenever(it.isDuckChatUrl(any())).thenAnswer { invocation -> invocation.getArgument<Uri>(0).host == "duck.ai" }
    }

    @Test
    fun whenChatHasIdThenItIsAConversation() {
        assertTrue("https://duck.ai/?chatID=abc".toUri().isDuckAiConversation(duckChat))
    }

    @Test
    fun whenSharedChatThenItIsAConversation() {
        assertTrue("https://duck.ai/share/abc".toUri().isDuckAiConversation(duckChat))
    }

    @Test
    fun whenShareLinkHasNoIdThenItIsNotAConversation() {
        assertFalse("https://duck.ai/share".toUri().isDuckAiConversation(duckChat))
    }

    @Test
    fun whenDuckAiHasNoChatThenItIsNotAConversation() {
        assertFalse("https://duck.ai/".toUri().isDuckAiConversation(duckChat))
        assertFalse("https://duck.ai/?chatID=".toUri().isDuckAiConversation(duckChat))
    }

    @Test
    fun whenNotDuckAiThenItIsNotAConversation() {
        assertFalse("https://example.com/share/abc".toUri().isDuckAiConversation(duckChat))
        assertFalse("https://example.com/?chatID=abc".toUri().isDuckAiConversation(duckChat))
    }
}
