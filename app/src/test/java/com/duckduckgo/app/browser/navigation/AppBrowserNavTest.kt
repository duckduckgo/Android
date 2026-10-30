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

package com.duckduckgo.app.browser.navigation

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.tabs.NativeInputLaunchAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppBrowserNavTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val testee = AppBrowserNav()

    @Test
    fun whenOpenInNewTabWithSourceTabIdThenIntentCarriesSourceTab() {
        val intent = testee.openInNewTab(context, "https://example.com", sourceTabId = "tab-123")

        assertEquals("tab-123", intent.getStringExtra(BrowserActivity.SOURCE_TAB_ID_EXTRA))
    }

    @Test
    fun whenOpenInNewTabWithoutSourceTabIdThenIntentHasNoSourceTab() {
        val intent = testee.openInNewTab(context, "https://example.com")

        assertNull(intent.getStringExtra(BrowserActivity.SOURCE_TAB_ID_EXTRA))
    }

    @Test
    fun whenOpenDuckChatWithImageGenerationThenIntentForcesImageGeneration() {
        val intent = testee.openDuckChat(context, duckChatUrl = DUCK_AI_URL, launchAction = NativeInputLaunchAction.IMAGE_GENERATION)

        assertTrue(intent.getBooleanExtra("OPEN_DUCK_CHAT_EXTRA", false))
        assertEquals(DUCK_AI_URL, intent.getStringExtra("DUCK_CHAT_URL"))
        assertTrue(intent.getBooleanExtra("DUCK_CHAT_FORCE_IMAGE_GENERATION", false))
    }

    @Test
    fun whenOpenDuckChatWithoutLaunchActionThenIntentDoesNotForceImageGeneration() {
        val intent = testee.openDuckChat(context, duckChatUrl = DUCK_AI_URL)

        assertFalse(intent.getBooleanExtra("DUCK_CHAT_FORCE_IMAGE_GENERATION", false))
    }

    private companion object {
        const val DUCK_AI_URL = "https://duck.ai/"
    }
}
