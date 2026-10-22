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

import okhttp3.CertificatePinner.Companion.sha256Hash
import okhttp3.tls.HeldCertificate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CertificateBlocklistTest {
    private val root = HeldCertificate.Builder().commonName("root").certificateAuthority(1).build()
    private val intermediate = HeldCertificate.Builder().commonName("intermediate").certificateAuthority(0).signedBy(root).build()
    private val leaf = HeldCertificate.Builder().commonName("leaf").addSubjectAlternativeName("example.com").signedBy(intermediate).build()
    private val chain = listOf(leaf.certificate, intermediate.certificate, root.certificate)

    @Test
    fun whenNoKeyIsBlocklistedThenChainIsClean() {
        val blocklist = enabledBlocklist(hashes = emptySet())

        assertNull(blocklist.firstBlocked(chain))
    }

    @Test
    fun whenIntermediateKeyIsBlocklistedThenIntermediateIsReturned() {
        val blocklist = enabledBlocklist(hashes = setOf(intermediate.certificate.spkiSha256Hex()))

        assertSame(intermediate.certificate, blocklist.firstBlocked(chain))
    }

    @Test
    fun whenHashIsUpperCaseThenItStillMatches() {
        val blocklist = enabledBlocklist(hashes = setOf(root.certificate.spkiSha256Hex().uppercase()))

        assertSame(root.certificate, blocklist.firstBlocked(chain))
    }

    @Test
    fun whenBlocklistedKeyIsReusedInAnotherCertificateThenThatCertificateIsBlockedToo() {
        val crossSign = HeldCertificate.Builder()
            .keyPair(intermediate.keyPair)
            .commonName("intermediate cross-sign")
            .certificateAuthority(0)
            .signedBy(root)
            .build()
        val blocklist = enabledBlocklist(hashes = setOf(intermediate.certificate.spkiSha256Hex()))

        assertSame(crossSign.certificate, blocklist.firstBlocked(listOf(leaf.certificate, crossSign.certificate)))
    }

    @Test
    fun whenNoKillSwitchIsInstalledThenNothingIsBlocked() {
        val blocklist = CertificateBlocklist(hashes = setOf(intermediate.certificate.spkiSha256Hex()))

        assertNull(blocklist.firstBlocked(chain))
    }

    @Test
    fun whenKillSwitchIsOffThenAMatchingChainIsNotBlockedUntilItIsOnAgain() {
        val blocklist = CertificateBlocklist(hashes = setOf(intermediate.certificate.spkiSha256Hex()))
        var enabled = false
        blocklist.enabledWhen { enabled }

        assertNull(blocklist.firstBlocked(chain))

        enabled = true
        assertSame(intermediate.certificate, blocklist.firstBlocked(chain))
    }

    @Test
    fun killSwitchIsOnlyConsultedWhenAChainMatches() {
        val blocklist = CertificateBlocklist(hashes = setOf(intermediate.certificate.spkiSha256Hex()))
        var consulted = 0
        blocklist.enabledWhen {
            consulted++
            true
        }

        blocklist.firstBlocked(listOf(leaf.certificate, root.certificate))
        assertEquals(0, consulted)

        blocklist.firstBlocked(chain)
        assertEquals(1, consulted)
    }

    @Test
    fun spkiHashMatchesTheHashUsedByCertificatePins() {
        assertEquals(leaf.certificate.sha256Hash().hex(), leaf.certificate.spkiSha256Hex())
    }

    @Test
    fun defaultListIsTheCompromisedEmudhraKeysAndDoesNotMatchUnrelatedChains() {
        val blocklist = CertificateBlocklist().apply { enabledWhen { true } }

        assertEquals(8, CertificateBlocklist.COMPROMISED_EMUDHRA_INTERMEDIATES.size)
        assertTrue(CertificateBlocklist.COMPROMISED_EMUDHRA_INTERMEDIATES.all { it.matches(Regex("[0-9a-f]{64}")) })
        assertNull(blocklist.firstBlocked(chain))
    }

    private fun enabledBlocklist(hashes: Set<String>) = CertificateBlocklist(hashes).apply { enabledWhen { true } }
}
