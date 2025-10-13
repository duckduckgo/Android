/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.privacy.dashboard.impl.ui

import android.net.http.SslCertificate
import android.os.Build.VERSION_CODES
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.privacy.dashboard.impl.ui.PublicKeyInfoMapper.PublicKeyInfo
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger
import java.security.cert.X509Certificate
import java.security.interfaces.DSAParams
import java.security.interfaces.DSAPublicKey
import java.security.interfaces.ECPublicKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.ECParameterSpec
import java.util.*

class PublicKeyInfoMapperTest {

    private val androidQAppBuildConfig = mock<AppBuildConfig>().apply {
        whenever(this.sdkInt).thenReturn(VERSION_CODES.Q)
    }

    @Test
    fun whenRSASslCertificateThenReturnRsaPublicKeyInfo() {
        val expected = PublicKeyInfo(
            type = "rsa",
            bitSize = 1,
        )
        val testee = PublicKeyInfoMapper(androidQAppBuildConfig)
        val publicKeyInfo = testee.mapFrom(aRSASslCertificate())

        assertEquals(expected, publicKeyInfo)
    }

    @Test
    fun whenDSASslCertificateThenReturnDSAPublicKeyInfo() {
        val expected = PublicKeyInfo(
            type = "dsa",
            bitSize = 1,
        )
        val testee = PublicKeyInfoMapper(androidQAppBuildConfig)

        val publicKeyInfo = testee.mapFrom(aDSASslCertificate())

        assertEquals(expected, publicKeyInfo)
    }

    @Test
    fun whenDSAWithParamsSslCertificateThenReturnDSAPublicKeyInfo() {
        val expected = PublicKeyInfo(
            type = "dsa",
            bitSize = 1,
        )
        val testee = PublicKeyInfoMapper(androidQAppBuildConfig)

        val publicKeyInfo = testee.mapFrom(aDSASslCertificate(hasParams = true))

        assertEquals(expected, publicKeyInfo)
    }

    @Test
    fun whenECSslCertificateThenReturnECPublicKeyInfo() {
        val expected = PublicKeyInfo(
            type = "ec",
            bitSize = 1,
        )
        val testee = PublicKeyInfoMapper(androidQAppBuildConfig)

        val publicKeyInfo = testee.mapFrom(aECSslCertificate())

        assertEquals(expected, publicKeyInfo)
    }
}

fun aRSASslCertificate(): SslCertificate {
    val certificate = mock<X509Certificate>().apply {
        val key = mock<RSAPublicKey>().apply {
            whenever(this.algorithm).thenReturn("rsa")
            whenever(this.modulus).thenReturn(BigInteger("1"))
        }
        whenever(this.publicKey).thenReturn(key)
    }
    return mock<SslCertificate>().apply {
        whenever(x509Certificate).thenReturn(certificate)
    }
}

fun aDSASslCertificate(
    hasParams: Boolean = false,
): SslCertificate {
    val certificate = mock<X509Certificate>().apply {
        val key = mock<DSAPublicKey>().apply {
            whenever(this.algorithm).thenReturn("dsa")
            if (hasParams) {
                val params = mock<DSAParams>().apply {
                    whenever(this.p).thenReturn(BigInteger("1"))
                }
                whenever(this.params).thenReturn(params)
            } else {
                whenever(this.y).thenReturn(BigInteger("1"))
            }
        }
        whenever(this.publicKey).thenReturn(key)
    }
    return mock<SslCertificate>().apply {
        whenever(x509Certificate).thenReturn(certificate)
    }
}

fun aECSslCertificate(): SslCertificate {
    val params = mock<ECParameterSpec>().apply {
        whenever(this.order).thenReturn(BigInteger("1"))
    }
    val certificate = mock<X509Certificate>().apply {
        val key = mock<ECPublicKey>().apply {
            whenever(this.algorithm).thenReturn("ec")
            whenever(this.params).thenReturn(params)
        }
        whenever(this.publicKey).thenReturn(key)
    }
    return mock<SslCertificate>().apply {
        whenever(x509Certificate).thenReturn(certificate)
    }
}
