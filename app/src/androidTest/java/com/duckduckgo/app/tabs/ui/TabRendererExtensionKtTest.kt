/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.tabs.ui

import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.tabs.model.TabEntity
import org.junit.Assert.*
import org.junit.Test

class TabRendererExtensionTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun whenTabIsBlankThenDisplayTitleIsDuckDuckGo() {
        assertEquals("DuckDuckGo", TabEntity("", position = 0).displayTitle(context))
    }

    @Test
    fun whenTabHasTitleThenDisplayTitleIsSame() {
        assertEquals(TITLE, TabEntity("", URL, TITLE, position = 0).displayTitle(context))
    }

    @Test
    fun whenTabDoesNotHaveTitleThenDisplayTitleIsUrlHost() {
        assertEquals("example.com", TabEntity("", URL, null, position = 0).displayTitle(context))
    }

    @Test
    fun whenTabDoesNotHaveTitleAndUrlIsInvalidThenTitleIsBlank() {
        assertEquals("", TabEntity("", INVALID_URL, null, position = 0).displayTitle(context))
    }

    @Test
    fun whenTabIsBlankThenUrlIsDuckDuckGo() {
        assertEquals("https://duckduckgo.com", TabEntity("", position = 0).displayUrl())
    }

    @Test
    fun whenTabHasUrlThenDisplayUrlIsSame() {
        assertEquals(URL, TabEntity("", URL, TITLE, position = 0).displayUrl())
    }

    @Test
    fun whenTabDoesNotHaveAUrlThenDisplayUrlIsBlank() {
        assertEquals("", TabEntity("", null, TITLE, position = 0).displayUrl())
    }

    @Test
    fun whenTabHasUrlThenFaviconIsNotNull() {
        assertNotNull(TabEntity("", URL, TITLE, position = 0).favicon())
    }

    @Test
    fun whenTabDoesNotHaveAUrlThenFaviconIsNull() {
        assertNull(TabEntity("", null, TITLE, position = 0).favicon())
    }

    @Test
    fun whenTabHasInvalidUrlThenFaviconIsNull() {
        assertNull(TabEntity("", INVALID_URL, TITLE, position = 0).favicon())
    }

    companion object {
        private const val TITLE = "Title"
        private const val URL = "https://example.com"
        private const val INVALID_URL = "notaurl"

    }
}