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

package com.duckduckgo.app.global.api

import okhttp3.CertificatePinner.Companion.sha256Hash
import okhttp3.internal.tls.OkHostnameVerifier
import okio.ByteString
import okio.ByteString.Companion.decodeHex
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLSession

/**
 * Rejects TLS connections whose peer chain contains a compromised public key.
 *
 * Android only distrusts a CA through an OS or Conscrypt update, which many devices get late or never, so
 * revoked intermediates can stay trusted by the platform. Hostname verification runs during the handshake,
 * before any request bytes are written, which is why the check lives here rather than in an interceptor.
 */
class ForbiddenCertificatesHostnameVerifier(
    private val forbiddenSpkiSha256s: Set<ByteString> = COMPROMISED_EMUDHRA_INTERMEDIATES,
    private val delegate: HostnameVerifier = OkHostnameVerifier,
) : HostnameVerifier {

    override fun verify(hostname: String, session: SSLSession): Boolean {
        return delegate.verify(hostname, session) && !hasForbiddenPeerCertificate(session)
    }

    private fun hasForbiddenPeerCertificate(session: SSLSession): Boolean {
        return session.peerCertificates.any { certificate ->
            certificate is X509Certificate && certificate.sha256Hash() in forbiddenSpkiSha256s
        }
    }

    companion object {
        /**
         * SPKI SHA-256 of the eight eMudhra intermediates whose private keys leaked (CVE-2026-111943).
         * Matching on the key also covers the Sectigo cross-signed certificates that share it.
         * https://groups.google.com/a/mozilla.org/g/dev-security-policy/c/0R3XLJL6FuE/m/0XofdebrAAAJ
         */
        val COMPROMISED_EMUDHRA_INTERMEDIATES: Set<ByteString> = setOf(
            "43bffa19d6ef651451f6841e019931e96f6f2eaeff28ab4b6f1ce4576de2717b".decodeHex(),
            "49dd353dc2b919a3770645117d0e60c16115e7d1bc259eefe734b00ea434fe5f".decodeHex(),
            "5c0660a2b164910d8ff589c18e14552e9e0ec237dcefe2cb736d41a4811f81a0".decodeHex(),
            "63ecefee431d49523bc1136c4e72d0206a477086caf606cd5cf025686c109cf8".decodeHex(),
            "92c063d67e8f14db71d288e11266ffdaf69cbf115f7bab14f3bc2f68d25e0e30".decodeHex(),
            "e0254c7fcdc265e2f0f8a428dabb75ab4ae039b4872d62f5ee57f0877d5a8a41".decodeHex(),
            "ebed6475c2c2d80fcb199f3fe8dd6e6b3ce60b77eb15d4e42c3dec0c6f5b5105".decodeHex(),
            "ec4ffa1ad0ac88081314b7aaffef6a91f97cd57291bb5ba686c5415f5eb4f6b6".decodeHex(),
        )
    }
}
