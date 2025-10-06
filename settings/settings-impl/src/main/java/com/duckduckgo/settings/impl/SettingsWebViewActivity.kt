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

package com.duckduckgo.settings.impl

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.MenuItem
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.browser.api.webviewcompat.WebViewCompatWrapper
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.contentscopescripts.api.CoreContentScopeScripts
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.duckduckgo.js.messaging.api.JsMessaging
import com.duckduckgo.navigation.api.getActivityParams
import com.duckduckgo.settings.api.SettingsWebViewScreenWithParams
import com.duckduckgo.settings.impl.databinding.ActivitySettingsWebviewBinding
import com.duckduckgo.user.agent.api.UserAgentProvider
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Named

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(SettingsWebViewScreenWithParams::class)
class SettingsWebViewActivity : DuckDuckGoActivity() {
    @Inject
    lateinit var userAgentProvider: UserAgentProvider

    @Inject
    lateinit var webViewCompat: WebViewCompatWrapper

    @Inject
    lateinit var pixel: Pixel

    private val viewModel: SettingsWebViewViewModel by bindViewModel()

    @Inject
    lateinit var css: CoreContentScopeScripts

    @Inject
    @Named("ContentScopeScripts")
    lateinit var contentScopeScripts: JsMessaging

    private val binding: ActivitySettingsWebviewBinding by viewBinding()

    private val toolbar
        get() = binding.includeToolbar.toolbar

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val params = intent.getActivityParams(SettingsWebViewScreenWithParams::class.java)
        val url = params?.url
        title = params?.screenTitle.orEmpty()

        setContentView(binding.root)
        setupToolbar(toolbar)

        lifecycleScope.launch {
            setupWebView()
            setupCollectors()
            setupBackPressedDispatcher()

            viewModel.onStart(url)
        }
    }

    private fun setupBackPressedDispatcher() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (binding.settingsWebView.canGoBack()) {
                        binding.settingsWebView.goBack()
                    } else {
                        exit()
                    }
                }
            },
        )
    }

    private fun setupCollectors() {
        viewModel.commands
            .flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)
    }

    private fun processCommand(command: SettingsWebViewViewModel.Command) {
        when (command) {
            is SettingsWebViewViewModel.Command.LoadUrl -> binding.settingsWebView.loadUrl(command.url)
            SettingsWebViewViewModel.Command.Exit -> exit()
        }
    }

    private fun exit() {
        binding.settingsWebView.stopLoading()
        binding.settingsWebView.removeJavascriptInterface(contentScopeScripts.context)
        binding.root.removeView(binding.settingsWebView)
        binding.settingsWebView.destroy()

        finish()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun setupWebView() {
        binding.settingsWebView.let {
            // temporary disable CSS until it's needed
            // configureCssMessaging(it)

            it.settings.apply {
                userAgentString = userAgentProvider.userAgent()
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                builtInZoomControls = true
                displayZoomControls = false
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                databaseEnabled = false
                setSupportZoom(true)
            }
        }
    }

    private suspend fun configureCssMessaging(webView: WebView) {
        contentScopeScripts.register(
            webView,
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

        webViewCompat.addDocumentStartJavaScript(
            webView,
            "javascript:${css.getScript(false, emptyList())}",
            setOf(
                "https://duckduckgo.com", // exact origin
                "https://*.duckduckgo.com", // any subdomain
            ),
        )
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }
}
