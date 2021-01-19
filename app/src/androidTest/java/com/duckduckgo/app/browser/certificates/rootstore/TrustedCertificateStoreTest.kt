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

import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.browser.certificates.LetsEncryptCertificateProvider
import com.duckduckgo.app.browser.certificates.LetsEncryptCertificateProviderImpl
import org.junit.Assert.assertEquals
import org.junit.Test

class TrustedCertificateStoreTest {

    private val isrgRootX1 = IsrgRootX1(InstrumentationRegistry.getInstrumentation().targetContext.applicationContext)
    private val letsEncryptAuthorityX3 = LetsEncryptAuthorityX3(InstrumentationRegistry.getInstrumentation().targetContext.applicationContext)
    private val letsEncryptR3 = LetsEncryptR3(InstrumentationRegistry.getInstrumentation().targetContext.applicationContext)

    private val subscriberCertificateX3 = """
        -----BEGIN CERTIFICATE-----
        MIIEezCCA2OgAwIBAgISBJ0FI1PHEJRX2NGBLO0cbml5MA0GCSqGSIb3DQEBCwUA
        MEoxCzAJBgNVBAYTAlVTMRYwFAYDVQQKEw1MZXQncyBFbmNyeXB0MSMwIQYDVQQD
        ExpMZXQncyBFbmNyeXB0IEF1dGhvcml0eSBYMzAeFw0yMDEyMDIwMDA5MzBaFw0y
        MTAzMDIwMDA5MzBaMBsxGTAXBgNVBAMTEG15b3N0dWRpb2JibC5jb20wggEiMA0G
        CSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQCzeGsPDtKIWTK4o+P/NFglfCh504n/
        KD+cxytW11c0Mvnf8KJMEYKWdXz8jeES/6LJc20bdtrAwShI2dT0WGdciC0mDuAW
        9MmI4iC6bhbmyngjM/XGMKWfobY9RzX5/WNi4urrAk6iHoF3Fzq4D4LSnuQKzvKR
        Cm0w9hY6CTjVD3cdK65JoaCmtCbKavJRJ+vt0tLVB5Fe4I098wqpMCwL7MLnrsR/
        /gXN47Jkygz498nVdjeolpfudxUmUYfINQ4s0feX5OlYQHciVUYvWR2BfGyxrZJD
        olHd5oz9HLhL+mYmE+h1MkwOUFfz9Q1x8k6p2JRdzlqCV4/rFV5UxD6rAgMBAAGj
        ggGIMIIBhDAOBgNVHQ8BAf8EBAMCBaAwHQYDVR0lBBYwFAYIKwYBBQUHAwEGCCsG
        AQUFBwMCMAwGA1UdEwEB/wQCMAAwHQYDVR0OBBYEFCLxEcJYkXBwO0ZhPQoqALAp
        5USxMB8GA1UdIwQYMBaAFKhKamMEfd265tE5t6ZFZe/zqOyhMG8GCCsGAQUFBwEB
        BGMwYTAuBggrBgEFBQcwAYYiaHR0cDovL29jc3AuaW50LXgzLmxldHNlbmNyeXB0
        Lm9yZzAvBggrBgEFBQcwAoYjaHR0cDovL2NlcnQuaW50LXgzLmxldHNlbmNyeXB0
        Lm9yZy8wMQYDVR0RBCowKIIQbXlvc3R1ZGlvYmJsLmNvbYIUd3d3Lm15b3N0dWRp
        b2JibC5jb20wTAYDVR0gBEUwQzAIBgZngQwBAgEwNwYLKwYBBAGC3xMBAQEwKDAm
        BggrBgEFBQcCARYaaHR0cDovL2Nwcy5sZXRzZW5jcnlwdC5vcmcwEwYKKwYBBAHW
        eQIEAwEB/wQCBQAwDQYJKoZIhvcNAQELBQADggEBAHjtggS6WQDFSyb/s64VsJ90
        a6mSRU3Z24ymzO6s4BX6PFFoT2rTwsWUTtsM+BD+U0GAzMtRPFaVqHt4MjTejBhb
        9M71VsErcL3E6qFjB/hZKVTe895AGSCt/8S30ysP1DBN/3keQgF7GNpwW/xSEuy8
        uipxj127AVVzNQK/V+oxt3OsbfJsuFD6MaJDNYzBLBWS+NevQMLiIDmuNAw17KpS
        ZaoHW3V/C+sI7UzqfzOfPKyyhXnGvTGl0tmS5C3rh9cscRXCmVUNMN/rT+2jZiqR
        CjXQf6fdejKvL5Iou9Ii9klgC9SNm2+fCEQ03mCduGwuoTmEpxYcz1aREYXV0z0=
        -----END CERTIFICATE-----
    """.trimIndent().parsePemCertificate()?.toSslCertificate()

    private val subscriberCertificateR3 = """
        -----BEGIN CERTIFICATE-----
        MIIFITCCBAmgAwIBAgISA54/GQdACBpOFGgRCx58MRfrMA0GCSqGSIb3DQEBCwUA
        MDIxCzAJBgNVBAYTAlVTMRYwFAYDVQQKEw1MZXQncyBFbmNyeXB0MQswCQYDVQQD
        EwJSMzAeFw0yMDEyMDgyMjAyNTZaFw0yMTAzMDgyMjAyNTZaMCkxJzAlBgNVBAMT
        HmF1dG9kaXNjb3Zlci5uYXRhbmJ1ZW5vLmNvbS5icjCCASIwDQYJKoZIhvcNAQEB
        BQADggEPADCCAQoCggEBAKegcMiUd5XTxkyJA40hHF29mvRH5gb4GMPudd2ERiCz
        w6YuHwq1/TDgkZui2iBbqGjAG/diDkLHBGWNBfFDYqxCYmMNdMA+ldMaVk2T2rIs
        3uJpAgaoUQLsPm8MXBjDjj9O6cEblGHO8/Gcxf2E1HZDkGxLPtd79gmH83PFS9Lw
        ekyx+6ISC3RaMdxtAStR4H0U05plFqsr6vsorzMJtH8oAOfGIOwo6+xgPfkDK17F
        0z9ggcbK2KkzgUBoT86Pj7Bwk/WjIBU3XnKIYGINPV+nSybdnfvx9xU0w6+fepyM
        A2Re9+gHW/znfcpfYOGelYr4epzjGH+EBIg60w9juiMCAwEAAaOCAjgwggI0MA4G
        A1UdDwEB/wQEAwIFoDAdBgNVHSUEFjAUBggrBgEFBQcDAQYIKwYBBQUHAwIwDAYD
        VR0TAQH/BAIwADAdBgNVHQ4EFgQUuM+fOk8E7cmrgLs7lB/Ml9hXsYMwHwYDVR0j
        BBgwFoAUFC6zF7dYVsuuUAlA5h+vnYsUwsYwVQYIKwYBBQUHAQEESTBHMCEGCCsG
        AQUFBzABhhVodHRwOi8vcjMuby5sZW5jci5vcmcwIgYIKwYBBQUHMAKGFmh0dHA6
        Ly9yMy5pLmxlbmNyLm9yZy8wgfoGA1UdEQSB8jCB74IeYXV0b2Rpc2NvdmVyLm5h
        dGFuYnVlbm8uY29tLmJyghhjcGFuZWwubmF0YW5idWVuby5jb20uYnKCHWNwY2Fs
        ZW5kYXJzLm5hdGFuYnVlbm8uY29tLmJyghxjcGNvbnRhY3RzLm5hdGFuYnVlbm8u
        Y29tLmJyghZtYWlsLm5hdGFuYnVlbm8uY29tLmJyghFuYXRhbmJ1ZW5vLmNvbS5i
        coIZd2ViZGlzay5uYXRhbmJ1ZW5vLmNvbS5icoIZd2VibWFpbC5uYXRhbmJ1ZW5v
        LmNvbS5icoIVd3d3Lm5hdGFuYnVlbm8uY29tLmJyMEwGA1UdIARFMEMwCAYGZ4EM
        AQIBMDcGCysGAQQBgt8TAQEBMCgwJgYIKwYBBQUHAgEWGmh0dHA6Ly9jcHMubGV0
        c2VuY3J5cHQub3JnMBMGCisGAQQB1nkCBAMBAf8EAgUAMA0GCSqGSIb3DQEBCwUA
        A4IBAQAOSMa/n+FQQhJSWY4IejRX77cN/ymP4PdFz8Bajm8X0YKWzciEoT8CaFgZ
        f81RTL2IuXAwG30L30iSp4RNF0UFx1Rm+bd0V4Nmp7otFHMmtlVRb4paBh3P2PEA
        iFvElfWb1SQSdgdA1Vo9qRUTkW6s4reBBpigS1rO+c4B2TmgljCoZl9n6MXwHkgs
        HdFOllSDQ15pIqQNnpLbuCEAyCRQyKJQ4GhZpG0DqRC7dOMhStLlk5u7p0FcL+9Z
        HBRf5CyN1uLFPbJfV40usywIeH5croMPa7zqT7ZRWWY2sCbQtbitSZhBnGsNdEX4
        Fsgf8m1G7tgWhOQ8bQ88coRwmtGZ
        -----END CERTIFICATE-----
    """.trimIndent().parsePemCertificate()?.toSslCertificate()

    @Test
    fun whenValidateSslCertificateChainWithX3TrustedChainThenSuccess() {
        val letsEncryptCertificateProvider: LetsEncryptCertificateProvider = LetsEncryptCertificateProviderImpl(
            setOf(isrgRootX1, letsEncryptAuthorityX3, letsEncryptR3)
        )
        val trustedCertificateStore = TrustedCertificateStoreImpl(letsEncryptCertificateProvider)

        assertEquals(
            CertificateValidationState.TrustedChain,
            trustedCertificateStore.validateSslCertificateChain(subscriberCertificateX3!!)
        )
    }

    @Test
    fun whenValidateSslCertificateChainWithX3UnTrustedChainThenError() {
        val letsEncryptCertificateProvider: LetsEncryptCertificateProvider = LetsEncryptCertificateProviderImpl(
            setOf(isrgRootX1, letsEncryptR3)
        )
        val trustedCertificateStore = TrustedCertificateStoreImpl(letsEncryptCertificateProvider)

        assertEquals(
            CertificateValidationState.UntrustedChain,
            trustedCertificateStore.validateSslCertificateChain(subscriberCertificateX3!!)
        )
    }

    @Test
    fun whenValidateSslCertificateChainWithR3TrustedChainThenSuccess() {
        val letsEncryptCertificateProvider: LetsEncryptCertificateProvider = LetsEncryptCertificateProviderImpl(
            setOf(isrgRootX1, letsEncryptAuthorityX3, letsEncryptR3)
        )
        val trustedCertificateStore = TrustedCertificateStoreImpl(letsEncryptCertificateProvider)

        assertEquals(
            CertificateValidationState.TrustedChain,
            trustedCertificateStore.validateSslCertificateChain(subscriberCertificateR3!!)
        )
    }

    @Test
    fun whenValidateSslCertificateChainWithR3UnTrustedChainThenError() {
        val letsEncryptCertificateProvider: LetsEncryptCertificateProvider = LetsEncryptCertificateProviderImpl(
            setOf(isrgRootX1, letsEncryptAuthorityX3)
        )
        val trustedCertificateStore = TrustedCertificateStoreImpl(letsEncryptCertificateProvider)

        assertEquals(
            CertificateValidationState.UntrustedChain,
            trustedCertificateStore.validateSslCertificateChain(subscriberCertificateR3!!)
        )
    }

    @Test
    fun whenValidateSslCertificateChainWithMissingRootThenError() {
        val letsEncryptCertificateProvider: LetsEncryptCertificateProvider = LetsEncryptCertificateProviderImpl(
            setOf(letsEncryptAuthorityX3, letsEncryptR3)
        )
        val trustedCertificateStore = TrustedCertificateStoreImpl(letsEncryptCertificateProvider)

        assertEquals(
            CertificateValidationState.UntrustedChain,
            trustedCertificateStore.validateSslCertificateChain(subscriberCertificateR3!!)
        )
    }
}
