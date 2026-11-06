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

package com.duckduckgo.url.impl

import android.net.Uri
import androidx.core.net.toUri
import androidx.core.util.PatternsCompat
import com.duckduckgo.common.utils.LOCALHOST
import com.duckduckgo.common.utils.UrlScheme
import com.duckduckgo.common.utils.baseHost
import com.duckduckgo.common.utils.isLocalUrl
import com.duckduckgo.common.utils.isMobileSite
import com.duckduckgo.common.utils.withScheme
import com.duckduckgo.url.DaxDomain
import com.duckduckgo.url.DaxScheme
import com.duckduckgo.url.DaxUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * The single [DaxUrl] implementation. [memoize] selects the one axis that used to be expressed as
 * separate Legacy/New subclasses: when true, [raw] is parsed once and derived values are cached (the
 * memoized "New" behavior); when false, every access recomputes (mirrors today's re-parse-per-call
 * "Legacy" behavior). Both modes produce identical results — only the repeated work differs — so the
 * `useUnifiedUrl` flag selects between them as a pure performance A/B. [raw] is kept module-internal,
 * never on the public [DaxUrl] surface.
 *
 * The cheap accessors below (path/query/mobile) read from [uri] directly rather than caching their
 * own value — `android.net.Uri` internally caches its parsed components after first access, so once
 * [uri] is resolved they're cheap; in recompute mode the repeated cost is re-parsing [raw] into a
 * new [Uri].
 */
internal class RealDaxUrl(
    internal val raw: String,
    internal val memoize: Boolean,
) : DaxUrl {

    private val uri: Uri by held(memoize) { raw.toUri() }

    override val domain: DaxDomain? by held(memoize) {
        uri.baseHost?.takeIf { it.isNotBlank() }?.let { RealDaxDomain(it, memoize) }
    }

    override val host: String?
        get() = uri.host

    override val scheme: DaxScheme by held(memoize) { DaxScheme.fromValue(uri.scheme) }

    override val isWebUrl: Boolean by held(memoize) { webUrlFrom(uri) }

    override val isLocalUrl: Boolean by held(memoize) { uri.isLocalUrl }

    override val isHttps: Boolean
        get() = scheme == DaxScheme.HTTPS

    override val isMobileSite: Boolean
        get() = uri.isMobileSite

    override val path: String?
        get() = uri.path

    override val pathSegments: List<String>
        get() = uri.pathSegments

    override val lastPathSegment: String?
        get() = uri.lastPathSegment

    override val port: Int?
        get() = uri.port.takeIf { it != -1 }

    override val query: String?
        get() = uri.query

    override val fragment: String?
        get() = uri.fragment

    override val queryParameterNames: Set<String>
        get() = if (uri.isOpaque) emptySet() else uri.queryParameterNames

    override fun queryParameter(key: String): String? =
        if (uri.isOpaque) null else uri.getQueryParameter(key)

    override fun matches(other: DaxUrl): Boolean = matches(other.domain ?: return false)

    override fun matches(other: DaxDomain): Boolean = other.sameOrSubdomain(domain ?: return false)

    override fun containsMatch(regex: Regex): Boolean = regex.containsMatchIn(raw)

    // By [raw], not by mode: a memoized and a recomputing URL built from the same String are equal,
    // mirroring RealDaxDomain, so the flag never changes what a cache keyed by DaxUrl sees.
    override fun equals(other: Any?): Boolean = other is RealDaxUrl && other.raw == raw

    override fun hashCode(): Int = raw.hashCode()

    // Mirrors UriString.isWebUrl (default, no search-query disambiguation — a DaxUrl is already a
    // URL). The two should converge, with UriString eventually delegating here. Takes the parsed
    // [rawUri] so the memoized parse can be reused.
    private fun webUrlFrom(rawUri: Uri): Boolean {
        if (raw.contains("\"") || raw.contains("'") || raw.contains(" ")) return false
        val uri = rawUri.withScheme()
        if (!uri.hasWebScheme()) return false
        if (uri.userInfo != null) return false
        val host = uri.host ?: return false
        if (host == LOCALHOST) return true
        if (host.contains("!")) return false
        if (PatternsCompat.WEB_URL.matcher(host).find()) return true
        return try {
            // OkHttp throws if this isn't a well-formed HTTP(S) URL. Only trust the verdict when
            // the scheme was explicitly provided (so "http://raspberrypi" passes, "raspberrypi" doesn't).
            uri.toString().toHttpUrl()
            rawUri.hasWebScheme()
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    private fun Uri.hasWebScheme(): Boolean {
        val normalized = normalizeScheme()
        return normalized.scheme == UrlScheme.http || normalized.scheme == UrlScheme.https
    }
}
