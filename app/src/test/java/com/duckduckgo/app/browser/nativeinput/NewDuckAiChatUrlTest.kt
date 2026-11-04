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
import com.duckduckgo.duckchat.api.DuckChat
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    fun whenNewChatThenEligible() {
        assertTrue(isNewDuckAiChat("https://duck.ai/chat?native-input=true", duckChat))
        assertTrue(isNewDuckAiChat("https://duck.ai/", duckChat))
    }

    @Test
    fun whenChatIdIsPresentThenIneligible() {
        assertFalse(isNewDuckAiChat("https://duck.ai/chat?chatID=existing", duckChat))
    }

    @Test
    fun whenChatIdIsBlankThenEligible() {
        assertTrue(isNewDuckAiChat("https://duck.ai/chat?chatID=", duckChat))
    }

    @Test
    fun whenPromptIsInProgressThenIneligible() {
        assertFalse(isNewDuckAiChat("https://duck.ai/chat?prompt=1", duckChat))
        assertTrue(isNewDuckAiChat("https://duck.ai/chat?prompt=0", duckChat))
    }

    @Test
    fun whenVoiceModeThenIneligible() {
        assertFalse(isNewDuckAiChat("https://duck.ai/chat?mode=voice-mode", duckChat))
    }

    @Test
    fun whenSettingsOpenThenIneligible() {
        assertFalse(isNewDuckAiChat("https://duck.ai/chat?settings=open", duckChat))
    }

    @Test
    fun whenOtherDuckAiPageThenIneligible() {
        assertFalse(isNewDuckAiChat("https://duck.ai/settings", duckChat))
        assertFalse(isNewDuckAiChat("https://duck.ai/about", duckChat))
        assertTrue(isNewDuckAiChat("https://duck.ai/chat/", duckChat))
    }

    @Test
    fun whenNotDuckAiUrlThenIneligible() {
        assertFalse(isNewDuckAiChat("https://example.com/", duckChat))
        assertFalse(isNewDuckAiChat(null, duckChat))
        assertFalse(isNewDuckAiChat("", duckChat))
        assertFalse(isNewDuckAiChat("invalid", duckChat))
    }
}
