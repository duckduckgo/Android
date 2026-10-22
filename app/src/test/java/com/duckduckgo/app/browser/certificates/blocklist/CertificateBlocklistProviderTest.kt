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

import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.KeyStore
import java.security.Security
import java.security.cert.CertificateException
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Installs the provider into the JVM's real JCA so the delegation to the platform factory (SunJSSE here, Conscrypt
 * on a device) is exercised for real; only the Android-specific host-aware path is left to [BlocklistTrustManagerTest].
 */
class CertificateBlocklistProviderTest {
    private val root = HeldCertificate.Builder().commonName("root").certificateAuthority(1).rsa2048().build()
    private val intermediate = HeldCertificate.Builder().commonName("intermediate").certificateAuthority(0).signedBy(root).rsa2048().build()
    private val leaf = HeldCertificate.Builder().commonName("leaf").addSubjectAlternativeName("example.com").signedBy(intermediate).rsa2048().build()
    private val serverChain = arrayOf(leaf.certificate, intermediate.certificate)
    private val leafIssuedByRoot = HeldCertificate.Builder().commonName(
        "direct leaf",
    ).addSubjectAlternativeName("example.com").signedBy(root).rsa2048().build()

    private val blocklist = CertificateBlocklist(hashes = setOf(intermediate.certificate.spkiSha256Hex())).apply { enabledWhen { true } }

    @Before
    fun setUp() {
        Security.insertProviderAt(CertificateBlocklistProvider(blocklist, InMemoryCertificateBlocklistRejections()), 1)
    }

    @After
    fun tearDown() {
        Security.removeProvider(CertificateBlocklistProvider.NAME)
    }

    @Test
    fun whenInstalledThenDefaultFactoryComesFromTheProviderAndWrapsThePlatformTrustManager() {
        val factory = defaultFactory()

        assertEquals(CertificateBlocklistProvider.NAME, factory.provider.name)
        assertTrue(factory.trustManagers.single() is BlocklistTrustManager)
    }

    @Test
    fun whenChainIsCleanThenPlatformVerdictStands() {
        defaultTrustManager().checkServerTrusted(arrayOf(leafIssuedByRoot.certificate), "RSA")
    }

    @Test
    fun whenChainCarriesABlockedKeyThenItIsRejected() {
        assertThrows(CertificateException::class.java) { defaultTrustManager().checkServerTrusted(serverChain, "RSA") }
    }

    @Test
    fun whenChainIsUntrustedByThePlatformThenItIsStillRejected() {
        val otherRoot = HeldCertificate.Builder().commonName("other root").certificateAuthority(0).rsa2048().build()
        val stranger = HeldCertificate.Builder().commonName("stranger").addSubjectAlternativeName("example.com").signedBy(otherRoot).rsa2048().build()

        assertThrows(CertificateException::class.java) {
            defaultTrustManager().checkServerTrusted(arrayOf(stranger.certificate, otherRoot.certificate), "RSA")
        }
    }

    @Test
    fun whenInitialisedWithAKeyStoreThenAcceptedIssuersComeFromIt() {
        assertEquals(listOf(root.certificate), defaultTrustManager().acceptedIssuers.toList())
    }

    @Test
    fun whenProviderIsRemovedThenThePlatformFactoryIsInFrontAgain() {
        Security.removeProvider(CertificateBlocklistProvider.NAME)

        assertNotEquals(CertificateBlocklistProvider.NAME, defaultFactory().provider.name)
    }

    @Test
    fun whenInstallerRunsTwiceThenTheProviderIsInstalledOnceAtTheFront() {
        Security.removeProvider(CertificateBlocklistProvider.NAME)

        CertificateBlocklistInstaller.install()
        CertificateBlocklistInstaller.install()

        assertEquals(1, Security.getProviders().count { it.name == CertificateBlocklistProvider.NAME })
        assertEquals(CertificateBlocklistProvider.NAME, Security.getProviders().first().name)
    }

    private fun defaultFactory(): TrustManagerFactory {
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null)
            setCertificateEntry("root", root.certificate)
        }
        return TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(keyStore) }
    }

    private fun defaultTrustManager(): X509TrustManager = defaultFactory().trustManagers.filterIsInstance<X509TrustManager>().single()
}
