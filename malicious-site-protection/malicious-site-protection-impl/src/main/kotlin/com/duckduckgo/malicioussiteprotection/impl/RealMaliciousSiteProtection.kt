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

package com.duckduckgo.malicioussiteprotection.impl

import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import androidx.core.net.toUri
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.di.IsMainProcess
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection
import com.duckduckgo.privacy.config.api.PrivacyConfigCallbackPlugin
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import java.net.URLDecoder
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import timber.log.Timber

@ContributesBinding(AppScope::class, MaliciousSiteProtection::class)
@ContributesMultibinding(AppScope::class, PrivacyConfigCallbackPlugin::class)
class RealMaliciousSiteProtection @Inject constructor(
    private val dispatchers: DispatcherProvider,
    private val maliciousSiteProtectionFeature: MaliciousSiteProtectionFeature,
    @IsMainProcess private val isMainProcess: Boolean,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : MaliciousSiteProtection, PrivacyConfigCallbackPlugin {

    private var isFeatureEnabled = false
    private var hashPrefixUpdateFrequency = 20L
    private var filterSetUpdateFrequency = 720L

    init {
        if (isMainProcess) {
            loadToMemory()
        }
    }

    override fun onPrivacyConfigDownloaded() {
        loadToMemory()
    }

    private fun loadToMemory() {
        appCoroutineScope.launch(dispatchers.io()) {
            isFeatureEnabled = maliciousSiteProtectionFeature.self().isEnabled()
            maliciousSiteProtectionFeature.self().getSettings()?.let {
                JSONObject(it).let { settings ->
                    hashPrefixUpdateFrequency = settings.getLong("hashPrefixUpdateFrequency")
                    filterSetUpdateFrequency = settings.getLong("filterSetUpdateFrequency")
                }
            }
        }
    }

    private val processedUrls = mutableListOf<String>()

    private fun shouldIntercept(url: Uri, onSiteBlockedAsync: () -> Unit): Boolean {
        Timber.tag("PhishingAndMalwareDetector").d("shouldIntercept $url")
        // TODO (cbarreiro): Implement the logic to check if the URL is malicious
        return false
    }

    override suspend fun shouldIntercept(
        request: WebResourceRequest,
        documentUri: Uri?,
        onSiteBlockedAsync: () -> Unit,
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

        if (request.isForMainFrame && decodedUrl.toUri() == documentUri) {
            if (shouldIntercept(decodedUrl.toUri(), onSiteBlockedAsync)) {
                return WebResourceResponse(null, null, null)
            }
            processedUrls.add(decodedUrl)
        } else if (isForIframe(request) && documentUri?.host == request.requestHeaders["Referer"]?.toUri()?.host) {
            if (shouldIntercept(decodedUrl.toUri(), onSiteBlockedAsync)) {
                return WebResourceResponse(null, null, null)
            }
            processedUrls.add(decodedUrl)
        }
        return null
    }

    override fun shouldOverrideUrlLoading(
        url: Uri,
        webViewUrl: Uri?,
        isForMainFrame: Boolean,
        isRedirect: Boolean,
        onSiteBlockedAsync: () -> Unit,
    ): Boolean {
        if (!isFeatureEnabled) {
            return false
        }
        val decodedUrl = URLDecoder.decode(url.toString(), "UTF-8").lowercase()

        if (processedUrls.contains(decodedUrl)) {
            processedUrls.remove(decodedUrl)
            Timber.tag("PhishingAndMalwareDetector").d("Already intercepted, skipping $decodedUrl")
            return false
        }

        if (isForMainFrame && decodedUrl.toUri() == webViewUrl) {
            if (shouldIntercept(decodedUrl.toUri(), onSiteBlockedAsync)) {
                return true
            }
            processedUrls.add(decodedUrl)
        }
        return false
    }

    private fun isForIframe(request: WebResourceRequest) = request.requestHeaders["Sec-Fetch-Dest"] == "iframe" ||
        request.url.path?.contains("/embed/") == true ||
        request.url.path?.contains("/iframe/") == true ||
        request.requestHeaders["Accept"]?.contains("text/html") == true

    override fun onPageLoadStarted() {
        processedUrls.clear()
    }
}
