/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.browser.webview

import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import androidx.annotation.VisibleForTesting
import androidx.core.net.toUri
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.di.IsMainProcess
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.IsMaliciousResult.MALICIOUS
import com.duckduckgo.privacy.config.api.PrivacyConfigCallbackPlugin
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import java.net.URLDecoder
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import timber.log.Timber

interface MaliciousSiteBlockerWebViewIntegration {

    suspend fun shouldIntercept(
        request: WebResourceRequest,
        documentUri: Uri?,
        confirmationCallback: (isMalicious: Boolean) -> Unit,
    ): WebResourceResponse?

    fun shouldOverrideUrlLoading(
        url: Uri,
        isForMainFrame: Boolean,
        confirmationCallback: (isMalicious: Boolean) -> Unit,
    ): Boolean

    fun onPageLoadStarted()
}

@ContributesMultibinding(AppScope::class, PrivacyConfigCallbackPlugin::class)
@ContributesBinding(AppScope::class, MaliciousSiteBlockerWebViewIntegration::class)
class RealMaliciousSiteBlockerWebViewIntegration @Inject constructor(
    private val maliciousSiteProtection: MaliciousSiteProtection,
    private val androidBrowserConfigFeature: AndroidBrowserConfigFeature,
    private val dispatchers: DispatcherProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    @IsMainProcess private val isMainProcess: Boolean,
) : MaliciousSiteBlockerWebViewIntegration, PrivacyConfigCallbackPlugin {

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    val processedUrls = mutableListOf<String>()
    private var isFeatureEnabled = false

    init {
        if (isMainProcess) {
            loadToMemory()
        }
    }

    private fun loadToMemory() {
        appCoroutineScope.launch(dispatchers.io()) {
            isFeatureEnabled = androidBrowserConfigFeature.enableMaliciousSiteProtection().isEnabled()
        }
    }

    override fun onPrivacyConfigDownloaded() {
        loadToMemory()
    }

    override suspend fun shouldIntercept(
        request: WebResourceRequest,
        documentUri: Uri?,
        confirmationCallback: (isMalicious: Boolean) -> Unit,
    ): WebResourceResponse? {
        if (!isFeatureEnabled) {
            return null
        }
        val url = request.url.let {
            if (it.fragment != null) {
                it.buildUpon().fragment(null).build()
            } else {
                it
            }
        }

        val decodedUrl = URLDecoder.decode(url.toString(), "UTF-8").lowercase()

        if (processedUrls.contains(decodedUrl)) {
            processedUrls.remove(decodedUrl)
            Timber.tag("PhishingAndMalwareDetector").d("Already intercepted, skipping $decodedUrl")
            return null
        }

        if (request.isForMainFrame) {
            if (maliciousSiteProtection.isMalicious(decodedUrl.toUri(), confirmationCallback) == MALICIOUS) {
                return WebResourceResponse(null, null, null)
            }
            processedUrls.add(decodedUrl)
        } else if (isForIframe(request) && documentUri?.host == request.requestHeaders["Referer"]?.toUri()?.host) {
            if (maliciousSiteProtection.isMalicious(decodedUrl.toUri(), confirmationCallback) == MALICIOUS) {
                return WebResourceResponse(null, null, null)
            }
            processedUrls.add(decodedUrl)
        }
        return null
    }

    override fun shouldOverrideUrlLoading(
        url: Uri,
        isForMainFrame: Boolean,
        confirmationCallback: (isMalicious: Boolean) -> Unit,
    ): Boolean {
        return runBlocking {
            if (!isFeatureEnabled) {
                return@runBlocking false
            }
            val decodedUrl = URLDecoder.decode(url.toString(), "UTF-8").lowercase()

            if (processedUrls.contains(decodedUrl)) {
                processedUrls.remove(decodedUrl)
                Timber.tag("PhishingAndMalwareDetector").d("Already intercepted, skipping $decodedUrl")
                return@runBlocking false
            }

            // iframes always go through the shouldIntercept method, so we only need to check the main frame here
            if (isForMainFrame) {
                if (maliciousSiteProtection.isMalicious(decodedUrl.toUri(), confirmationCallback) == MALICIOUS) {
                    return@runBlocking true
                }
                processedUrls.add(decodedUrl)
            }
            false
        }
    }

    private fun isForIframe(request: WebResourceRequest) = request.requestHeaders["Sec-Fetch-Dest"] == "iframe" ||
        request.url.path?.contains("/embed/") == true ||
        request.url.path?.contains("/iframe/") == true ||
        request.requestHeaders["Accept"]?.contains("text/html") == true

    override fun onPageLoadStarted() {
        processedUrls.clear()
    }
}
