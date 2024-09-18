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

package com.duckduckgo.autofill.impl.importing.gpm.webflow

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.WebView
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.autofill.impl.importing.gpm.webflow.GooglePasswordBlobConsumer.Callback
import com.duckduckgo.browser.api.WebViewMessageListening
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.FragmentScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.ByteString.Companion.encode

interface GooglePasswordBlobConsumer {
    suspend fun configureWebViewForBlobDownload(
        webView: WebView,
        callback: Callback,
    )

    suspend fun postMessageToConvertBlobToDataUri(url: String)

    interface Callback {
        suspend fun onCsvAvailable(csv: String)
        suspend fun onCsvError()
    }
}

@ContributesBinding(FragmentScope::class)
class ImportGooglePasswordBlobConsumer @Inject constructor(
    private val dispatchers: DispatcherProvider,
    private val webViewMessageListening: WebViewMessageListening,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : GooglePasswordBlobConsumer {

    private val replyProxyMap = mutableMapOf<String, JavaScriptReplyProxy>()

    // Map<String, Map<String, JavaScriptReplyProxy>>() = Map<Origin, Map<location.href, JavaScriptReplyProxy>>()
    private val fixedReplyProxyMap = mutableMapOf<String, Map<String, JavaScriptReplyProxy>>()

    @SuppressLint("RequiresFeature")
    override suspend fun configureWebViewForBlobDownload(
        webView: WebView,
        callback: Callback,
    ) {
        withContext(dispatchers.main()) {
            WebViewCompat.addDocumentStartJavaScript(webView, blobDownloadScript(), setOf("*"))
            WebViewCompat.addWebMessageListener(
                webView,
                "ddgBlobDownloadObj",
                setOf("*"),
            ) { _, message, sourceOrigin, _, replyProxy ->
                val data = message.data ?: return@addWebMessageListener
                appCoroutineScope.launch(dispatchers.io()) {
                    processReceivedWebMessage(data, message, sourceOrigin, replyProxy, callback)
                }
            }
        }
    }

    private suspend fun processReceivedWebMessage(
        data: String,
        message: WebMessageCompat,
        sourceOrigin: Uri,
        replyProxy: JavaScriptReplyProxy,
        callback: Callback,
    ) {
        if (data.startsWith("data:")) {
            kotlin.runCatching {
                callback.onCsvAvailable(data)
            }.onFailure { callback.onCsvError() }
        } else if (message.data?.startsWith("Ping:") == true) {
            val locationRef = message.data.toString().encode().md5().toString()
            saveReplyProxyForBlobDownload(sourceOrigin.toString(), replyProxy, locationRef)
        }
    }

    private suspend fun saveReplyProxyForBlobDownload(
        originUrl: String,
        replyProxy: JavaScriptReplyProxy,
        locationHref: String? = null,
    ) {
        withContext(dispatchers.io()) { // FF check has disk IO
            if (true) {
                // if (webViewBlobDownloadFeature.fixBlobDownloadWithIframes().isEnabled()) {
                val frameProxies = fixedReplyProxyMap[originUrl]?.toMutableMap() ?: mutableMapOf()
                // if location.href is not passed, we fall back to origin
                val safeLocationHref = locationHref ?: originUrl
                frameProxies[safeLocationHref] = replyProxy
                fixedReplyProxyMap[originUrl] = frameProxies
            } else {
                replyProxyMap[originUrl] = replyProxy
            }
        }
    }

    @SuppressLint("RequiresFeature") // it's already checked in isBlobDownloadWebViewFeatureEnabled
    override suspend fun postMessageToConvertBlobToDataUri(url: String) {
        withContext(dispatchers.main()) { // main because postMessage is not always safe in another thread
            if (true) {
                // if (withContext(dispatchers.io()) { webViewBlobDownloadFeature.fixBlobDownloadWithIframes().isEnabled() }) {
                for ((key, proxies) in fixedReplyProxyMap) {
                    if (sameOrigin(url.removePrefix("blob:"), key)) {
                        for (replyProxy in proxies.values) {
                            replyProxy.postMessage(url)
                        }
                        return@withContext
                    }
                }
            } else {
                for ((key, value) in replyProxyMap) {
                    if (sameOrigin(url.removePrefix("blob:"), key)) {
                        value.postMessage(url)
                        return@withContext
                    }
                }
            }
        }
    }

    private fun sameOrigin(
        firstUrl: String,
        secondUrl: String,
    ): Boolean {
        return kotlin.runCatching {
            val firstUri = Uri.parse(firstUrl)
            val secondUri = Uri.parse(secondUrl)

            firstUri.host == secondUri.host && firstUri.scheme == secondUri.scheme && firstUri.port == secondUri.port
        }.getOrNull() ?: return false
    }

    private fun blobDownloadScript(): String {
        val script = """
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

        return script
    }
}
