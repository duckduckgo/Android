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

import android.annotation.SuppressLint
import android.net.http.X509TrustManagerExtensions
import android.os.Build
import androidx.annotation.RequiresApi
import logcat.LogPriority.WARN
import logcat.logcat
import java.net.Socket
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLEngine
import javax.net.ssl.X509ExtendedTrustManager

/**
 * Wraps the platform's trust manager and additionally rejects any chain that carries a blocklisted public key.
 *
 * The platform decides trust first; the blocklist only ever removes trust. The host-aware overloads are not part of
 * [X509ExtendedTrustManager]: [X509TrustManagerExtensions] finds them by reflection ("duck typing"), and that is the path
 * WebView (Chromium's X509Util) and OkHttp's chain cleaner take, so their signatures must stay exactly as declared here.
 * Those overloads inspect the chain the delegate actually verified, trust anchor and cached intermediates included,
 * whereas the socket and engine overloads can only see the chain the server sent.
 */
@SuppressLint("CustomX509TrustManager")
class BlocklistTrustManager(
    private val delegate: X509ExtendedTrustManager,
    private val blocklist: CertificateBlocklist,
    private val rejections: CertificateBlocklistRejections,
    private val delegateExtensions: X509TrustManagerExtensions = X509TrustManagerExtensions(delegate),
) : X509ExtendedTrustManager() {
    override fun getAcceptedIssuers(): Array<X509Certificate> = delegate.acceptedIssuers

    override fun checkClientTrusted(
        chain: Array<X509Certificate>,
        authType: String,
    ) = delegate.checkClientTrusted(chain, authType)

    override fun checkClientTrusted(
        chain: Array<X509Certificate>,
        authType: String,
        socket: Socket?,
    ) = delegate.checkClientTrusted(chain, authType, socket)

    override fun checkClientTrusted(
        chain: Array<X509Certificate>,
        authType: String,
        engine: SSLEngine?,
    ) = delegate.checkClientTrusted(chain, authType, engine)

    override fun checkServerTrusted(
        chain: Array<X509Certificate>,
        authType: String,
    ) {
        delegate.checkServerTrusted(chain, authType)
        rejectIfBlocked(chain.asList())
    }

    override fun checkServerTrusted(
        chain: Array<X509Certificate>,
        authType: String,
        socket: Socket?,
    ) {
        delegate.checkServerTrusted(chain, authType, socket)
        rejectIfBlocked(chain.asList())
    }

    override fun checkServerTrusted(
        chain: Array<X509Certificate>,
        authType: String,
        engine: SSLEngine?,
    ) {
        delegate.checkServerTrusted(chain, authType, engine)
        rejectIfBlocked(chain.asList())
    }

    fun checkServerTrusted(
        chain: Array<X509Certificate>,
        authType: String,
        host: String?,
    ): List<X509Certificate> {
        val verifiedChain = delegateExtensions.checkServerTrusted(chain, authType, host)
        rejectIfBlocked(verifiedChain)
        return verifiedChain
    }

    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    fun checkServerTrusted(
        chain: Array<X509Certificate>,
        ocspData: ByteArray?,
        tlsSctData: ByteArray?,
        authType: String,
        host: String,
    ): List<X509Certificate> {
        val verifiedChain = delegateExtensions.checkServerTrusted(chain, ocspData, tlsSctData, authType, host)
        rejectIfBlocked(verifiedChain)
        return verifiedChain
    }

    fun isSameTrustConfiguration(
        hostname1: String?,
        hostname2: String?,
    ): Boolean = delegateExtensions.isSameTrustConfiguration(hostname1, hostname2)

    private fun rejectIfBlocked(chain: List<X509Certificate>) {
        blocklist.firstBlocked(chain) ?: return
        chain.firstOrNull()?.let(rejections::record)
        logcat(WARN) { "Rejected a certificate chain carrying a blocklisted public key" }
        throw CertificateException("Certificate chain carries a blocklisted public key")
    }
}
