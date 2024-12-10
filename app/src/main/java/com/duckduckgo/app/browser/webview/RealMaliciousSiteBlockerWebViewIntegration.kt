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
import com.duckduckgo.browser.api.MaliciousSiteBlockerWebViewIntegration
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection
import com.squareup.anvil.annotations.ContributesBinding
import java.net.URLDecoder
import javax.inject.Inject
import timber.log.Timber

@ContributesBinding(AppScope::class)
class RealMaliciousSiteBlockerWebViewIntegration @Inject constructor(
    private val maliciousSiteProtection: MaliciousSiteProtection,
) : MaliciousSiteBlockerWebViewIntegration {

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    val processedUrls = mutableListOf<String>()

    override suspend fun shouldIntercept(
        request: WebResourceRequest,
        documentUri: Uri?,
        onSiteBlockedAsync: () -> Unit,
    ): WebResourceResponse? {
        if (!maliciousSiteProtection.isFeatureEnabled) {
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
            if (maliciousSiteProtection.isMalicious(decodedUrl.toUri(), onSiteBlockedAsync)) {
                return WebResourceResponse(null, null, null)
            }
            processedUrls.add(decodedUrl)
        } else if (isForIframe(request) && documentUri?.host == request.requestHeaders["Referer"]?.toUri()?.host) {
            if (maliciousSiteProtection.isMalicious(decodedUrl.toUri(), onSiteBlockedAsync)) {
                return WebResourceResponse(null, null, null)
            }
            processedUrls.add(decodedUrl)
        }
        return null
    }

    override suspend fun shouldOverrideUrlLoading(
        url: Uri,
        webViewUrl: Uri?,
        isForMainFrame: Boolean,
        onSiteBlockedAsync: () -> Unit,
    ): Boolean {
        if (!maliciousSiteProtection.isFeatureEnabled) {
            return false
        }
        val decodedUrl = URLDecoder.decode(url.toString(), "UTF-8").lowercase()

        if (processedUrls.contains(decodedUrl)) {
            processedUrls.remove(decodedUrl)
            Timber.tag("PhishingAndMalwareDetector").d("Already intercepted, skipping $decodedUrl")
            return false
        }

        if (isForMainFrame && decodedUrl.toUri() == webViewUrl) {
            if (maliciousSiteProtection.isMalicious(decodedUrl.toUri(), onSiteBlockedAsync)) {
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
