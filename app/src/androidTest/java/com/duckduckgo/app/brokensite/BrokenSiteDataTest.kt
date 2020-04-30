/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.app.brokensite

import com.duckduckgo.app.global.model.Site
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Assert.assertEquals
import org.junit.Test

class BrokenSiteDataTest {

    @Test
    fun whenSiteIsNotNullThenBrokenSiteDataContainsUrl() {
        val site = buildSite("foo.com")
        val data = BrokenSiteData.fromSite(site)
        assertEquals("foo.com", data.url)
    }

    @Test
    fun whenSiteIsNullThenBrokenSiteDataContainsBlankUrl() {
        val data = BrokenSiteData.fromSite(null)
        assertEquals("", data.url)
    }

    private fun buildSite(
        url: String
    ): Site {
        val site: Site = mock()
        whenever(site.url).thenReturn(url)
        return site
    }
}