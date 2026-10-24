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

package com.duckduckgo.downloads.impl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadRefererResolverTest {

    @Test
    fun whenPageUrlIsNullThenNoReferer() {
        assertNull(resolveDownloadReferer(pageUrl = null, downloadUrl = "https://example.com/file.jpg"))
    }

    @Test
    fun whenPageUrlIsBlankThenNoReferer() {
        assertNull(resolveDownloadReferer(pageUrl = "", downloadUrl = "https://example.com/file.jpg"))
    }

    @Test
    fun whenPageUrlIsNotHttpThenNoReferer() {
        assertNull(resolveDownloadReferer(pageUrl = "about:blank", downloadUrl = "https://example.com/file.jpg"))
    }

    @Test
    fun whenDownloadUrlIsNotHttpThenNoReferer() {
        assertNull(resolveDownloadReferer(pageUrl = "https://example.com/page", downloadUrl = "data:image/png;base64,abc"))
    }

    @Test
    fun whenSameOriginThenFullPageUrlWithoutFragment() {
        val referer = resolveDownloadReferer(
            pageUrl = "https://inkbunny.net/s/1234567#comments",
            downloadUrl = "https://inkbunny.net/files/full/image.jpg",
        )
        assertEquals("https://inkbunny.net/s/1234567", referer)
    }

    @Test
    fun whenSameOriginWithQueryThenFullPageUrlIncludingQuery() {
        val referer = resolveDownloadReferer(
            pageUrl = "https://example.com/page?id=42&tab=2",
            downloadUrl = "https://example.com/download/file.zip",
        )
        assertEquals("https://example.com/page?id=42&tab=2", referer)
    }

    @Test
    fun whenCrossOriginThenOnlyPageOrigin() {
        val referer = resolveDownloadReferer(
            pageUrl = "https://inkbunny.net/s/1234567",
            downloadUrl = "https://content.ib.metapix.net/files/image.jpg",
        )
        assertEquals("https://inkbunny.net/", referer)
    }

    @Test
    fun whenCrossOriginWithNonDefaultPortThenPortIncludedInOrigin() {
        val referer = resolveDownloadReferer(
            pageUrl = "http://localhost:8080/page",
            downloadUrl = "https://example.com/file.zip",
        )
        assertEquals("http://localhost:8080/", referer)
    }

    @Test
    fun whenDifferentPortThenTreatedAsCrossOrigin() {
        val referer = resolveDownloadReferer(
            pageUrl = "http://example.com:8080/page",
            downloadUrl = "http://example.com:9090/file.zip",
        )
        assertEquals("http://example.com:8080/", referer)
    }

    @Test
    fun whenHttpsPageDownloadsOverHttpThenNoReferer() {
        assertNull(
            resolveDownloadReferer(
                pageUrl = "https://example.com/page",
                downloadUrl = "http://example.com/file.zip",
            ),
        )
    }

    @Test
    fun whenHttpPageDownloadsOverHttpsThenOriginSent() {
        val referer = resolveDownloadReferer(
            pageUrl = "http://example.com/page",
            downloadUrl = "https://example.com/file.zip",
        )
        assertEquals("http://example.com/", referer)
    }
}
