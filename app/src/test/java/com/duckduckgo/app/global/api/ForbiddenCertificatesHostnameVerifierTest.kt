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
import okhttp3.tls.HeldCertificate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLSession

class ForbiddenCertificatesHostnameVerifierTest {

    private val intermediate = HeldCertificate.Builder().certificateAuthority(0).build()
    private val leaf = HeldCertificate.Builder().signedBy(intermediate).addSubjectAlternativeName(HOSTNAME).build()
    private val session: SSLSession = mock {
        on { peerCertificates } doReturn arrayOf(leaf.certificate, intermediate.certificate)
    }

    @Test
    fun whenPeerChainHasNoForbiddenKeyThenAccepted() {
        val testee = ForbiddenCertificatesHostnameVerifier(forbiddenSpkiSha256s = emptySet(), delegate = acceptAll)

        assertTrue(testee.verify(HOSTNAME, session))
    }

    @Test
    fun whenIntermediateKeyIsForbiddenThenRejected() {
        val testee = ForbiddenCertificatesHostnameVerifier(
            forbiddenSpkiSha256s = setOf(intermediate.certificate.sha256Hash()),
            delegate = acceptAll,
        )

        assertFalse(testee.verify(HOSTNAME, session))
    }

    @Test
    fun whenLeafKeyIsForbiddenThenRejected() {
        val testee = ForbiddenCertificatesHostnameVerifier(
            forbiddenSpkiSha256s = setOf(leaf.certificate.sha256Hash()),
            delegate = acceptAll,
        )

        assertFalse(testee.verify(HOSTNAME, session))
    }

    @Test
    fun whenCertificateIsReissuedWithForbiddenKeyThenRejected() {
        val crossSigned = HeldCertificate.Builder()
            .certificateAuthority(0)
            .keyPair(intermediate.keyPair)
            .signedBy(HeldCertificate.Builder().certificateAuthority(1).build())
            .build()
        whenever(session.peerCertificates).thenReturn(arrayOf(leaf.certificate, crossSigned.certificate))
        val testee = ForbiddenCertificatesHostnameVerifier(
            forbiddenSpkiSha256s = setOf(intermediate.certificate.sha256Hash()),
            delegate = acceptAll,
        )

        assertFalse(testee.verify(HOSTNAME, session))
    }

    @Test
    fun whenDelegateRejectsThenRejected() {
        val testee = ForbiddenCertificatesHostnameVerifier(forbiddenSpkiSha256s = emptySet(), delegate = { _, _ -> false })

        assertFalse(testee.verify(HOSTNAME, session))
    }

    @Test
    fun whenDefaultDelegateThenHostnameIsStillVerified() {
        val testee = ForbiddenCertificatesHostnameVerifier(forbiddenSpkiSha256s = emptySet())

        assertTrue(testee.verify(HOSTNAME, session))
        assertFalse(testee.verify("other.example.com", session))
    }

    @Test
    fun whenCompromisedEmudhraKeysLoadedThenEightDistinctSha256Hashes() {
        val keys = ForbiddenCertificatesHostnameVerifier.COMPROMISED_EMUDHRA_INTERMEDIATES

        assertEquals(8, keys.size)
        assertTrue(keys.all { it.size == 32 })
    }

    private companion object {
        const val HOSTNAME = "example.com"
        val acceptAll = HostnameVerifier { _, _ -> true }
    }
}
