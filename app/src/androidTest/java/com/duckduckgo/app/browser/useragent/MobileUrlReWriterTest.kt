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

package com.duckduckgo.app.browser.useragent

import androidx.core.net.toUri
import org.junit.Assert.*
import org.junit.Test

class MobileUrlReWriterTest {

    private val testee = MobileUrlReWriter()

    @Test
    fun whenIsStrictlyMobileSiteAndSiteBelongsToMobileSiteHostsThenReturnTrue() {
        val domain = MobileUrlReWriter.strictlyMobileSiteHosts.first().host
        val url = "https://$domain".toUri()

        assertTrue(testee.isStrictlyMobileSite(url))
    }

    @Test
    fun whenIsStrictlyMobileSiteAndSiteBelongsToMobileSiteHostsWithExclusionPathThenReturnFalse() {
        val domain = MobileUrlReWriter.strictlyMobileSiteHosts.first().host
        val path = MobileUrlReWriter.strictlyMobileSiteHosts.first().excludedPaths.first()
        val url = "https://$domain/$path".toUri()

        assertFalse(testee.isStrictlyMobileSite(url))
    }

    @Test
    fun whenIsStrictlyMobileSiteAndSiteDoesNotBelongsToMobileSiteHostsThenReturnFalse() {
        assertFalse(testee.isStrictlyMobileSite("https://example.com".toUri()))
    }

    @Test
    fun whenGetMobileSiteThenReturnAMobileUrl() {
        val url = "https://example.com"

        val value = testee.getMobileSite(url.toUri())

        assertEquals("https://m.example.com", value)
    }

}
