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
import com.duckduckgo.app.tabs.DuckChatLaunch
import com.duckduckgo.app.tabs.DuckChatLaunchOrigin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        whenever(it.openDuckChat(any(), any(), anyOrNull(), any())).thenAnswer { call ->
            val fromWidget = (call.getArgument<DuckChatLaunch>(3) as? DuckChatLaunch.Open)?.origin == DuckChatLaunchOrigin.HOME_SCREEN_WIDGET
            Intent()
                .putExtra("DUCK_CHAT_URL", call.getArgument<String?>(2))
                .putExtra("LAUNCH_REQUIRES_REGULAR_MODE", fromWidget)
                .putExtra("DUCK_CHAT_ENTRY_POINT_EXTRA", if (fromWidget) "WIDGET_DUCK_AI" else null)
        }
    }
    private val intents = DuckAiWidgetIntents(nav)

    @Test
    fun whenChatRowFillInIsAppliedToTemplateThenChatUrlAndWidgetLaunchExtrasWin() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val template = shadowOf(intents.chatClickTemplate(context, 1, newChatUrl)).savedIntent

        template.fillIn(intents.chatFillInIntent(context, chatUrl), 0)

        assertEquals(chatUrl, template.getStringExtra("DUCK_CHAT_URL"))
        assertTrue(template.getBooleanExtra("LAUNCH_REQUIRES_REGULAR_MODE", false))
        assertEquals("WIDGET_DUCK_AI", template.getStringExtra("DUCK_CHAT_ENTRY_POINT_EXTRA"))
    }

    @Test
    fun whenChatClickTemplateIsBuiltThenItCarriesNoExtras() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        val template = shadowOf(intents.chatClickTemplate(context, 1, newChatUrl)).savedIntent

        assertNull(template.extras)
    }
}
