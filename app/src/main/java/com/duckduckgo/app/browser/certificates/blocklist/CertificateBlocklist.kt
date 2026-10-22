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
 * Keyed by SHA-256 of the SubjectPublicKeyInfo (the hash behind `sha256/…` certificate pins) rather than by certificate
 * fingerprint, so a leaked key is caught in every certificate that carries it, cross-signs included.
 */
class CertificateBlocklist(hashes: Set<String> = COMPROMISED_EMUDHRA_INTERMEDIATES) {
    private val hashes: Set<String> = hashes.mapTo(HashSet()) { it.lowercase() }

    @Volatile
    private var isEnabled: () -> Boolean = { false }

    /**
     * Installs the kill switch. It is consulted only once a chain has matched, so a feature toggle can sit behind it
     * without being read on every handshake, and its current value applies to every match. Until it is installed
     * nothing is blocked, so the window before the feature toggle is available behaves like the toggle's default.
     */
    fun enabledWhen(isEnabled: () -> Boolean) {
        this.isEnabled = isEnabled
    }

    /** Returns the first certificate in [chain] carrying a blocklisted public key, or null when the chain is clean. */
    fun firstBlocked(chain: Iterable<X509Certificate>): X509Certificate? {
        if (hashes.isEmpty()) return null
        val blocked = chain.firstOrNull { certificate -> certificate.spkiSha256Hex() in hashes } ?: return null
        return if (isEnabled()) blocked else null
    }

    companion object {
        /**
         * The eight eMudhra intermediates whose private keys leaked (CVE-2026-111943).
         * https://groups.google.com/a/mozilla.org/g/dev-security-policy/c/0R3XLJL6FuE/m/0XofdebrAAAJ
         */
        val COMPROMISED_EMUDHRA_INTERMEDIATES: Set<String> = setOf(
            "43bffa19d6ef651451f6841e019931e96f6f2eaeff28ab4b6f1ce4576de2717b",
            "49dd353dc2b919a3770645117d0e60c16115e7d1bc259eefe734b00ea434fe5f",
            "5c0660a2b164910d8ff589c18e14552e9e0ec237dcefe2cb736d41a4811f81a0",
            "63ecefee431d49523bc1136c4e72d0206a477086caf606cd5cf025686c109cf8",
            "92c063d67e8f14db71d288e11266ffdaf69cbf115f7bab14f3bc2f68d25e0e30",
            "e0254c7fcdc265e2f0f8a428dabb75ab4ae039b4872d62f5ee57f0877d5a8a41",
            "ebed6475c2c2d80fcb199f3fe8dd6e6b3ce60b77eb15d4e42c3dec0c6f5b5105",
            "ec4ffa1ad0ac88081314b7aaffef6a91f97cd57291bb5ba686c5415f5eb4f6b6",
        )
    }
}

fun X509Certificate.spkiSha256Hex(): String = MessageDigest.getInstance("SHA-256").digest(publicKey.encoded).toHex()

internal fun ByteArray.toHex(): String = joinToString(separator = "") { byte -> "%02x".format(byte) }
