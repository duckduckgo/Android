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

package com.duckduckgo.app.browser

import com.duckduckgo.app.browser.omnibar.Omnibar.ViewMode
import com.duckduckgo.pagetranslation.api.TabContent
import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserTabViewModeTransitionTest {
    private val url = "https://example.com"

    @Test
    fun whenLeavingDuckAiThenBrowser() {
        assertEquals(ViewMode.Browser(url), ViewMode.DuckAI.afterDuckAiFullScreenDisabled(url))
    }

    @Test
    fun whenBrowsingThenBrowserWithTheNewUrl() {
        assertEquals(ViewMode.Browser(url), ViewMode.Browser("https://other.com").afterDuckAiFullScreenDisabled(url))
    }

    @Test
    fun whenCustomTabThenBrowser() {
        assertEquals(ViewMode.Browser(url), ViewMode.CustomTab(toolbarColor = 0, title = null).afterDuckAiFullScreenDisabled(url))
    }

    @Test
    fun whenNoWebPageThenModeKept() {
        listOf(ViewMode.Error, ViewMode.SSLWarning, ViewMode.MaliciousSiteWarning, ViewMode.NewTab, ViewMode.Pdf("https://example.com/a.pdf"))
            .forEach { assertEquals(it.toString(), it, it.afterDuckAiFullScreenDisabled(url)) }
    }

    @Test
    fun whenErrorPageThenTranslationContentStaysOther() {
        assertEquals(TabContent.Other, ViewMode.Error.afterDuckAiFullScreenDisabled(url).toTabContent())
    }
}
