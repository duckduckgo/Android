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
import com.duckduckgo.app.browser.certificates.toX509Certificate
import com.duckduckgo.app.browser.certificates.CertificateType
import com.duckduckgo.app.browser.certificates.LetsEncryptCertificateProvider
import timber.log.Timber
import java.security.cert.*

interface TrustedCertificateStore {
    fun validateSslCertificateChain(sslCertificate: SslCertificate)
}

class TrustedCertificateStoreImpl(
    private val letsEncryptCertificateProvider: LetsEncryptCertificateProvider
) : TrustedCertificateStore {

    /**
     * Validates the given SSL certificate chaing
     * @param sslCertificate the SSL certificate to be validated
     *
     * @throws CertificateExpiredException when any issuer in the [sslCertificate] chain is expired
     * @throws CertificateNotYetValidException when any issuer in the [sslCertificate] chain is not yet valid
     * @throws CertificateException when we could not validate the [sslCertificate] certificate chain
     */
    override fun validateSslCertificateChain(sslCertificate: SslCertificate) {
        val issuer = letsEncryptCertificateProvider.findByCname(sslCertificate.issuedBy.cName)
        issuer?.let {
            // first validate
            validate(sslCertificate.toX509Certificate(), it.certificate())
            // then check if root
            if (it.type() == CertificateType.Root) {
                Timber.d("Certificate Trusted anchor validated!")
                return
            }
            Timber.d("Intermediate certificated validated!")
            validateSslCertificateChain(SslCertificate(it.certificate() as X509Certificate))

            // certificate chain validated
            return
        }

        throw CertificateException("Unable to find certificate trusted anchor")
    }

    @Throws(CertificateExpiredException::class, CertificateNotYetValidException::class)
    private fun validate(cert: Certificate, issuerCertificate: Certificate) {
        if (issuerCertificate.type == "X.509") {
            (issuerCertificate as X509Certificate).checkValidity()
        }

        // method silently returns, throws when verification fails
        cert.verify(issuerCertificate.publicKey)
    }
}
