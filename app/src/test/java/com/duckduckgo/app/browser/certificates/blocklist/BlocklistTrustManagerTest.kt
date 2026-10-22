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

import android.net.http.X509TrustManagerExtensions
import okhttp3.tls.HeldCertificate
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.net.Socket
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.X509ExtendedTrustManager

class BlocklistTrustManagerTest {
    private val root = HeldCertificate.Builder().commonName("root").certificateAuthority(1).build()
    private val intermediate = HeldCertificate.Builder().commonName("intermediate").certificateAuthority(0).signedBy(root).build()
    private val leaf = HeldCertificate.Builder().commonName("leaf").addSubjectAlternativeName("example.com").signedBy(intermediate).build()
    private val serverChain = arrayOf(leaf.certificate, intermediate.certificate)
    private val verifiedChain = listOf(leaf.certificate, intermediate.certificate, root.certificate)

    private val delegate: X509ExtendedTrustManager = mock()
    private val delegateExtensions: X509TrustManagerExtensions = mock()
    private val rejections = InMemoryCertificateBlocklistRejections()

    private fun trustManager(vararg blockedHashes: String): BlocklistTrustManager {
        val blocklist = CertificateBlocklist(hashes = blockedHashes.toSet()).apply { enabledWhen { true } }
        return BlocklistTrustManager(delegate, blocklist, rejections, delegateExtensions)
    }

    @Test
    fun whenChainIsRejectedThenItsLeafIsRecorded() {
        val trustManager = trustManager(intermediate.certificate.spkiSha256Hex())

        assertThrows(CertificateException::class.java) { trustManager.checkServerTrusted(serverChain, "RSA") }

        assertTrue(rejections.contains(leaf.certificate))
    }

    @Test
    fun whenChainIsCleanThenNoLeafIsRecorded() {
        trustManager().checkServerTrusted(serverChain, "RSA")

        assertFalse(rejections.contains(leaf.certificate))
    }

    @Test
    fun whenVerifiedChainIsCleanThenHostAwareCheckReturnsIt() {
        whenever(delegateExtensions.checkServerTrusted(serverChain, "RSA", "example.com")).thenReturn(verifiedChain)

        assertEquals(verifiedChain, trustManager().checkServerTrusted(serverChain, "RSA", "example.com"))
    }

    @Test
    fun whenOnlyTheVerifiedChainCarriesABlockedKeyThenHostAwareCheckStillRejects() {
        // the trust anchor is never in the server-sent chain, so blocking it proves the verified chain is the one inspected
        whenever(delegateExtensions.checkServerTrusted(serverChain, "RSA", "example.com")).thenReturn(verifiedChain)
        val trustManager = trustManager(root.certificate.spkiSha256Hex())

        assertThrows(CertificateException::class.java) { trustManager.checkServerTrusted(serverChain, "RSA", "example.com") }
    }

    @Test
    fun whenDelegateRejectsThenItsRejectionPropagates() {
        whenever(delegateExtensions.checkServerTrusted(serverChain, "RSA", "example.com")).thenThrow(CertificateException("untrusted"))

        val error = assertThrows(CertificateException::class.java) { trustManager().checkServerTrusted(serverChain, "RSA", "example.com") }
        assertEquals("untrusted", error.message)
    }

    @Test
    fun whenServerChainIsCleanThenPlainCheckDelegates() {
        trustManager().checkServerTrusted(serverChain, "RSA")

        verify(delegate).checkServerTrusted(serverChain, "RSA")
    }

    @Test
    fun whenServerChainCarriesABlockedKeyThenPlainCheckRejects() {
        val trustManager = trustManager(intermediate.certificate.spkiSha256Hex())

        assertThrows(CertificateException::class.java) { trustManager.checkServerTrusted(serverChain, "RSA") }
    }

    @Test
    fun whenSocketCheckChainIsCleanThenItIsForwardedToTheDelegate() {
        val socket: Socket = mock()

        trustManager().checkServerTrusted(serverChain, "RSA", socket)

        verify(delegate).checkServerTrusted(serverChain, "RSA", socket)
    }

    @Test
    fun whenSocketCheckChainCarriesABlockedKeyThenItRejects() {
        val trustManager = trustManager(intermediate.certificate.spkiSha256Hex())

        assertThrows(CertificateException::class.java) { trustManager.checkServerTrusted(serverChain, "RSA", mock<Socket>()) }
    }

    @Test
    fun clientChecksAndAcceptedIssuersAreDelegatedUntouched() {
        whenever(delegate.acceptedIssuers).thenReturn(arrayOf(root.certificate))
        val trustManager = trustManager(root.certificate.spkiSha256Hex())

        trustManager.checkClientTrusted(serverChain, "RSA")

        verify(delegate).checkClientTrusted(serverChain, "RSA")
        assertArrayEquals(arrayOf(root.certificate), trustManager.acceptedIssuers)
    }

    @Test
    fun isSameTrustConfigurationIsDelegated() {
        whenever(delegateExtensions.isSameTrustConfiguration("a.example", "b.example")).thenReturn(true)

        assertTrue(trustManager().isSameTrustConfiguration("a.example", "b.example"))
    }

    @Test
    fun hostAwareOverloadsKeepTheSignaturesThatX509TrustManagerExtensionsLooksUp() {
        val type = BlocklistTrustManager::class.java
        val certificates = Array<X509Certificate>::class.java

        assertEquals(
            List::class.java,
            type.getMethod("checkServerTrusted", certificates, String::class.java, String::class.java).returnType,
        )
        assertEquals(
            List::class.java,
            type.getMethod(
                "checkServerTrusted",
                certificates,
                ByteArray::class.java,
                ByteArray::class.java,
                String::class.java,
                String::class.java,
            ).returnType,
        )
        assertEquals(
            java.lang.Boolean.TYPE,
            type.getMethod("isSameTrustConfiguration", String::class.java, String::class.java).returnType,
        )
    }
}
