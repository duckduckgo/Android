/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.privacy.dashboard.impl.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.lifecycle.Lifecycle.State.STARTED
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.webview.enableDarkMode
import com.duckduckgo.app.browser.webview.enableLightMode
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.app.tabs.tabId
import com.duckduckgo.browser.api.brokensite.BrokenSiteNav
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.ui.DuckDuckGoTheme
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import com.duckduckgo.privacy.dashboard.impl.databinding.ActivityPrivacyHybridDashboardBinding
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.Command
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.Command.LaunchReportBrokenSite
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@InjectWith(ActivityScope::class)
class PrivacyDashboardHybridActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var repository: TabRepository

    @Inject
    lateinit var pixel: Pixel

    @Inject
    lateinit var rendererFactory: PrivacyDashboardRendererFactory

    @Inject
    lateinit var brokenSiteNav: BrokenSiteNav

    private val binding: ActivityPrivacyHybridDashboardBinding by viewBinding()

    private val webView
        get() = binding.privacyDashboardWebview

    private val dashboardRenderer by lazy {
        rendererFactory.createRenderer(RendererViewHolder.WebviewRenderer(webView, { userChangedValues -> updateActivityResult(userChangedValues) }))
    }

    private val viewModel: PrivacyDashboardHybridViewModel by bindViewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        configureWebView()
        Timber.i("PDHy: onCreate")
        webView.loadUrl("file:///android_asset/html/popup.html")
        repository.retrieveSiteData(intent.tabId!!).observe(
            this
        ) {
            viewModel.onSiteChanged(it)
        }

        lifecycleScope.launch {
            viewModel.commands()
                .flowWithLifecycle(lifecycle, STARTED)
                .collectLatest { processCommands(it) }
        }
    }

    private fun processCommands(it: Command) {
        when (it) {
            is LaunchReportBrokenSite -> {
                startActivity(brokenSiteNav.navigate(this, it.data))
            }
        }
    }

    private fun configureWebView() {
        with(webView.settings) {
            builtInZoomControls = false
            javaScriptEnabled = true
            configureDarkThemeSupport(this)
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                url: String?
            ): Boolean {
                return false
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                return false
            }

            override fun onPageFinished(
                view: WebView?,
                url: String?
            ) {
                super.onPageFinished(view, url)
                Timber.i("PDHy: onPageFinished")
                setupObservers()
            }
        }

        webView.addJavascriptInterface(
            PrivacyDashboardJavascriptInterface(
                onBrokenSiteClicked = { viewModel.onReportBrokenSiteSelected() },
                onPrivacyProtectionsClicked = { newValue ->
                    viewModel.onPrivacyProtectionsClicked(newValue)
                },
                onClose = { finish() }
            ),
            PrivacyDashboardJavascriptInterface.JAVASCRIPT_INTERFACE_NAME
        )
    }

    private fun configureDarkThemeSupport(webSettings: WebSettings) {
        when (themingDataStore.theme) {
            DuckDuckGoTheme.LIGHT -> webSettings.enableLightMode()
            DuckDuckGoTheme.DARK -> webSettings.enableDarkMode()
            DuckDuckGoTheme.SYSTEM_DEFAULT -> {
                val currentNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                if (currentNightMode == Configuration.UI_MODE_NIGHT_YES) {
                    Timber.i("PDHy: trying night mode")
                    webSettings.enableDarkMode()
                } else {
                    Timber.i("PDHy: trying light mode")
                    webSettings.enableLightMode()
                }
            }
        }
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.viewState()
                .flowWithLifecycle(lifecycle, STARTED)
                .collectLatest {
                    if (it == null) return@collectLatest
                    dashboardRenderer.render(it)
                }
        }
    }

    private fun updateActivityResult(shouldReload: Boolean) {
        Timber.i("PDHy: updateActivityResult $shouldReload")
        if (shouldReload) {
            setResult(RELOAD_RESULT_CODE)
            finish()
        } else {
            setResult(Activity.RESULT_OK)
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    companion object {

        const val RELOAD_RESULT_CODE = 100

        fun intent(
            context: Context,
            tabId: String
        ): Intent {
            val intent = Intent(context, PrivacyDashboardHybridActivity::class.java)
            intent.tabId = tabId
            return intent
        }
    }
}
