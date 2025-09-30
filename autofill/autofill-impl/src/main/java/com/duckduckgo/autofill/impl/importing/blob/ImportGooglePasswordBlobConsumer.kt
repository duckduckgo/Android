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

package com.duckduckgo.autofill.impl.importing.blob

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.WebView
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.autofill.impl.importing.blob.GooglePasswordBlobConsumer.Callback
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.FragmentScope
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.ByteString.Companion.encode
import javax.inject.Inject

interface GooglePasswordBlobConsumer {
    suspend fun configureWebViewForBlobDownload(
        webView: WebView,
        callback: Callback,
    )

    suspend fun postMessageToConvertBlobToDataUri(
        webView: WebView,
        url: String,
    )

    interface Callback {
        suspend fun onCsvAvailable(csv: String)

        suspend fun onCsvError()
    }
}

@ContributesBinding(FragmentScope::class)
class ImportGooglePasswordBlobConsumer @Inject constructor(
    private val webViewBlobDownloader: WebViewBlobDownloader,
    private val dispatchers: DispatcherProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : GooglePasswordBlobConsumer {
    // access to the flow which uses this be guarded against where these features aren't available
    @SuppressLint("RequiresFeature", "AddWebMessageListenerUsage")
    override suspend fun configureWebViewForBlobDownload(
        webView: WebView,
        callback: Callback,
    ) {
        withContext(dispatchers.main()) {
            webViewBlobDownloader.addBlobDownloadSupport(webView)

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
            kotlin
                .runCatching {
                    callback.onCsvAvailable(data)
                }.onFailure { callback.onCsvError() }
        } else if (message.data?.startsWith("Ping:") == true) {
            val locationRef =
                message.data
                    .toString()
                    .encode()
                    .md5()
                    .toString()
            webViewBlobDownloader.storeReplyProxy(sourceOrigin.toString(), replyProxy, locationRef)
        }
    }

    override suspend fun postMessageToConvertBlobToDataUri(
        webView: WebView,
        url: String,
    ) {
        webViewBlobDownloader.convertBlobToDataUri(webView, url)
    }
}
