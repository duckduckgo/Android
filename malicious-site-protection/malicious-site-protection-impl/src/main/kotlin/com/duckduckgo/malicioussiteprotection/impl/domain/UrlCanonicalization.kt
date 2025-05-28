/*
 * Copyright (c) 2025 DuckDuckGo
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
import android.util.Patterns.IP_ADDRESS
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.malicioussiteprotection.impl.MaliciousSiteProtectionRCFeature
import com.squareup.anvil.annotations.ContributesBinding
import java.net.Inet4Address
import java.net.InetAddress
import javax.inject.Inject

interface UrlCanonicalization {
    fun canonicalizeUrl(uri: Uri): Uri
}

@ContributesBinding(AppScope::class)
class RealUrlCanonicalization @Inject constructor(
    private val maliciousSiteProtectionRCFeature: MaliciousSiteProtectionRCFeature,
) : UrlCanonicalization {

    private val multipleDotsRegex = Regex("\\.{2,}")
    private val multipleSlashesRegex = Regex("/+")

    private fun String.canonicalizeDomain(): String {
        return this
            .lowercase()
            .filter { it.code in 0x20..0x7E }
            .trim('.')
            .replace(multipleDotsRegex, ".")
            .normalizeIpAddress()
            .percentEncodeDomain()
            .let {
                if (maliciousSiteProtectionRCFeature.stripWWWPrefix()) {
                    it.removePrefix("www.")
                } else {
                    it
                }
            }
            .split('.')
            .takeLast(6)
            .joinToString(".")
    }

    override fun canonicalizeUrl(uri: Uri): Uri {
        return runCatching {
            Uri.Builder()
                .scheme(uri.scheme?.clean())
                .encodedAuthority(uri.host?.clean()?.canonicalizeDomain())
                .encodedPath(uri.path?.clean().canonicalizePath())
                .encodedQuery(uri.query?.clean())
                .build()
        }.getOrDefault(uri)
    }

    private fun String.percentEncodePath(): String {
        return buildString(length) {
            this@percentEncodePath.forEach { c ->
                when {
                    c.code <= 32 || c.code >= 127 || c == '#' || c == '%' ->
                        append('%').append(c.code.toString(16).padStart(2, '0').uppercase())
                    else -> append(c)
                }
            }
        }
    }

    private fun String.percentEncodeDomain(): String {
        return buildString(length) {
            this@percentEncodeDomain.forEach { c ->
                when {
                    c.isLetterOrDigit() || c == '.' || c == '-' -> append(c)
                    else -> append('%').append(c.code.toString(16).padStart(2, '0').uppercase())
                }
            }
        }
    }

    private fun String?.canonicalizePath(): String {
        if (isNullOrBlank()) return "/"

        return this
            .trimEnd('/')
            .let { multipleSlashesRegex.replace(it, "/") }
            .split("/")
            .normalizePath()
            .joinToString("/", prefix = "/")
            .percentEncodePath()
    }

    private fun String.normalizeIpAddress(): String {
        // If not an IP address, return host as is

        /* TODO (cbarreiro) IP_ADDRESS is deprecated and InetAddresses.isNumericAddress should be used above API 29 instead,
         *  but it's broken in Robolectric
         */
        if (!IP_ADDRESS.matcher(this).matches()) return this

        return runCatching {
            (InetAddress.getByName(this) as Inet4Address).hostAddress
        }.getOrDefault(this)
    }

    private fun List<String>.normalizePath(): List<String> {
        val stack = mutableListOf<String>()
        for (part in this) {
            when (part) {
                "", "." -> continue
                ".." -> if (stack.isNotEmpty() && stack.last() != "..") stack.removeAt(stack.lastIndex) else stack.add(part)
                else -> stack.add(part)
            }
        }
        return stack
    }

    private fun String.clean(): String {
        // Removes tab (0x09), CR (0x0d), and LF (0x0a) characters
        return filterNot { it.code in listOf(0x09, 0x0d, 0x0a) }
    }
}
