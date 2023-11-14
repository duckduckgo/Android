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
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.duckduckgo.js.messaging.api.JsMessaging
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.navigation.api.getActivityParams
import com.duckduckgo.subscriptions.impl.databinding.ActivitySubscriptionsWebviewBinding
import com.duckduckgo.subscriptions.impl.messaging.SubscriptionCallback
import com.duckduckgo.user.agent.api.UserAgentProvider
import javax.inject.Inject
import javax.inject.Named

data class SubscriptionsWebViewActivityWithParams(
    val url: String,
    val screenTitle: String,
) : GlobalActivityStarter.ActivityParams

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(SubscriptionsWebViewActivityWithParams::class)
class SubscriptionsWebViewActivity : DuckDuckGoActivity(), SubscriptionCallback {

    @Inject
    @Named("Subscriptions")
    lateinit var subscriptionJsMessaging: JsMessaging

    @Inject
    lateinit var userAgent: UserAgentProvider

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
        binding.simpleWebview.let {
            subscriptionJsMessaging.register(
                it,
                object : JsMessageCallback(this) {
                    override fun process(method: String) {
                        runCatching {
                            callback.javaClass.getDeclaredMethod(method)
                        }.getOrNull()?.invoke(callback)
                    }
                },
            )
            it.webChromeClient = WebChromeClient()
            it.settings.apply {
                userAgentString = userAgent.userAgent()
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
            binding.simpleWebview.loadUrl(it)
        }
    }

    override fun backToSettings() {
        finish()
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
