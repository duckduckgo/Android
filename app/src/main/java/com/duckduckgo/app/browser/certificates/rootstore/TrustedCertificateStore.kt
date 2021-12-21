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
import com.duckduckgo.app.browser.certificates.CertificateType
import com.duckduckgo.app.browser.certificates.CertificateTypes
import com.duckduckgo.app.browser.certificates.LetsEncryptCertificateProvider
import com.duckduckgo.app.browser.certificates.toX509Certificate
import java.security.cert.*
import timber.log.Timber

interface TrustedCertificateStore {
    fun validateSslCertificateChain(sslCertificate: SslCertificate): CertificateValidationState
}

sealed class CertificateValidationState {
    object IssuerExpired : CertificateValidationState()
    object IssuerNotYetValid : CertificateValidationState()
    object UntrustedChain : CertificateValidationState()
    object TrustedChain : CertificateValidationState()
}

class TrustedCertificateStoreImpl(
    private val letsEncryptCertificateProvider: LetsEncryptCertificateProvider
) : TrustedCertificateStore {

    /**
     * Validates the given SSL certificate chain
     * @param sslCertificate the SSL certificate to be validated
     *
     * @return [IssuerExpired] when any issuer in the [sslCertificate] chain is expired
     * @return [IssuerNotYetValid] when any issuer in the [sslCertificate] chain is not yet valid
     * @return [UntrustedChain] when we could not validate the [sslCertificate] certificate chain
     * @return [TrustedChain] when SSL certificated chain is validated
     */
    override fun validateSslCertificateChain(
        sslCertificate: SslCertificate
    ): CertificateValidationState {
        return try {
            validateSslCertificateChainInternal(sslCertificate)
            CertificateValidationState.TrustedChain
        } catch (t: Throwable) {
            when (t) {
                is CertificateExpiredException -> CertificateValidationState.IssuerExpired
                is CertificateNotYetValidException -> CertificateValidationState.IssuerNotYetValid
                else -> CertificateValidationState.UntrustedChain
            }
        }
    }

    @Throws(
        CertificateException::class,
        CertificateExpiredException::class,
        CertificateNotYetValidException::class)
    private fun validateSslCertificateChainInternal(sslCertificate: SslCertificate) {
        val issuer = letsEncryptCertificateProvider.findByCname(sslCertificate.issuedBy.cName)
        issuer?.let {
            // first validate
            validate(sslCertificate.toX509Certificate(), it.certificate())
            // then check if root
            if (it.type() == CertificateType.Root) {
                Timber.d("Certificate Trusted anchor validated!")
                return
            }
            Timber.d("Intermediate certificate validated!")
            validateSslCertificateChainInternal(SslCertificate(it.certificate() as X509Certificate))

            // certificate chain validated
            return
        }

        throw CertificateException("Unable to find certificate trusted anchor")
    }

    @Throws(CertificateExpiredException::class, CertificateNotYetValidException::class)
    private fun validate(cert: Certificate, issuerCertificate: Certificate) {
        if (issuerCertificate.type == CertificateTypes.X509) {
            (issuerCertificate as X509Certificate).checkValidity()
        }

        // method silently returns, throws when verification fails
        cert.verify(issuerCertificate.publicKey)
    }
}
