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

package com.duckduckgo.app.browser.tabs.adapter

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.browser.tabs.adapter.TabSwitcherItemDiffCallback.Companion.DIFF_KEY_TITLE
import com.duckduckgo.app.browser.tabs.adapter.TabSwitcherItemDiffCallback.Companion.DIFF_KEY_URL
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.ui.TabSwitcherItem.Tab.NormalTab
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TabSwitcherItemDiffCallbackTest {

    private val callback = TabSwitcherItemDiffCallback(isDragging = { false })

    @Test
    fun `when title transitions from non-null to null then payload contains DIFF_KEY_TITLE with null value`() {
        // Reproduces the bug precondition: pageChanged() persists (newUrl, null) before titleReceived()
        // fires, so the differ sees a tab whose title goes from "Some Title" to null.
        val oldTab = NormalTab(
            entity = TabEntity(tabId = "1", url = "https://huel.com/help", title = "How can we help? | Huel ES", position = 0),
            isActive = false,
        )
        val newTab = NormalTab(
            entity = TabEntity(tabId = "1", url = "https://clicks.easyjet.com/redirect", title = null, position = 0),
            isActive = false,
        )

        val bundle = callback.getChangePayload(oldTab, newTab)

        assertTrue("payload should mark title as changed", bundle!!.containsKey(DIFF_KEY_TITLE))
        assertNull("payload's title value is null - this is what caused the original bug", bundle.getString(DIFF_KEY_TITLE))
        assertTrue("payload should also mark url as changed", bundle.containsKey(DIFF_KEY_URL))
    }

    @Test
    fun `when only title changes to non-null then payload contains DIFF_KEY_TITLE with new value`() {
        val oldTab = NormalTab(
            entity = TabEntity(tabId = "1", url = "https://huel.com", title = "Old Title", position = 0),
            isActive = false,
        )
        val newTab = NormalTab(
            entity = TabEntity(tabId = "1", url = "https://huel.com", title = "New Title", position = 0),
            isActive = false,
        )

        val bundle = callback.getChangePayload(oldTab, newTab)

        assertTrue(bundle!!.containsKey(DIFF_KEY_TITLE))
        assertFalse(bundle.containsKey(DIFF_KEY_URL))
    }
}
