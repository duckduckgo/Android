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

package com.duckduckgo.app.tabs.ui

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.browsermode.api.BrowserMode
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class RealTabTitleResolverTest {

    private val context: Context = mock()
    private val testee = RealTabTitleResolver(context)

    @Test
    fun whenTabHasTitleThenResolvedTitleIsTitle() {
        assertEquals(TITLE, testee.resolveTitle(TabEntity("", URL, TITLE, position = 0), BrowserMode.REGULAR))
    }

    @Test
    fun whenTabHasTitleEndingInDuckDuckGoSuffixThenSuffixIsStripped() {
        val raw = "Search results at DuckDuckGo"
        assertEquals("Search results ", testee.resolveTitle(TabEntity("", URL, raw, position = 0), BrowserMode.REGULAR))
    }

    @Test
    fun whenTabHasNoTitleThenResolvedTitleIsUrlHost() {
        assertEquals("example.com", testee.resolveTitle(TabEntity("", URL, null, position = 0), BrowserMode.REGULAR))
    }

    @Test
    fun whenTabHasNoTitleAndUrlIsInvalidThenResolvedTitleIsBlank() {
        assertEquals("", testee.resolveTitle(TabEntity("", INVALID_URL, null, position = 0), BrowserMode.REGULAR))
    }

    @Test
    fun whenTabTitleIsAboutBlankThenResolvedTitleIsAboutBlank() {
        val entity = TabEntity("1", url = null, title = "about:blank", position = 0)
        assertEquals("about:blank", testee.resolveTitle(entity, BrowserMode.REGULAR))
    }

    @Test
    fun whenTabTitleIsAboutBlankUppercaseThenResolvedTitleIsAboutBlank() {
        val entity = TabEntity("1", url = null, title = "ABOUT:BLANK", position = 0)
        assertEquals("about:blank", testee.resolveTitle(entity, BrowserMode.REGULAR))
    }

    @Test
    fun whenTabIsBlankAndModeIsRegularThenResolvedTitleIsNewTabLabel() {
        whenever(context.getString(R.string.newTabMenuItem)).thenReturn("New Tab")
        val entity = TabEntity("1", url = null, title = null, position = 0)
        assertEquals("New Tab", testee.resolveTitle(entity, BrowserMode.REGULAR))
    }

    @Test
    fun whenTabIsBlankAndModeIsFireThenResolvedTitleIsFireTabLabel() {
        whenever(context.getString(R.string.fireTabMenuItem)).thenReturn("Fire Tab")
        val entity = TabEntity("1", url = null, title = null, position = 0)
        assertEquals("Fire Tab", testee.resolveTitle(entity, BrowserMode.FIRE))
    }

    private companion object {
        const val TITLE = "Title"
        const val URL = "https://example.com"
        const val INVALID_URL = "notaurl"
    }
}
