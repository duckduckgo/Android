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

package com.duckduckgo.pir.impl.dashboard

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.store.AppTheme
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.js.messaging.api.JsCallbackData
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.duckduckgo.js.messaging.api.JsMessaging
import com.duckduckgo.js.messaging.api.SubscriptionEventData
import com.duckduckgo.pir.api.dashboard.PirDashboardWebViewScreen
import com.duckduckgo.pir.impl.databinding.ActivityPirDashboardWebviewBinding
import com.duckduckgo.pir.impl.messaging.PirDashboardWebConstants
import com.duckduckgo.pir.impl.ui.PirDashboardWebViewClient
import com.duckduckgo.pir.impl.ui.PirDashboardWebViewViewModel
import com.duckduckgo.pir.impl.ui.PirDashboardWebViewViewModel.Command
import com.duckduckgo.pir.impl.ui.PirDashboardWebViewViewModel.Command.SendJsEvent
import com.duckduckgo.pir.impl.ui.PirDashboardWebViewViewModel.Command.SendResponseToJs
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.json.JSONObject

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(PirDashboardWebViewScreen::class)
class PirDashboardWebViewActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var webViewClient: PirDashboardWebViewClient

    @Named("PirDashboardWebMessaging")
    @Inject
    lateinit var pirWebJsMessaging: JsMessaging

    private val viewModel: PirDashboardWebViewViewModel by bindViewModel()

    @Inject
    lateinit var appTheme: AppTheme

    private val binding: ActivityPirDashboardWebviewBinding by viewBinding()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupWebView()
        observeCommands()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        pirWebJsMessaging.register(
            binding.pirWebView,
            object : JsMessageCallback() {

                override fun process(
                    featureName: String,
                    method: String,
                    id: String?,
                    data: JSONObject?,
                ) {
                    viewModel.handleJsMessage(
                        featureName = featureName,
                        method = method,
                        id = id,
                        data = data,
                    )
                }
            },
        )

        binding.pirWebView.webViewClient = webViewClient
        binding.pirWebView.webChromeClient = object : WebChromeClient() {

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                message: android.os.Message,
            ): Boolean {
                val transport = message.obj as WebView.WebViewTransport
                transport.webView = binding.pirWebView
                message.sendToTarget()
                return true
            }
        }

        binding.pirWebView.settings.apply {
            userAgentString = PirDashboardWebConstants.CUSTOM_UA
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

        binding.pirWebView.loadUrl(PirDashboardWebConstants.WEB_UI_URL)
    }

    private fun observeCommands() {
        viewModel.commands()
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)
    }

    private fun processCommand(command: Command) {
        when (command) {
            is SendJsEvent -> sendJsEvent(command.event)
            is SendResponseToJs -> sendResponseToJs(command.data)
        }
    }

    private fun sendJsEvent(event: SubscriptionEventData) {
        pirWebJsMessaging.sendSubscriptionEvent(event)
    }

    private fun sendResponseToJs(data: JsCallbackData) {
        pirWebJsMessaging.onResponse(data)
    }
}
