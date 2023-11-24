/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.subscriptions.impl.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.MenuItem
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebViewClient
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.dialog.TextAlertDialogBuilder
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.js.messaging.api.JsCallbackData
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.duckduckgo.js.messaging.api.JsMessaging
import com.duckduckgo.js.messaging.api.SubscriptionEventData
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.navigation.api.getActivityParams
import com.duckduckgo.subscriptions.impl.R.string
import com.duckduckgo.subscriptions.impl.databinding.ActivitySubscriptionsWebviewBinding
import com.duckduckgo.subscriptions.impl.ui.AddDeviceActivity.Companion.AddDeviceScreenWithEmptyParams
import com.duckduckgo.subscriptions.impl.ui.RestoreSubscriptionActivity.Companion.RestoreSubscriptionScreenWithEmptyParams
import com.duckduckgo.subscriptions.impl.ui.SubscriptionWebViewViewModel.Command
import com.duckduckgo.subscriptions.impl.ui.SubscriptionWebViewViewModel.Command.ActivateOnAnotherDevice
import com.duckduckgo.subscriptions.impl.ui.SubscriptionWebViewViewModel.Command.BackToSettings
import com.duckduckgo.subscriptions.impl.ui.SubscriptionWebViewViewModel.Command.RestoreSubscription
import com.duckduckgo.subscriptions.impl.ui.SubscriptionWebViewViewModel.Command.SendResponseToJs
import com.duckduckgo.subscriptions.impl.ui.SubscriptionWebViewViewModel.Command.SubscriptionSelected
import com.duckduckgo.subscriptions.impl.ui.SubscriptionWebViewViewModel.PurchaseStateView
import com.duckduckgo.user.agent.api.UserAgentProvider
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.json.JSONObject

data class SubscriptionsWebViewActivityWithParams(
    val url: String,
    val screenTitle: String,
) : GlobalActivityStarter.ActivityParams

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(SubscriptionsWebViewActivityWithParams::class)
class SubscriptionsWebViewActivity : DuckDuckGoActivity() {

    @Inject
    @Named("Subscriptions")
    lateinit var subscriptionJsMessaging: JsMessaging

    @Inject
    lateinit var userAgent: UserAgentProvider

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    private val viewModel: SubscriptionWebViewViewModel by bindViewModel()

    private val binding: ActivitySubscriptionsWebviewBinding by viewBinding()

    private val toolbar
        get() = binding.includeToolbar.toolbar

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)
        setupToolbar(toolbar)

        val params = intent.getActivityParams(SubscriptionsWebViewActivityWithParams::class.java)
        val url = params?.url

        title = params?.screenTitle.orEmpty()
        binding.webview.let {
            subscriptionJsMessaging.register(
                it,
                object : JsMessageCallback() {
                    override fun process(featureName: String, method: String, id: String?, data: JSONObject?) {
                        viewModel.processJsCallbackMessage(featureName, method, id, data)
                    }
                },
            )
            it.webChromeClient = WebChromeClient()
            it.webViewClient = WebViewClient()
            it.settings.apply {
                userAgentString = userAgent.userAgent(url)
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
        }

        url?.let {
            binding.webview.loadUrl(it)
        }

        viewModel.start()

        viewModel.commands()
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)

        viewModel.currentPurchaseViewState.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).distinctUntilChanged().onEach {
            renderPurchaseState(it.purchaseState)
        }.launchIn(lifecycleScope)
    }

    private fun processCommand(command: Command) {
        when (command) {
            is BackToSettings -> backToSettings()
            is SendResponseToJs -> sendResponseToJs(command.data)
            is SubscriptionSelected -> selectSubscription(command.id)
            is ActivateOnAnotherDevice -> activateOnAnotherDevice()
            is RestoreSubscription -> restoreSubscription()
        }
    }

    private fun renderPurchaseState(purchaseState: PurchaseStateView) {
        when (purchaseState) {
            is PurchaseStateView.InProgress -> {
                binding.webview.gone()
                binding.progress.show()
            }
            is PurchaseStateView.Inactive -> {
                binding.webview.show()
                binding.progress.gone()
            }
            is PurchaseStateView.Success -> {
                binding.webview.show()
                binding.progress.gone()
                onPurchaseSuccess(purchaseState.subscriptionEventData)
            }
            is PurchaseStateView.Recovered -> {
                binding.webview.show()
                binding.progress.gone()
                onPurchaseRecovered()
            }
            is PurchaseStateView.Failure -> {
                binding.webview.show()
                binding.progress.gone()
                onPurchaseFailure(purchaseState.message)
            }
        }
    }

    private fun onPurchaseRecovered() {
        TextAlertDialogBuilder(this)
            .setTitle(getString(string.purchaseCompletedTitle))
            .setMessage(getString(string.purchaseRecoveredText))
            .setPositiveButton(string.ok)
            .addEventListener(
                object : TextAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked() {
                        finish()
                    }
                },
            )
            .show()
    }

    private fun onPurchaseSuccess(subscriptionEventData: SubscriptionEventData) {
        TextAlertDialogBuilder(this)
            .setTitle(getString(string.purchaseCompletedTitle))
            .setMessage(getString(string.purchaseCompletedText))
            .setPositiveButton(string.ok)
            .addEventListener(
                object : TextAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked() {
                        subscriptionJsMessaging.sendSubscriptionEvent(subscriptionEventData)
                    }
                },
            )
            .show()
    }

    private fun onPurchaseFailure(message: String) {
        TextAlertDialogBuilder(this)
            .setTitle(getString(string.purchaseErrorTitle))
            .setMessage(message)
            .setDestructiveButtons(true)
            .setPositiveButton(string.ok)
            .addEventListener(
                object : TextAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked() {
                        // NOOP
                    }
                },
            )
            .show()
    }

    private fun selectSubscription(id: String) {
        viewModel.purchaseSubscription(this, id)
    }

    private fun sendResponseToJs(data: JsCallbackData) {
        subscriptionJsMessaging.onResponse(data)
    }

    private fun backToSettings() {
        finish()
    }

    private fun activateOnAnotherDevice() {
        globalActivityStarter.start(this, AddDeviceScreenWithEmptyParams)
    }

    private fun restoreSubscription() {
        globalActivityStarter.start(this, RestoreSubscriptionScreenWithEmptyParams)
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
        if (binding.webview.canGoBack()) {
            binding.webview.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
