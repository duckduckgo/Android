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

package com.duckduckgo.app.browser.nativeinput

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.browser.nativeinput.DuckAiChatUrlType.ExistingChat
import com.duckduckgo.app.browser.nativeinput.DuckAiChatUrlType.NewChat
import com.duckduckgo.app.browser.nativeinput.DuckAiChatUrlType.NotDuckAi
import com.duckduckgo.app.browser.nativeinput.DuckAiChatUrlType.OtherDuckAiPage
import com.duckduckgo.app.browser.nativeinput.DuckAiChatUrlType.PromptInProgress
import com.duckduckgo.duckchat.api.DuckChat
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class NewDuckAiChatUrlTest {
    private val duckChat: DuckChat = mock()

    @Before
    fun setUp() {
        whenever(duckChat.isDuckChatUrl(any<Uri>())).thenAnswer { it.getArgument<Uri>(0).host == "duck.ai" }
    }

    @Test
    fun whenNewChatThenNewChat() {
        assertUrlType(NewChat, "https://duck.ai/chat?native-input=true")
        assertUrlType(NewChat, "https://duck.ai/")
        assertUrlType(NewChat, "https://duck.ai/chat/")
    }

    @Test
    fun whenChatIdIsPresentThenExistingChat() {
        assertUrlType(ExistingChat, "https://duck.ai/chat?chatID=existing")
    }

    @Test
    fun whenChatIdIsBlankThenNewChat() {
        assertUrlType(NewChat, "https://duck.ai/chat?chatID=")
    }

    @Test
    fun whenPromptIsInProgressThenPromptInProgress() {
        assertUrlType(PromptInProgress, "https://duck.ai/chat?prompt=1")
        assertUrlType(NewChat, "https://duck.ai/chat?prompt=0")
    }

    @Test
    fun whenVoiceModeThenOtherDuckAiPage() {
        assertUrlType(OtherDuckAiPage, "https://duck.ai/chat?mode=voice-mode")
    }

    @Test
    fun whenSettingsOpenThenOtherDuckAiPage() {
        assertUrlType(OtherDuckAiPage, "https://duck.ai/chat?settings=open")
    }

    @Test
    fun whenOtherDuckAiPageThenOtherDuckAiPage() {
        assertUrlType(OtherDuckAiPage, "https://duck.ai/settings")
        assertUrlType(OtherDuckAiPage, "https://duck.ai/about")
    }

    @Test
    fun whenNotDuckAiUrlThenNotDuckAi() {
        assertUrlType(NotDuckAi, "https://example.com/")
        assertUrlType(NotDuckAi, null)
        assertUrlType(NotDuckAi, "")
        assertUrlType(NotDuckAi, "invalid")
    }

    private fun assertUrlType(expected: DuckAiChatUrlType, rawUrl: String?) {
        assertEquals(rawUrl, expected, classifyDuckAiChatUrl(rawUrl, duckChat))
    }
}
