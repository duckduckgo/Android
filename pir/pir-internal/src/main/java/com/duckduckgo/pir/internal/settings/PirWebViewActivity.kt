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

package com.duckduckgo.pir.internal.settings

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Message
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.duckduckgo.navigation.api.GlobalActivityStarter.ActivityParams
import com.duckduckgo.navigation.api.getActivityParams
import com.duckduckgo.pir.internal.common.PirJobConstants.DBP_INITIAL_URL
import com.duckduckgo.pir.internal.databinding.ActivityPirWebviewBinding
import com.duckduckgo.pir.internal.optout.PirOptOut
import com.duckduckgo.pir.internal.scripts.PirCssScriptLoader
import com.duckduckgo.pir.internal.web.PIRWebUiConstants
import com.duckduckgo.pir.internal.web.PirWebMessagingInterface
import javax.inject.Inject
import kotlinx.coroutines.launch
import logcat.logcat
import org.json.JSONObject

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(PirDebugWebViewResultsScreenParams::class)
class PirWebViewActivity : DuckDuckGoActivity() {
    @Inject
    lateinit var pirOptOut: PirOptOut

    @Inject
    lateinit var dispatcherProvider: DispatcherProvider

    @Inject
    lateinit var pirWebMessagingInterface: PirWebMessagingInterface

    @Inject
    lateinit var pirCssScriptLoader: PirCssScriptLoader

    private val binding: ActivityPirWebviewBinding by viewBinding()
    private val params: PirDebugWebViewResultsScreenParams?
        get() = intent.getActivityParams(PirDebugWebViewResultsScreenParams::class.java)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        // val brokersToOptOut = params?.brokers

        lifecycleScope.launch {
            val scriptToLoad = pirCssScriptLoader.getScript()

            binding.pirDebugWebView.let { webView ->
                pirWebMessagingInterface.register(
                    webView,
                    object : JsMessageCallback() {
                        override fun process(
                            featureName: String,
                            method: String,
                            id: String?,
                            data: JSONObject?,
                        ) {
                            logcat { "PIR-WEB: activity message callback process featureName=$featureName, method=$method, id=$id, data=$data" }
                        }
                    },
                )
                webView.webChromeClient = object : WebChromeClient() {

                    override fun onCreateWindow(
                        view: WebView?,
                        isDialog: Boolean,
                        isUserGesture: Boolean,
                        message: Message,
                    ): Boolean {
                        val transport = message.obj as WebView.WebViewTransport
                        transport.webView = webView
                        message.sendToTarget()
                        return true
                    }

                    override fun onProgressChanged(
                        view: WebView?,
                        newProgress: Int,
                    ) {
                        // if (newProgress == 100) {
                        //     if (binding.webview.canGoBack()) {
                        //         toolbar.setNavigationIcon(R.drawable.ic_arrow_left_24)
                        //     } else {
                        //         toolbar.setNavigationIcon(R.drawable.ic_close_24)
                        //     }
                        // }
                        super.onProgressChanged(view, newProgress)
                    }
                }
                webView.webViewClient = object : WebViewClient() {
                    private var requestedUrl: String? = null
                    private var receivedError: Boolean = false

                    @SuppressLint("RequiresFeature")
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?,
                    ): Boolean {
                        return false
                    }

                    override fun onPageStarted(
                        view: WebView?,
                        url: String?,
                        favicon: Bitmap?,
                    ) {
                        super.onPageStarted(view, url, favicon)
                        logcat { "PIR-SCAN: __________________________" }
                        logcat { "PIR-SCAN: webview onPageStarted $url" }
                        requestedUrl = url
                        receivedError = false
                    }

                    override fun onPageFinished(
                        view: WebView?,
                        url: String?,
                    ) {
                        logcat { "PIR-SCAN: webview onPageFinished $url" }
                        if (!receivedError) {
                            logcat { "PIR-SCAN: webview onPageFinished receivedError $receivedError requestedUrl $requestedUrl for url $url" }
                            view?.evaluateJavascript("javascript:$scriptToLoad", null)
                            // onPageLoaded(url)
                            // pirWebMessagingInterface.sendSubscriptionEvent(SubscriptionEventData(
                            //     PIRWebUiConstants.SCRIPT_FEATURE_NAME,
                            //     "handshake",
                            //     JSONObject().apply {
                            //         put("version", "8")
                            //     },
                            // ))
                        }
                        super.onPageFinished(view, url)
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?,
                    ) {
                        logcat { "PIR-SCAN: webview onReceivedError $error" }
                        if (request?.isForMainFrame == true &&
                            requestedUrl == request.url.toString() &&
                            requestedUrl != DBP_INITIAL_URL
                        ) {
                            logcat {
                                """
                            PIR-SCAN: webview onReceivedError requestedUrl $requestedUrl for url ${request.url} 
                            mainframe ${request.isForMainFrame}
                                """.trimIndent()
                            }
                            receivedError = true
                            // onPageLoadFailed(requestedUrl)
                        }
                        super.onReceivedError(view, request, error)
                    }
                }
                webView.settings.apply {
                    userAgentString = CUSTOM_UA
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    builtInZoomControls = true
                    displayZoomControls = false
                    mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                    setSupportMultipleWindows(false)
                    setSupportZoom(true)
                }
            }

            binding.pirDebugWebView.loadUrl(PIRWebUiConstants.WEB_UI_URL)
        }

        // lifecycleScope.launch {
        //     if (!brokersToOptOut.isNullOrEmpty()) {
        //         pirOptOut.debugExecute(brokersToOptOut, binding.pirDebugWebView).also {
        //             finish()
        //         }
        //     } else {
        //         finish()
        //     }
        // }
    }

    override fun onDestroy() {
        super.onDestroy()
        pirOptOut.stop()
    }

    companion object {
        private const val CUSTOM_UA =
            "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/124.0.0.0 Mobile DuckDuckGo/5 Safari/537.36"
    }
}

data class PirDebugWebViewResultsScreenParams(val brokers: List<String>) : ActivityParams
