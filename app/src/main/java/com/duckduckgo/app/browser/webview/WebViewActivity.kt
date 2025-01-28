/*
 * Copyright (c) 2021 DuckDuckGo
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

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Message
import android.view.MenuItem
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.browser.BrowserWebViewClient
import com.duckduckgo.app.browser.commands.Command.SendResponseToJs
import com.duckduckgo.app.browser.databinding.ActivityWebviewBinding
import com.duckduckgo.app.browser.duckchat.DuckChatJSHelper
import com.duckduckgo.app.browser.duckchat.RealDuckChatJSHelper.Companion.DUCK_CHAT_FEATURE_NAME
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.browser.api.ui.BrowserScreens.WebViewActivityWithParams
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.duckduckgo.js.messaging.api.JsMessaging
import com.duckduckgo.navigation.api.getActivityParams
import com.duckduckgo.user.agent.api.UserAgentProvider
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(WebViewActivityWithParams::class)
class WebViewActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var userAgentProvider: UserAgentProvider

    @Inject
    lateinit var webViewClient: BrowserWebViewClient

    @Inject
    lateinit var pixel: Pixel

    @Inject
    @Named("ContentScopeScripts")
    lateinit var contentScopeScripts: JsMessaging

    @Inject
    lateinit var duckChatJSHelper: DuckChatJSHelper

    @Inject
    @AppCoroutineScope
    lateinit var appCoroutineScope: CoroutineScope

    @Inject
    lateinit var dispatcherProvider: DispatcherProvider

    private val binding: ActivityWebviewBinding by viewBinding()

    private val toolbar
        get() = binding.includeToolbar.toolbar

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)
        setupToolbar(toolbar)

        val params = intent.getActivityParams(WebViewActivityWithParams::class.java)
        val url = params?.url
        title = params?.screenTitle.orEmpty()
        val supportNewWindows = params?.supportNewWindows ?: false

        binding.simpleWebview.let {
            it.webViewClient = webViewClient

            if (supportNewWindows) {
                it.webChromeClient = object : WebChromeClient() {
                    override fun onCreateWindow(
                        view: WebView?,
                        isDialog: Boolean,
                        isUserGesture: Boolean,
                        resultMsg: Message?,
                    ): Boolean {
                        pixel.fire(AppPixelName.DEDICATED_WEBVIEW_NEW_TAB_REQUESTED)
                        view?.requestFocusNodeHref(resultMsg)
                        val newWindowUrl = resultMsg?.data?.getString("url")
                        if (newWindowUrl != null) {
                            startActivity(BrowserActivity.intent(this@WebViewActivity, newWindowUrl))
                            return true
                        } else {
                            pixel.fire(AppPixelName.DEDICATED_WEBVIEW_URL_EXTRACTION_FAILED)
                        }
                        return false
                    }
                }
            }

            it.settings.apply {
                userAgentString = userAgentProvider.userAgent()
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                builtInZoomControls = true
                displayZoomControls = false
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                setSupportMultipleWindows(supportNewWindows)
                databaseEnabled = false
                setSupportZoom(true)
            }

            contentScopeScripts.register(
                it,
                object : JsMessageCallback() {
                    override fun process(
                        featureName: String,
                        method: String,
                        id: String?,
                        data: JSONObject?,
                    ) {
                        when (featureName) {
                            DUCK_CHAT_FEATURE_NAME -> {
                                appCoroutineScope.launch(dispatcherProvider.io()) {
                                    val response = duckChatJSHelper.processJsCallbackMessage(featureName, method, id, data)
                                    if (response is SendResponseToJs) {
                                        withContext(dispatcherProvider.main()) {
                                            contentScopeScripts.onResponse(response.data)
                                        }
                                    }
                                }
                            }

                            else -> {}
                        }
                    }
                },
            )
        }

        url?.let {
            binding.simpleWebview.loadUrl(it)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                super.onBackPressed()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onBackPressed() {
        if (binding.simpleWebview.canGoBack()) {
            binding.simpleWebview.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
