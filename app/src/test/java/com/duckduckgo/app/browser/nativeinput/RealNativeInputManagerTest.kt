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
import com.duckduckgo.app.browser.omnibar.QueryUrlPredictor
import com.duckduckgo.duckchat.api.DuckAiFeatureState
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.voice.api.VoiceSearchAvailability
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class RealNativeInputManagerTest {

    private val duckChat: DuckChat = mock()
    private val animator: NativeInputAnimator = mock()
    private val voiceSearchAvailability: VoiceSearchAvailability = mock()
    private val globalActivityStarter: GlobalActivityStarter = mock()
    private val queryUrlPredictor: QueryUrlPredictor = mock()
    private val duckAiFeatureState: DuckAiFeatureState = mock()

    private lateinit var testee: RealNativeInputManager

    @Before
    fun setUp() {
        testee = RealNativeInputManager(
            duckChat,
            animator,
            voiceSearchAvailability,
            globalActivityStarter,
            queryUrlPredictor,
            duckAiFeatureState,
        )
    }

    @Test
    fun whenUrlIsNullThenIsExistingDuckAiChatFalse() {
        assertFalse(testee.isExistingDuckAiChat(null))
    }

    @Test
    fun whenUrlIsBlankThenIsExistingDuckAiChatFalse() {
        assertFalse(testee.isExistingDuckAiChat(""))
        assertFalse(testee.isExistingDuckAiChat("   "))
    }

    @Test
    fun whenUrlIsNotDuckAiThenIsExistingDuckAiChatFalse() {
        whenever(duckChat.isDuckChatUrl(any())).thenReturn(false)

        assertFalse(testee.isExistingDuckAiChat("https://example.com/?chatID=abcd"))
    }

    @Test
    fun whenDuckAiUrlWithoutChatIdThenIsExistingDuckAiChatFalse() {
        whenever(duckChat.isDuckChatUrl(any())).thenReturn(true)

        assertFalse(testee.isExistingDuckAiChat("https://duck.ai/"))
    }

    @Test
    fun whenDuckAiUrlWithBlankChatIdThenIsExistingDuckAiChatFalse() {
        whenever(duckChat.isDuckChatUrl(any())).thenReturn(true)

        assertFalse(testee.isExistingDuckAiChat("https://duck.ai/?chatID="))
    }

    @Test
    fun whenDuckAiUrlWithChatIdThenIsExistingDuckAiChatTrue() {
        whenever(duckChat.isDuckChatUrl(any())).thenReturn(true)

        assertTrue(testee.isExistingDuckAiChat("https://duck.ai/?chatID=abc-123"))
    }

    @Test
    fun whenIsDuckChatUrlChecksParsedUriThenCheckedWithSameUri() {
        whenever(duckChat.isDuckChatUrl(any())).thenReturn(true)
        val raw = "https://duck.ai/chat?chatID=xyz"

        testee.isExistingDuckAiChat(raw)

        verify(duckChat).isDuckChatUrl(Uri.parse(raw))
    }
}
