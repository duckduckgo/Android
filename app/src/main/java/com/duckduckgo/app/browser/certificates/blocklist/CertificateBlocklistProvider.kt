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

import logcat.LogPriority.ERROR
import logcat.LogPriority.INFO
import logcat.asLog
import logcat.logcat
import java.security.KeyStore
import java.security.NoSuchAlgorithmException
import java.security.Provider
import java.security.Security
import javax.net.ssl.ManagerFactoryParameters
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.TrustManagerFactorySpi
import javax.net.ssl.X509ExtendedTrustManager

/**
 * JCA provider that sits in front of the platform's TrustManagerFactory so that every trust manager handed out in
 * this process is wrapped in a [BlocklistTrustManager].
 *
 * WebView (Chromium's X509Util), OkHttp, HttpsURLConnection and Conscrypt's default SSLContext all resolve their trust
 * manager through `TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())`, which is what makes a
 * provider at position 1 a single choke point. The framework's own Network Security Config hooks in the same way
 * (NetworkSecurityConfigProvider), so the delegate found here is the NSC-aware RootTrustManager, not raw Conscrypt.
 *
 * The delegate is resolved in [Provider.Service.newInstance] rather than at init time so that, should no other provider
 * offer the algorithm, JCA skips this provider and continues down the list instead of failing the lookup.
 */
@Suppress("DEPRECATION")
class CertificateBlocklistProvider(
    blocklist: CertificateBlocklist,
    rejections: CertificateBlocklistRejections,
) : Provider(NAME, VERSION, INFO) {
    init {
        putService(BlocklistService(this, blocklist, rejections))
    }

    private class BlocklistService(
        provider: Provider,
        private val blocklist: CertificateBlocklist,
        private val rejections: CertificateBlocklistRejections,
    ) : Provider.Service(provider, TYPE, ALGORITHM, BlocklistTrustManagerFactorySpi::class.java.name, null, null) {

        override fun newInstance(constructorParameter: Any?): Any {
            val delegateProvider = Security.getProviders("$TYPE.$ALGORITHM")
                ?.firstOrNull { it.name != provider.name }
                ?: throw NoSuchAlgorithmException("No other provider offers $TYPE.$ALGORITHM")
            return BlocklistTrustManagerFactorySpi(TrustManagerFactory.getInstance(ALGORITHM, delegateProvider), blocklist, rejections)
        }
    }

    companion object {
        const val NAME = "DuckDuckGoCertificateBlocklist"
        private const val VERSION = 1.0
        private const val INFO = "Wraps the platform TrustManagerFactory to reject blocklisted public keys"
        private const val TYPE = "TrustManagerFactory"
        private val ALGORITHM: String = TrustManagerFactory.getDefaultAlgorithm()
    }
}

internal class BlocklistTrustManagerFactorySpi(
    private val delegate: TrustManagerFactory,
    private val blocklist: CertificateBlocklist,
    private val rejections: CertificateBlocklistRejections,
) : TrustManagerFactorySpi() {
    override fun engineInit(keyStore: KeyStore?) = delegate.init(keyStore)

    override fun engineInit(spec: ManagerFactoryParameters?) = delegate.init(spec)

    override fun engineGetTrustManagers(): Array<TrustManager> =
        delegate.trustManagers.map { trustManager ->
            if (trustManager is X509ExtendedTrustManager) trustManager.wrapped() else trustManager
        }.toTypedArray()

    // X509TrustManagerExtensions refuses a delegate without the host-aware overloads; WebView could not use such a
    // delegate either, so handing it back unwrapped keeps TLS working exactly as it did before.
    private fun X509ExtendedTrustManager.wrapped(): TrustManager =
        runCatching { BlocklistTrustManager(this, blocklist, rejections) }
            .getOrElse { error ->
                logcat(ERROR) { "Certificate blocklist cannot wrap ${this::class.java.name}: ${error.asLog()}" }
                this
            }
}

object CertificateBlocklistInstaller {
    val blocklist = CertificateBlocklist()
    val rejections: CertificateBlocklistRejections = InMemoryCertificateBlocklistRejections()

    /** Must run before anything in the process touches TLS: Conscrypt caches the default trust manager on first use. */
    fun install() {
        if (Security.getProvider(CertificateBlocklistProvider.NAME) != null) return
        runCatching { Security.insertProviderAt(CertificateBlocklistProvider(blocklist, rejections), 1) }
            .onSuccess { position -> logcat(INFO) { "Certificate blocklist provider installed at position $position" } }
            .onFailure { error -> logcat(ERROR) { "Certificate blocklist provider not installed: ${error.asLog()}" } }
    }
}
