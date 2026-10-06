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

    private val duckChat: DuckChat = mock<DuckChat>().apply {
        whenever(isDuckChatUrl(any())).thenAnswer { (it.arguments[0] as android.net.Uri).host == "duck.ai" }
    }

    @Test
    fun `when url is a bare new chat link then is new duck chat url`() {
        assertTrue(NEW_CHAT_URL.toUri().isNewDuckChatUrl(duckChat))
    }

    @Test
    fun `when url is not a duck chat url then is not new duck chat url`() {
        assertFalse("https://example.com/chat".toUri().isNewDuckChatUrl(duckChat))
    }

    @Test
    fun `when url carries a prompt then is not new duck chat url`() {
        assertFalse("$NEW_CHAT_URL&q=hello".toUri().isNewDuckChatUrl(duckChat))
    }

    @Test
    fun `when url carries voice mode then is not new duck chat url`() {
        assertFalse("$NEW_CHAT_URL&mode=voice-mode".toUri().isNewDuckChatUrl(duckChat))
    }

    @Test
    fun `when url carries a chat id then is not new duck chat url`() {
        assertFalse("$NEW_CHAT_URL&chatID=abc123".toUri().isNewDuckChatUrl(duckChat))
    }

    @Test
    fun `when prompt and chat id are blank then is new duck chat url`() {
        assertTrue("$NEW_CHAT_URL&q=&chatID=".toUri().isNewDuckChatUrl(duckChat))
    }

    companion object {
        private const val NEW_CHAT_URL = "https://duck.ai/chat?native-input=true&duckai=5"
    }
}
