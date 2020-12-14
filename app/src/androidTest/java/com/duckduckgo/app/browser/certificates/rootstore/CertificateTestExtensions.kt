/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.app.browser.certificates.rootstore

import android.net.http.SslCertificate
import com.duckduckgo.app.browser.certificates.CertificateTypes.Companion.X509
import okio.ByteString
import okio.ByteString.Companion.decodeBase64
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

fun String.parsePemCertificate(): ByteString? {
    return this
        .replace("-----BEGIN CERTIFICATE-----", "")
        .replace("-----END CERTIFICATE-----", "")
        .decodeBase64()
}

fun ByteString.toX509Certificate(): X509Certificate {
    val certificateFactory = CertificateFactory.getInstance(X509)
    return certificateFactory.generateCertificate(this.toByteArray().inputStream()) as X509Certificate
}

fun ByteString.toSslCertificate(): SslCertificate {
    return SslCertificate(this.toX509Certificate())
}
