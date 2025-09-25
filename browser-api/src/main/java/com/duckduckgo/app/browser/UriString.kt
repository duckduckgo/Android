/*
 * Copyright (c) 2024 DuckDuckGo
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

import android.net.Uri
import androidx.collection.LruCache
import androidx.core.util.PatternsCompat
import com.duckduckgo.common.utils.UrlScheme
import com.duckduckgo.common.utils.baseHost
import com.duckduckgo.common.utils.withScheme
import java.lang.IllegalArgumentException
import logcat.LogPriority.INFO
import logcat.logcat
import okhttp3.HttpUrl.Companion.toHttpUrl

class UriString {

    companion object {
        private const val localhost = "localhost"
        private const val space = " "
        private val webUrlRegex by lazy { PatternsCompat.WEB_URL.toRegex() }
        private val domainRegex by lazy { PatternsCompat.DOMAIN_NAME.toRegex() }
        private val inputQueryCleanupRegex by lazy { "['\"\n]|\\s+".toRegex() }
        private val cache = LruCache<Int, Boolean>(250_000)

        fun extractUrl(inputQuery: String): String? {
            val urls = webUrlRegex.findAll(inputQuery).map { it.value }.toList()
            return when {
                urls.isEmpty() -> null
                urls.size == 1 -> urls.first()
                // If multiple URLs found and all start with http, treat this as a search.
                urls.all { it.startsWith("http") } -> null
                else -> urls.firstOrNull { it.startsWith("http") }
            }
        }

        fun host(uriString: String): String? {
            return Uri.parse(uriString).baseHost
        }

        fun sameOrSubdomain(
            child: String,
            parent: String,
        ): Boolean {
            val childHost = host(child) ?: return false
            val parentHost = host(parent) ?: return false
            return parentHost == childHost || childHost.endsWith(".$parentHost")
        }

        fun sameOrSubdomain(
            child: Domain?,
            parent: Domain,
        ): Boolean {
            child ?: return false
            val hash = (child.value + parent.value).hashCode()
            return cache.get(hash) ?: (parent == child || child.value.endsWith(".${parent.value}")).also {
                cache.put(hash, it)
            }
        }

        fun sameOrSubdomain(
            child: Uri,
            parent: String,
        ): Boolean {
            val childHost = child.host ?: return false
            val parentHost = host(parent) ?: return false
            return parentHost == childHost || childHost.endsWith(".$parentHost")
        }

        fun sameOrSubdomain(
            child: Uri,
            parent: Domain,
        ): Boolean {
            val childHost = child.host ?: return false
            val parentHost = host(parent.value) ?: return false
            return parentHost == childHost || childHost.endsWith(".$parentHost")
        }

        fun sameOrSubdomainPair(
            first: Uri,
            second: String,
        ): Boolean {
            val childHost = first.host ?: return false
            val parentHost = host(second) ?: return false
            return parentHost == childHost || (childHost.endsWith(".$parentHost") || parentHost.endsWith(".$childHost"))
        }

        fun sameOrSubdomainPair(
            first: Uri,
            second: Uri,
        ): Boolean {
            val childHost = first.host ?: return false
            val parentHost = second.host ?: return false
            return parentHost == childHost || (childHost.endsWith(".$parentHost") || parentHost.endsWith(".$childHost"))
        }

        fun isWebUrl(inputQuery: String, extractUrlQuery: Boolean = false): Boolean {
            if (extractUrlQuery) {
                val cleanInputQuery = cleanupInputQuery(inputQuery)
                val extractedUrl = extractUrl(cleanInputQuery)
                if (extractedUrl != null) {
                    return isWebUrl(extractedUrl)
                }
            }

            if (inputQuery.contains("\"") || inputQuery.contains("'")) {
                return false
            }

            if (inputQuery.contains(space)) return false
            val rawUri = Uri.parse(inputQuery)

            val uri = rawUri.withScheme()
            if (!uri.hasWebScheme()) return false
            if (uri.userInfo != null) return false

            val host = uri.host ?: return false
            if (host == localhost) return true
            if (host.contains("!")) return false

            if (webUrlRegex.containsMatchIn(host)) return true

            return try {
                // this will throw an exception if OkHttp thinks it's not a well-formed HTTP or HTTPS URL
                uri.toString().toHttpUrl()

                // it didn't match the regex and OkHttp thinks it looks good
                // we might have prepended a scheme to let okHttp check, so only consider it valid at this point if the scheme was manually provided
                // e.g., this means "http://raspberrypi" will be considered a webUrl, but "raspberrypi" will not
                rawUri.hasWebScheme()
            } catch (e: IllegalArgumentException) {
                logcat(INFO) { "Failed to parse $inputQuery as a web url; assuming it isn't" }
                false
            }
        }

        fun isValidDomain(domain: String): Boolean {
            return domainRegex.matches(domain)
        }

        fun isDuckUri(inputQuery: String): Boolean {
            val uri = Uri.parse(inputQuery)
            return isDuckUri(uri)
        }

        fun isDuckUri(uri: Uri): Boolean {
            if (!uri.hasDuckScheme()) return false
            if (uri.userInfo != null) return false
            val host = uri.host ?: return false
            return !host.contains("!")
        }

        private fun Uri.hasWebScheme(): Boolean {
            val normalized = normalizeScheme()
            return normalized.scheme == UrlScheme.http || normalized.scheme == UrlScheme.https
        }

        private fun Uri.hasDuckScheme(): Boolean {
            val normalized = normalizeScheme()
            return normalized.scheme == UrlScheme.duck
        }

        private fun cleanupInputQuery(text: String): String {
            return text.replace(inputQueryCleanupRegex, " ").trim()
        }
    }
}
