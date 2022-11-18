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

import android.annotation.SuppressLint
import android.net.http.SslCertificate
import android.os.Build.VERSION_CODES
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import java.security.cert.X509Certificate
import java.security.interfaces.DSAPublicKey
import java.security.interfaces.ECPublicKey
import java.security.interfaces.RSAPublicKey
import javax.inject.Inject

class PublicKeyInfoMapper @Inject constructor(
    private val appBuildConfig: AppBuildConfig,
) {

    fun mapFrom(sslCertificate: SslCertificate): PublicKeyInfo? {
        return sslCertificate.publicKeyInfo()
    }

    @SuppressLint("NewApi")
    private fun SslCertificate.publicKeyInfo(): PublicKeyInfo? {
        if (appBuildConfig.sdkInt < VERSION_CODES.Q) return null

        return this.x509Certificate?.let { it ->
            val bitSize = certificateBitSize(it)
            PublicKeyInfo(
                type = it.publicKey.algorithm,
                bitSize = bitSize,
            )
        }
    }

    private fun SslCertificate.certificateBitSize(it: X509Certificate) = when (val publicKey = it.publicKey) {
        is RSAPublicKey -> {
            publicKey.modulus.bitLength()
        }
        is DSAPublicKey -> {
            runCatching {
                publicKey.params?.let {
                    it.p.bitLength()
                } ?: publicKey.y.bitLength()
            }.getOrNull()
        }
        is ECPublicKey -> {
            runCatching {
                publicKey.params.order.bitLength()
            }.getOrNull()
        }
        else -> null
    }

    data class PublicKeyInfo(
        val blockSize: Int? = null,
        val canEncrypt: Boolean? = null,
        val bitSize: Int? = null,
        val canSign: Boolean? = null,
        val canDerive: Boolean? = null,
        val canUnwrap: Boolean? = null,
        val canWrap: Boolean? = null,
        val canDecrypt: Boolean? = null,
        val effectiveSize: Int? = null,
        val isPermanent: Boolean? = null,
        val type: String? = null,
        val externalRepresentation: String? = null,
        val canVerify: Boolean? = null,
        val keyId: String? = null,
    )
}
