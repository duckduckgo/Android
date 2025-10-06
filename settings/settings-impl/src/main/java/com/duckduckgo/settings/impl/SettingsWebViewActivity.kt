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
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.js.messaging.api.JsMessaging
import com.duckduckgo.navigation.api.getActivityParams
import com.duckduckgo.settings.api.SettingsWebViewScreenWithParams
import com.duckduckgo.settings.impl.databinding.ActivitySettingsWebviewBinding
import com.duckduckgo.user.agent.api.UserAgentProvider
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Named

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(SettingsWebViewScreenWithParams::class)
class SettingsWebViewActivity : DuckDuckGoActivity() {
    @Inject
    lateinit var userAgentProvider: UserAgentProvider

    @Inject
    lateinit var pixel: Pixel

    private val viewModel: SettingsWebViewViewModel by bindViewModel()

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
        binding.root.removeView(binding.settingsWebView)
        binding.settingsWebView.destroy()

        finish()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        binding.settingsWebView.let {
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
