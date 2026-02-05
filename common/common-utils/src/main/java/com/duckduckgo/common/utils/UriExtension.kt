/*
 * Copyright (c) 2023 DuckDuckGo
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
package com.duckduckgo.common.utils

import android.net.Uri
import android.net.Uri.parse
import android.os.Build
import androidx.core.net.toUri
import com.duckduckgo.common.utils.UrlScheme.Companion.http
import java.io.UnsupportedEncodingException
import java.net.InetAddress
import java.net.URLEncoder
import java.util.*

val IP_REGEX =
    Regex(
        "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)(:[0-9]+)?$",
    )

fun Uri.withScheme(): Uri {
    // Uri.parse function falsely parses IP:PORT string.
    // For example if input is "255.255.255.255:9999", it falsely flags 255.255.255.255 as the
    // scheme.
    // Therefore in the withScheme method, we need to parse it after manually inserting "http".
    if (scheme == null || scheme!!.matches(IP_REGEX)) {
        return parse("$http://${toString()}")
    }

    return this
}

/**
 * Tries to resolve a host (even if the scheme is missing), returning a basic host without the "www"
 * subdomain.
 */
val Uri.baseHost: String?
    get() = withScheme().host?.removePrefix("www.")

val Uri.isHttp: Boolean
    get() = scheme?.equals(http, true) ?: false

val Uri.isHttps: Boolean
    get() = scheme?.equals(UrlScheme.https, true) ?: false

val Uri.toHttps: Uri
    get() = buildUpon().scheme(UrlScheme.https).build()

val Uri.isHttpOrHttps: Boolean
    get() = isHttp || isHttps

val Uri.hasIpHost: Boolean
    get() {
        return baseHost?.matches(IP_REGEX) ?: false
    }

/**
 * Checks if the URI represents a local or private network address.
 * This includes:
 * - localhost hostname
 * - IPv4/IPv6 loopback addresses (127.0.0.0/8, ::1)
 * - IPv4/IPv6 private network ranges (10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16, fc00::/7)
 * - IPv6 link-local addresses (fe80::/10)
 *
 * Note: file:// URLs return false as they are not network locations.
 * Note: .local domain names are NOT treated as local (they require mDNS resolution).
 */
val Uri.isLocalUrl: Boolean
    get() {
        // Explicitly exclude file:// scheme
        if (scheme == "file") return false

        val host = this.host?.lowercase() ?: return false
        if (host == "localhost") return true

        // Pre-validate that host looks like a numeric IP address
        // This prevents malformed IPs and domain names from being parsed
        if (!host.looksLikeNumericAddress()) return false

        // Try to parse as numeric IP address (both IPv4 and IPv6)
        val addr = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                android.net.InetAddresses.parseNumericAddress(host)
            } else {
                // Fallback for pre-Q: use InetAddress.getByName (already validated above)
                InetAddress.getByName(host)
            }
        }.getOrNull() ?: return false

        // Check if it's a loopback, site-local (private IPv4), or link-local (IPv6) address
        // For IPv6, also check for unique local addresses (fc00::/7) manually
        return addr.isLoopbackAddress || addr.isSiteLocalAddress || addr.isLinkLocalAddress || addr.isIPv6UniqueLocal()
    }

/**
 * Quick check if a string looks like a numeric IP address (IPv4 or IPv6).
 * Prevents attempting to parse domain names or malformed addresses.
 */
private fun String.looksLikeNumericAddress(): Boolean {
    // IPv6: contains colons
    if (contains(':')) return true

    // IPv4: exactly 4 parts separated by dots, each 0-255
    val parts = split('.')
    if (parts.size != 4) return false
    return parts.all { part ->
        part.toIntOrNull()?.let { it in 0..255 } ?: false
    }
}


/**
 * Checks if an IPv6 address is a Unique Local Address (ULA) in the fc00::/7 range.
 * These are the IPv6 equivalent of private IPv4 addresses.
 */
private fun InetAddress.isIPv6UniqueLocal(): Boolean {
    val bytes = this.address
    // IPv6 addresses have 16 bytes, check if it starts with 0xfc or 0xfd
    return bytes.size == 16 && (bytes[0].toInt() and 0xfe) == 0xfc
}

val Uri.absoluteString: String
    get() {
        return "$scheme://$host$path"
    }

fun Uri.toStringDropScheme(): String {
    return if (scheme != null) this.toString().substringAfter("$scheme://") else this.toString()
}

fun Uri.isHttpsVersionOfUri(other: Uri): Boolean {
    return isHttps && other.isHttp && other.toHttps == this
}

private val MOBILE_URL_PREFIXES = listOf("m.", "mobile.")

val Uri.isMobileSite: Boolean
    get() {
        val auth = authority ?: return false
        return MOBILE_URL_PREFIXES.firstOrNull { auth.startsWith(it) } != null
    }

fun Uri.toDesktopUri(): Uri {
    if (!isMobileSite) return this

    val newUrl =
        MOBILE_URL_PREFIXES.fold(toString()) { url, prefix -> url.replaceFirst(prefix, "") }

    return parse(newUrl)
}

fun Uri.domain(): String? = this.host

// to obtain a favicon for a website, we go directly to the site and look for /favicon.ico
private const val faviconBaseUrlFormat = "%s://%s/favicon.ico"
private const val touchFaviconBaseUrlFormat = "%s://%s/apple-touch-icon.png"

fun Uri?.faviconLocation(): Uri? {
    if (this == null) return null
    val host = this.host
    if (host.isNullOrBlank()) return null
    val isHttps = this.isHttps
    return parse(String.format(faviconBaseUrlFormat, if (isHttps) "https" else "http", host))
}

fun Uri?.touchFaviconLocation(): Uri? {
    if (this == null) return null
    val host = this.host
    if (host.isNullOrBlank()) return null
    val isHttps = this.isHttps
    return parse(String.format(touchFaviconBaseUrlFormat, if (isHttps) "https" else "http", host))
}

fun Uri.getValidUrl(): ValidUrl? {
    val validHost = host ?: return null
    val validBaseHost = baseHost ?: return null
    return ValidUrl(validBaseHost, validHost, path)
}

data class ValidUrl(
    val baseHost: String,
    val host: String,
    val path: String?,
)

fun Uri.replaceQueryParameters(queryParameters: List<String>): Uri {
    val newUri = buildUpon().clearQuery()
    val query = queryParameters.joinToString(separator = "&") { parameter ->
        getEncodedQueryParameters(parameter).joinToString(separator = "&") {
            "$parameter=$it"
        }
    }
    newUri.encodedQuery(query)
    return newUri.build()
}

/**
 * This method is exactly the same as [Uri.getQueryParameters] except it doesn't decode each entry in the values list.
 * @return a list of encoded values.
 */
fun Uri.getEncodedQueryParameters(key: String?): List<String> {
    if (isOpaque) {
        throw UnsupportedOperationException("This isn't a hierarchical URI.")
    }
    if (key == null) {
        throw NullPointerException("key")
    }
    val query: String = encodedQuery ?: return emptyList()
    val encodedKey: String = try {
        URLEncoder.encode(key, "UTF-8")
    } catch (e: UnsupportedEncodingException) {
        throw AssertionError(e)
    }
    val values = ArrayList<String>()
    var start = 0
    do {
        val nextAmpersand = query.indexOf('&', start)
        val end = if (nextAmpersand != -1) nextAmpersand else query.length
        var separator = query.indexOf('=', start)
        if (separator > end || separator == -1) {
            separator = end
        }
        if (separator - start == encodedKey.length &&
            query.regionMatches(start, encodedKey, 0, encodedKey.length)
        ) {
            if (separator == end) {
                values.add("")
            } else {
                values.add(query.substring(separator + 1, end))
            }
        }

        // Move start to end of name.
        start = if (nextAmpersand != -1) {
            nextAmpersand + 1
        } else {
            break
        }
    } while (true)
    return Collections.unmodifiableList(values)
}

fun String.extractDomain(): String? {
    return if (this.startsWith("http")) {
        this.toUri().domain()
    } else if (this.startsWith("duck")) {
        this.toUri().buildUpon().path("").toString()
    } else {
        "https://$this".extractDomain()
    }
}

fun String.normalizeScheme(): String {
    if (!startsWith("https://") && !startsWith("http://")) {
        return "https://$this"
    }
    return this
}
