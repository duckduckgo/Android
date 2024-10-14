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

package com.duckduckgo.app.browser.downloader

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.WebView
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebViewCompat
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker.WebViewCapability
import com.duckduckgo.app.browser.webview.WebViewBlobDownloadFeature
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.browser.api.download.WebViewBlobDownloader
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlinx.coroutines.withContext

@ContributesBinding(AppScope::class)
class WebViewBlobDownloaderModernImpl @Inject constructor(
    private val webViewBlobDownloadFeature: WebViewBlobDownloadFeature,
    private val dispatchers: DispatcherProvider,
    private val webViewCapabilityChecker: WebViewCapabilityChecker,
    private val androidBrowserConfig: AndroidBrowserConfigFeature,

) : WebViewBlobDownloader {

    private val replyProxyMap = mutableMapOf<String, JavaScriptReplyProxy>()

    // Map<String, Map<String, JavaScriptReplyProxy>>() = Map<Origin, Map<location.href, JavaScriptReplyProxy>>()
    private val fixedReplyProxyMap = mutableMapOf<String, Map<String, JavaScriptReplyProxy>>()

    @SuppressLint("RequiresFeature")
    override suspend fun addBlobDownloadSupport(webView: WebView) {
        if (isBlobDownloadWebViewFeatureEnabled()) {
            WebViewCompat.addDocumentStartJavaScript(webView, script, setOf("*"))
        }
    }

    @SuppressLint("RequiresFeature")
    override suspend fun convertBlobToDataUri(blobUrl: String) {
        if (withContext(dispatchers.io()) { androidBrowserConfig.fixBlobDownloadWithIframes().isEnabled() }) {
            for ((key, proxies) in fixedReplyProxyMap) {
                if (sameOrigin(blobUrl.removePrefix("blob:"), key)) {
                    for (replyProxy in proxies.values) {
                        replyProxy.postMessage(blobUrl)
                    }
                    return
                }
            }
        } else {
            for ((key, value) in replyProxyMap) {
                if (sameOrigin(blobUrl.removePrefix("blob:"), key)) {
                    value.postMessage(blobUrl)
                    return
                }
            }
        }
    }

    override suspend fun storeReplyProxy(
        originUrl: String,
        replyProxy: JavaScriptReplyProxy,
        locationHref: String?,
    ) {
        if (androidBrowserConfig.fixBlobDownloadWithIframes().isEnabled()) {
            val frameProxies = fixedReplyProxyMap[originUrl]?.toMutableMap() ?: mutableMapOf()
            // if location.href is not passed, we fall back to origin
            val safeLocationHref = locationHref ?: originUrl
            frameProxies[safeLocationHref] = replyProxy
            fixedReplyProxyMap[originUrl] = frameProxies
        } else {
            replyProxyMap[originUrl] = replyProxy
        }
    }

    private fun sameOrigin(firstUrl: String, secondUrl: String): Boolean {
        return kotlin.runCatching {
            val firstUri = Uri.parse(firstUrl)
            val secondUri = Uri.parse(secondUrl)

            firstUri.host == secondUri.host && firstUri.scheme == secondUri.scheme && firstUri.port == secondUri.port
        }.getOrNull() ?: return false
    }

    override fun clearReplyProxies() {
        fixedReplyProxyMap.clear()
        replyProxyMap.clear()
    }

    private suspend fun isBlobDownloadWebViewFeatureEnabled(): Boolean {
        return withContext(dispatchers.io()) { webViewBlobDownloadFeature.self().isEnabled() } &&
            webViewCapabilityChecker.isSupported(WebViewCapability.WebMessageListener) &&
            webViewCapabilityChecker.isSupported(WebViewCapability.DocumentStartJavaScript)
    }

    companion object {
        private val script = """
            window.__url_to_blob_collection = {};
        
            const original_createObjectURL = URL.createObjectURL;
        
            URL.createObjectURL = function () {
                const blob = arguments[0];
                const url = original_createObjectURL.call(this, ...arguments);
                if (blob instanceof Blob) {
                    __url_to_blob_collection[url] = blob;
                }
                return url;
            }
            
            function blobToBase64DataUrl(blob) {
                return new Promise((resolve, reject) => {
                    const reader = new FileReader();
                    reader.onloadend = function() {
                        resolve(reader.result);
                    }
                    reader.onerror = function() {
                        reject(new Error('Failed to read Blob object'));
                    }
                    reader.readAsDataURL(blob);
                });
            }
        
            const pingMessage = 'Ping:' + window.location.href
            ddgBlobDownloadObj.postMessage(pingMessage)
                    
            ddgBlobDownloadObj.onmessage = function(event) {
                if (event.data.startsWith('blob:')) {
                    const blob = window.__url_to_blob_collection[event.data];
                    if (blob) {
                        blobToBase64DataUrl(blob).then((dataUrl) => {
                            ddgBlobDownloadObj.postMessage(dataUrl);
                        });
                    }
                }
            }
        """.trimIndent()
    }
}
