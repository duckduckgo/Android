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

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Resolves the value of the Referer header for a file download request, following a
 * strict-origin-when-cross-origin policy:
 * - same-origin download: the full page URL (without fragment) is sent
 * - cross-origin download: only the page origin (scheme://host[:port]/) is sent
 * - HTTPS page downloading over HTTP (downgrade), or non-HTTP(S) URLs: no Referer is sent
 */
internal fun resolveDownloadReferer(
    pageUrl: String?,
    downloadUrl: String,
): String? {
    if (pageUrl.isNullOrBlank()) return null

    val page = pageUrl.toHttpUrlOrNull() ?: return null
    val download = downloadUrl.toHttpUrlOrNull() ?: return null

    // never send a Referer when downgrading from HTTPS to HTTP
    if (page.isHttps && !download.isHttps) return null

    return if (page.originEquals(download)) {
        pageUrl.substringBefore('#')
    } else {
        buildString {
            append(page.scheme).append("://").append(page.host)
            if (page.port != defaultPort(page.scheme)) {
                append(':').append(page.port)
            }
            append('/')
        }
    }
}

private fun okhttp3.HttpUrl.originEquals(other: okhttp3.HttpUrl): Boolean =
    scheme == other.scheme && host == other.host && port == other.port

private fun defaultPort(scheme: String): Int = if (scheme == "https") 443 else 80
