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

class BrowserTabPageTranslationContentTest {
    @Test
    fun aPageInTheBrowserOrACustomTabIsAWebPage() {
        assertEquals(TabContent.WebPage, ViewMode.Browser("https://example.com").toTabContent())
        assertEquals(TabContent.WebPage, ViewMode.CustomTab(toolbarColor = 0, title = null).toTabContent())
    }

    @Test
    fun aPdfIsAPdf() {
        assertEquals(TabContent.Pdf, ViewMode.Pdf("https://example.com/a.pdf").toTabContent())
    }

    @Test
    fun everythingElseIsOther() {
        listOf(ViewMode.NewTab, ViewMode.DuckAI, ViewMode.Error, ViewMode.SSLWarning, ViewMode.MaliciousSiteWarning).forEach {
            assertEquals(TabContent.Other, it.toTabContent())
        }
    }
}
