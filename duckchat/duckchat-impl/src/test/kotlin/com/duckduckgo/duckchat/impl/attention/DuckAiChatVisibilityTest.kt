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

package com.duckduckgo.duckchat.impl.attention

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.impl.attention.ChatVisibilityEvent.AppClosed
import com.duckduckgo.duckchat.impl.attention.ChatVisibilityEvent.AppOpened
import com.duckduckgo.duckchat.impl.attention.ChatVisibilityEvent.LaunchLandingResolved
import com.duckduckgo.duckchat.impl.attention.ChatVisibilityEvent.SelectedTabChanged
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class DuckAiChatVisibilityTest {

    private val duckChat: DuckChat = mock()

    private lateinit var chatVisibility: DuckAiChatVisibility

    @Before
    fun setup() {
        whenever(duckChat.isDuckChatUrl(any())).thenAnswer { invocation ->
            (invocation.arguments[0] as Uri).toString().startsWith(DUCK_AI_URL)
        }
        chatVisibility = DuckAiChatVisibility(duckChat)
    }

    private fun openApp() {
        chatVisibility.update(AppOpened)
        chatVisibility.update(LaunchLandingResolved(tabId = null, url = null))
    }

    private fun chat(tabId: String, chatId: String) = SelectedTabChanged(tabId, "$DUCK_AI_URL?chatID=$chatId")

    private fun otherSite(tabId: String) = SelectedTabChanged(tabId, OTHER_URL)

    @Test
    fun `onScreen is null until the app has opened and resolved its launch`() {
        chatVisibility.update(chat("t1", "A"))
        assertNull(chatVisibility.onScreen())
        assertNull(chatVisibility.selectedTabId)

        openApp()
        chatVisibility.update(chat("t1", "A"))

        assertEquals(OnScreen("t1", "A"), chatVisibility.onScreen())
    }

    @Test
    fun `onScreen is null when the selected tab is not Duck ai`() {
        openApp()

        chatVisibility.update(otherSite("t1"))

        assertNull(chatVisibility.onScreen())
    }

    @Test
    fun `landing on a non Duck ai tab clears a Duck ai tab selected before the app closed`() {
        openApp()
        chatVisibility.update(chat("t1", "A"))
        chatVisibility.update(AppClosed)

        openApp()

        assertNull(chatVisibility.onScreen())
    }

    @Test
    fun `selectedTabId is the tab selected after the event`() {
        openApp()
        chatVisibility.update(chat("t1", "A"))

        chatVisibility.update(otherSite("t2"))
        assertEquals("t2", chatVisibility.selectedTabId)

        chatVisibility.update(otherSite("t1"))
        assertEquals("t1", chatVisibility.selectedTabId)
    }

    @Test
    fun `update reports a change of tab or of what the tab shows`() {
        openApp()

        assertTrue(chatVisibility.update(chat("t1", "A")))
        assertFalse(chatVisibility.update(chat("t1", "A")))
        assertTrue(chatVisibility.update(chat("t1", "B")))
        assertTrue(chatVisibility.update(otherSite("t1")))
        assertTrue(chatVisibility.update(otherSite("t2")))
        assertFalse(chatVisibility.update(AppClosed))
    }

    private companion object {
        const val DUCK_AI_URL = "https://duck.ai/"
        const val OTHER_URL = "https://example.com/"
    }
}
