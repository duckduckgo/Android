/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.app.browser

import android.annotation.SuppressLint
import androidx.core.net.toUri
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import com.duckduckgo.app.browser.webview.WebViewCompatFeature
import com.duckduckgo.app.browser.webview.WebViewCompatFeatureSettings
import com.duckduckgo.browser.api.webviewcompat.WebViewCompatWrapper
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.FragmentScope
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.Moshi
import dagger.SingleInstanceIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val delay = "\$DELAY$"
private const val postInitialPing = "\$POST_INITIAL_PING$"
private const val replyToNativeMessages = "\$REPLY_TO_NATIVE_MESSAGES$"
private const val objectName = "\$OBJECT_NAME$"

interface WebViewCompatTestHelper {
    suspend fun configureWebViewForWebViewCompatTest(webView: DuckDuckGoWebView, isBlobDownloadWebViewFeatureEnabled: Boolean)
    suspend fun handleWebViewCompatMessage(
        message: WebMessageCompat,
        replyProxy: JavaScriptReplyProxy,
        isMainFrame: Boolean,
    )

    suspend fun useBlobDownloadsMessageListener(): Boolean

    suspend fun onPageStarted(webView: DuckDuckGoWebView?)
    suspend fun onBrowserMenuButtonPressed(webView: DuckDuckGoWebView?)

    fun blobDownloadScriptForWebViewCompatTest(): String
}

@ContributesBinding(FragmentScope::class)
@SingleInstanceIn(FragmentScope::class)
class RealWebViewCompatTestHelper @Inject constructor(
    private val dispatchers: DispatcherProvider,
    private val webViewCompatFeature: WebViewCompatFeature,
    private val webViewCompatWrapper: WebViewCompatWrapper,
    moshi: Moshi,
) : WebViewCompatTestHelper {

    private var proxy: JavaScriptReplyProxy? = null

    private val adapter = moshi.adapter(WebViewCompatFeatureSettings::class.java)

    data class WebViewCompatConfig(
        val settings: WebViewCompatFeatureSettings?,
        val useBlobDownloadsMessageListener: Boolean,
        val replyToInitialPing: Boolean,
        val jsSendsInitialPing: Boolean,
        val jsRepliesToNativeMessages: Boolean,
        val sendMessageOnPageStarted: Boolean,
        val sendMessageOnContextMenuOpen: Boolean,
        val sendMessagesUsingReplyProxy: Boolean,
    )

    private var cachedConfig: WebViewCompatConfig? = null

    private suspend fun getWebViewCompatConfig(): WebViewCompatConfig {
        cachedConfig?.let { return it }

        return withContext(dispatchers.io()) {
            WebViewCompatConfig(
                settings = webViewCompatFeature.self().getSettings()?.let {
                    adapter.fromJson(it)
                },
                useBlobDownloadsMessageListener = webViewCompatFeature.useBlobDownloadsMessageListener().isEnabled(),
                replyToInitialPing = webViewCompatFeature.replyToInitialPing().isEnabled(),
                jsSendsInitialPing = webViewCompatFeature.jsSendsInitialPing().isEnabled(),
                jsRepliesToNativeMessages = webViewCompatFeature.jsRepliesToNativeMessages().isEnabled(),
                sendMessageOnPageStarted = webViewCompatFeature.sendMessageOnPageStarted().isEnabled(),
                sendMessageOnContextMenuOpen = webViewCompatFeature.sendMessageOnContextMenuOpen().isEnabled(),
                sendMessagesUsingReplyProxy = webViewCompatFeature.sendMessagesUsingReplyProxy().isEnabled(),
            ).also {
                cachedConfig = it
            }
        }
    }

    override suspend fun configureWebViewForWebViewCompatTest(webView: DuckDuckGoWebView, isBlobDownloadWebViewFeatureEnabled: Boolean) {
        val config = getWebViewCompatConfig()

        val useDedicatedListener = !config.useBlobDownloadsMessageListener || !isBlobDownloadWebViewFeatureEnabled

        val script = withContext(dispatchers.io()) {
            if (!webViewCompatFeature.self().isEnabled()) return@withContext null
            webView.context.resources?.openRawResource(R.raw.webviewcompat_test_script)
                ?.bufferedReader().use { it?.readText() }
                ?.replace(delay, config.settings?.jsInitialPingDelay?.toString() ?: "0")
                ?.replace(postInitialPing, config.jsSendsInitialPing.toString())
                ?.replace(replyToNativeMessages, config.jsRepliesToNativeMessages.toString())
                ?.replace(objectName, if (useDedicatedListener) "webViewCompatTestObj" else "ddgBlobDownloadObj")
        } ?: return

        withContext(dispatchers.main()) {
            webViewCompatWrapper.addDocumentStartJavaScript(webView, script, setOf("*"))

            if (useDedicatedListener) {
                webViewCompatWrapper.addWebMessageListener(
                    webView,
                    "webViewCompatTestObj",
                    setOf("*"),
                ) { view, message, sourceOrigin, isMainFrame, replyProxy ->
                    webView.findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
                        handleWebViewCompatMessage(message, replyProxy, isMainFrame)
                    }
                }
            }
        }
    }

    @SuppressLint("PostMessageUsage", "RequiresFeature")
    override suspend fun handleWebViewCompatMessage(
        message: WebMessageCompat,
        replyProxy: JavaScriptReplyProxy,
        isMainFrame: Boolean,
    ) {
        withContext(dispatchers.io()) {
            if (message.data?.startsWith("webViewCompat Ping:") != true) return@withContext
            val cfg = getWebViewCompatConfig()
            if (isMainFrame) {
                proxy = replyProxy
            }
            if (cfg.replyToInitialPing) {
                cfg.settings?.initialPingDelay?.takeIf { it > 0 }?.let {
                    delay(it)
                }
                withContext(dispatchers.main()) {
                    replyProxy.postMessage("Pong from Native")
                }
            }
        }
    }

    override suspend fun useBlobDownloadsMessageListener(): Boolean {
        return withContext(dispatchers.io()) { getWebViewCompatConfig().useBlobDownloadsMessageListener }
    }

    @SuppressLint("PostMessageUsage", "RequiresFeature")
    override suspend fun onPageStarted(webView: DuckDuckGoWebView?) {
        withContext(dispatchers.main()) {
            val config = getWebViewCompatConfig()

            if (!config.sendMessageOnPageStarted) return@withContext

            val messageData = "PageStarted"

            if (config.sendMessagesUsingReplyProxy) {
                proxy?.postMessage(messageData)
            } else {
                webView?.url?.let {
                    WebViewCompat.postWebMessage(
                        webView,
                        WebMessageCompat(messageData),
                        it.toUri(),
                    )
                }
            }
        }
    }

    @SuppressLint("RequiresFeature", "PostMessageUsage")
    override suspend fun onBrowserMenuButtonPressed(webView: DuckDuckGoWebView?) {
        withContext(dispatchers.main()) {
            val config = getWebViewCompatConfig()

            if (!config.sendMessageOnContextMenuOpen) return@withContext

            val messageData = "ContextMenuOpened"

            if (config.sendMessagesUsingReplyProxy) {
                proxy?.postMessage(messageData)
            } else {
                webView?.url?.let {
                    WebViewCompat.postWebMessage(
                        webView,
                        WebMessageCompat(messageData),
                        it.toUri(),
                    )
                }
            }
        }
    }

    override fun blobDownloadScriptForWebViewCompatTest(): String {
        val script =
            """
            (function() {

                const urlToBlobCollection = {};

                const original_createObjectURL = URL.createObjectURL;

                URL.createObjectURL = function () {
                    const blob = arguments[0];
                    const url = original_createObjectURL.call(this, ...arguments);
                    if (blob instanceof Blob) {
                        urlToBlobCollection[url] = blob;
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

                const pingMessage = 'Ping:' + window.location.href;
                window.ddgBlobDownloadObj.postMessage(pingMessage);
                console.log('Sent ping message for blob downloads: ' + pingMessage);

                window.ddgBlobDownloadObj.addEventListener('message', function(event) {
                    if (event.data.startsWith('blob:')) {
                        console.log(event.data);
                        const blob = urlToBlobCollection[event.data];
                        if (blob) {
                            blobToBase64DataUrl(blob).then((dataUrl) => {
                                console.log('Sending data URL back to native ' + dataUrl);
                                window.ddgBlobDownloadObj.postMessage(dataUrl);
                            });
                        } else {
                            console.log('No Blob found for URL: ' + event.data);
                        }
                    }
                });
            })();
            """.trimIndent()

        return script
    }
}
