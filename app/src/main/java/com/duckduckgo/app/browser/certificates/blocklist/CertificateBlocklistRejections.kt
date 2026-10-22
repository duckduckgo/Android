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

package com.duckduckgo.app.browser.certificates.blocklist

import java.security.MessageDigest
import java.security.cert.X509Certificate

/**
 * Leaf certificates the trust manager refused because of the blocklist. WebView only reports a generic
 * "untrusted" SSL error for them, so this is how that error is told apart from an ordinary untrusted chain.
 */
interface CertificateBlocklistRejections {
    fun record(leaf: X509Certificate)
    fun contains(leaf: X509Certificate): Boolean
}

/**
 * Keeps only the most recent rejections: WebView raises its SSL error moments after the handshake fails, so a
 * short memory is enough, and bounding it keeps a hostile network from growing it without limit.
 */
class InMemoryCertificateBlocklistRejections(
    private val capacity: Int = 64,
) : CertificateBlocklistRejections {
    private val fingerprints = object : LinkedHashMap<String, Unit>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Unit>?): Boolean = size > capacity
    }

    @Synchronized
    override fun record(leaf: X509Certificate) {
        fingerprints[leaf.sha256FingerprintHex()] = Unit
    }

    @Synchronized
    override fun contains(leaf: X509Certificate): Boolean = fingerprints.containsKey(leaf.sha256FingerprintHex())
}

fun X509Certificate.sha256FingerprintHex(): String = MessageDigest.getInstance("SHA-256").digest(encoded).toHex()
