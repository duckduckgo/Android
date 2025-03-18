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

package com.duckduckgo.duckchat.impl

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Message
import android.view.MenuItem
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.browser.api.ui.BrowserScreens.BrowserActivityWithParams
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.duckchat.impl.DuckChatPixelName.DEDICATED_WEBVIEW_NEW_TAB_REQUESTED
import com.duckduckgo.duckchat.impl.DuckChatPixelName.DEDICATED_WEBVIEW_URL_EXTRACTION_FAILED
import com.duckduckgo.duckchat.impl.RealDuckChatJSHelper.Companion.DUCK_CHAT_FEATURE_NAME
import com.duckduckgo.duckchat.impl.databinding.ActivityWebviewBinding
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.duckduckgo.js.messaging.api.JsMessaging
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.user.agent.api.UserAgentProvider
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@InjectWith(ActivityScope::class)
class DuckChatWebViewActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var userAgentProvider: UserAgentProvider

    @Inject
    lateinit var webViewClient: DuckChatWebViewClient

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

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    private val binding: ActivityWebviewBinding by viewBinding()

    private val toolbar
        get() = binding.includeToolbar.toolbar

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)
        setupToolbar(toolbar)

        val url = intent.getStringExtra(URL_EXTRA)

        binding.simpleWebview.let {
            it.webViewClient = webViewClient
            it.webChromeClient = object : WebChromeClient() {
                override fun onCreateWindow(
                    view: WebView?,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: Message?,
                ): Boolean {
                    pixel.fire(DEDICATED_WEBVIEW_NEW_TAB_REQUESTED)
                    view?.requestFocusNodeHref(resultMsg)
                    val newWindowUrl = resultMsg?.data?.getString("url")
                    if (newWindowUrl != null) {
                        val intent = globalActivityStarter.startIntent(
                            this@DuckChatWebViewActivity,
                            BrowserActivityWithParams(
                                url = newWindowUrl,
                            ),
                        )
                        startActivity(intent)
                        return true
                    } else {
                        pixel.fire(DEDICATED_WEBVIEW_URL_EXTRACTION_FAILED)
                    }
                    return false
                }
            }

            it.settings.apply {
                userAgentString = CUSTOM_UA
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                builtInZoomControls = true
                displayZoomControls = false
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                setSupportMultipleWindows(true)
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

    companion object {
        fun intent(
            context: Context,
            url: String? = null,
        ): Intent {
            val intent = Intent(context, DuckChatWebViewActivity::class.java)
            intent.putExtra(URL_EXTRA, url)
            return intent
        }
        private const val URL_EXTRA = "URL_EXTRA"
        private const val CUSTOM_UA =
            "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/124.0.0.0 Mobile DuckDuckGo/5 Safari/537.36"
    }
}
