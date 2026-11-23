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

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.browser.mode.SelectedTextSearch
import com.duckduckgo.app.pixels.toPixelLaunchSourceValue
import com.duckduckgo.app.tabs.Attachment
import org.junit.Assert.assertEquals
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
    fun whenOpenDuckChatWithMultipleFilesThenAllUrisInClipDataWithReadGrant() {
        val first = Uri.parse("content://test/1")
        val second = Uri.parse("content://test/2")

        val intent = testee.openDuckChat(
            context = context,
            hasSessionActive = false,
            duckChatUrl = "https://duck.ai",
            forceImageGeneration = false,
            forceLaunchContextual = false,
            attachments = listOf(Attachment.File(first), Attachment.File(second)),
        )

        val clipData = intent.clipData!!
        assertEquals(listOf(first, second), (0 until clipData.itemCount).map { clipData.getItemAt(it).uri })
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    @Test
    fun whenOpenDuckChatWithOnlyFilesThenLaunchSourceIsSelectedTextSearch() {
        val intent = testee.openDuckChat(
            context = context,
            hasSessionActive = false,
            duckChatUrl = "https://duck.ai",
            forceImageGeneration = false,
            forceLaunchContextual = false,
            attachments = listOf(Attachment.File(Uri.parse("content://test/1"))),
        )

        assertEquals(
            SelectedTextSearch.toPixelLaunchSourceValue(),
            intent.getStringExtra(BrowserActivity.LAUNCH_SOURCE_PIXEL_VALUE),
        )
    }
}
