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

package com.duckduckgo.duckchat.impl.widget

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.tabs.BrowserNav
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class DuckAiWidgetIntentsFillInTest {

    private val newChatUrl = "https://duckduckgo.com/?q=DuckDuckGo+AI+Chat&ia=chat&duckai=5"
    private val chatUrl = "https://duckduckgo.com/?q=DuckDuckGo+AI+Chat&ia=chat&duckai=5&chatID=abc"

    // Mirrors BrowserActivity.intent(): the URL and every launch extra are always written, even when empty.
    private val nav: BrowserNav = mock<BrowserNav>().also {
        whenever(it.openDuckChat(any(), any(), any(), anyOrNull(), any())).thenAnswer { call ->
            Intent()
                .putExtra("DUCK_CHAT_URL", call.getArgument<String>(2))
                .putExtra("NATIVE_INPUT_LAUNCH_ACTION_EXTRA", call.getArgument<Any?>(3)?.toString())
        }
    }
    private val intents = DuckAiWidgetIntents(nav, fakeGlobalActivityStarter())

    @Test
    fun whenChatRowFillInIsAppliedToTemplateThenChatUrlWins() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val template = shadowOf(intents.chatClickTemplate(context, 1, newChatUrl)).savedIntent

        template.fillIn(intents.chatFillInIntent(context, chatUrl), 0)

        assertEquals(chatUrl, template.getStringExtra("DUCK_CHAT_URL"))
    }
}
