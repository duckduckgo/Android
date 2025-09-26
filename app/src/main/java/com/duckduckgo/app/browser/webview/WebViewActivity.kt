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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.browser.BrowserWebViewClient
import com.duckduckgo.app.browser.databinding.ActivityWebviewBinding
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.browser.api.ui.BrowserScreens.WebViewActivityWithParams
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.duckduckgo.js.messaging.api.JsMessaging
import com.duckduckgo.navigation.api.getActivityParams
import com.duckduckgo.user.agent.api.UserAgentProvider
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Named

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(WebViewActivityWithParams::class)
class WebViewActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var userAgentProvider: UserAgentProvider

    @Inject
    lateinit var webViewClient: BrowserWebViewClient

    @Inject
    lateinit var pixel: Pixel

    private val viewModel: WebViewViewModel by bindViewModel()

    @Inject
    @Named("ContentScopeScripts")
    lateinit var contentScopeScripts: JsMessaging

    private val binding: ActivityWebviewBinding by viewBinding()

    private val toolbar
        get() = binding.includeToolbar.toolbar

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)
        setupToolbar(toolbar)

        val (url, supportNewWindows) = extractParameters()

        setupWebView(supportNewWindows)
        setupCollectors()

        viewModel.onStart(url)
    }

    private fun setupCollectors() {
        viewModel.commands
            .flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)
    }

    private fun processCommand(command: WebViewViewModel.Command) {
        when (command) {
            is WebViewViewModel.Command.LoadUrl -> {
                binding.simpleWebview.loadUrl(command.url)
            }
            WebViewViewModel.Command.Exit -> {
                finish()
            }
        }
    }

    private fun extractParameters(): Pair<String?, Boolean> {
        val params = intent.getActivityParams(WebViewActivityWithParams::class.java)
        val url = params?.url
        title = params?.screenTitle.orEmpty()
        val supportNewWindows = params?.supportNewWindows ?: false
        return Pair(url, supportNewWindows)
    }

    private fun setupWebView(supportNewWindows: Boolean) {
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
                        view?.requestFocusNodeHref(resultMsg)
                        val newWindowUrl = resultMsg?.data?.getString("url")
                        if (newWindowUrl != null) {
                            startActivity(BrowserActivity.intent(this@WebViewActivity, newWindowUrl))
                            return true
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
                        viewModel.processJsCallbackMessage(featureName, method, id, data)
                    }
                },
            )
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
