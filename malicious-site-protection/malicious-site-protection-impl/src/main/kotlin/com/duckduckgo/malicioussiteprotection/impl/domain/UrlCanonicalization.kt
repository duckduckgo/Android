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

package com.duckduckgo.malicioussiteprotection.impl.domain

import android.net.Uri
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import java.net.Inet4Address
import java.net.InetAddress
import java.net.URLDecoder
import javax.inject.Inject

interface UrlCanonicalization {
    fun canonicalizeDomain(hostname: String): String
    fun canonicalizeUrl(uri: Uri): Uri
}

@ContributesBinding(AppScope::class)
class RealUrlCanonicalization @Inject constructor() : UrlCanonicalization {

    private val multipleDotsRegex = Regex("\\.{2,}")
    private val ipv4Regex = Regex(
        "^(([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\\.){3}([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])$",
    )
    private val multipleSlashesRegex = Regex("/+")

    override fun canonicalizeDomain(hostname: String): String {
        var decoded = URLDecoder.decode(hostname, "UTF-8")
            .lowercase()
            .filter { it.code in 0x20..0x7E }
            .trim('.')
            .replace(multipleDotsRegex, ".")

        decoded = normalizeIpAddress(decoded)
        decoded = percentEncodeString(decoded)

        val components = decoded.split('.')
        return if (components.size > 6) {
            components.takeLast(6).joinToString(".")
        } else {
            decoded
        }
    }

    override fun canonicalizeUrl(uri: Uri): Uri {
        val cleanUrl = Uri.parse(cleanUrl(uri.toString()))

        val scheme = cleanUrl.scheme
        val authority = cleanUrl.authority
        val path = canonicalizePath(cleanUrl.path ?: "")
        val query = cleanUrl.query

        val canonicalDomain = canonicalizeDomain(authority ?: "")

        val canonicalUrl = Uri.Builder()
            .scheme(scheme)
            .encodedAuthority(canonicalDomain)
            .encodedPath(path)
            .encodedQuery(query)
            .build()
            .toString()

        return try {
            Uri.parse(canonicalUrl)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid URL")
        }
    }

    private fun percentEncodeString(input: String): String {
        return buildString(input.length) {
            input.forEach { c ->
                when {
                    c.isLetterOrDigit() || c in ".-_~" -> append(c)
                    else -> append('%').append(c.code.toString(16).padStart(2, '0').uppercase())
                }
            }
        }
    }

    private fun canonicalizePath(path: String): String {
        if (path.isBlank()) return "/"

        var canonicalPath = path.trimEnd('/')
        if (canonicalPath.isEmpty()) canonicalPath = "/"

        canonicalPath = multipleSlashesRegex.replace(canonicalPath, "/")

        val normalizedParts = normalizePath(canonicalPath.split("/"))
        canonicalPath = "/" + normalizedParts.joinToString("/")

        return percentEncodeString(canonicalPath)
    }

    private fun normalizeIpAddress(address: String): String {
        return if (ipv4Regex.matches(address)) {
            (InetAddress.getByName(address) as Inet4Address).hostAddress ?: address
        } else {
            address
        }
    }

    private fun normalizePath(path: List<String>): List<String> {
        val stack = mutableListOf<String>()
        for (part in path) {
            when (part) {
                "", "." -> continue
                ".." -> if (stack.isNotEmpty() && stack.last() != "..") stack.removeAt(stack.lastIndex) else stack.add(part)
                else -> stack.add(part)
            }
        }
        return stack
    }

    private fun cleanUrl(url: String): String {
        return url.filter { it.code !in listOf(0x09, 0x0d, 0x0a) }
    }
}
