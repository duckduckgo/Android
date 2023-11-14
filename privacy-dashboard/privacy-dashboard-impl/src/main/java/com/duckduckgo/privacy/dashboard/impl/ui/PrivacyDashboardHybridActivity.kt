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

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.lifecycle.Lifecycle.State.STARTED
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.tabs.BrowserNav
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.autoconsent.api.AutoconsentNav
import com.duckduckgo.browser.api.brokensite.BrokenSiteNav
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.store.AppTheme
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.webview.enableDarkMode
import com.duckduckgo.common.utils.webview.enableLightMode
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.navigation.api.getActivityParams
import com.duckduckgo.privacy.dashboard.api.ui.PrivacyDashboardHybridScreen.PrivacyDashboardHybridWithTabIdParam
import com.duckduckgo.privacy.dashboard.impl.databinding.ActivityPrivacyHybridDashboardBinding
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.Command
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.Command.LaunchReportBrokenSite
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.Command.OpenSettings
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.Command.OpenURL
import javax.inject.Inject
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(PrivacyDashboardHybridWithTabIdParam::class)
class PrivacyDashboardHybridActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var repository: TabRepository

    @Inject
    lateinit var pixel: Pixel

    @Inject
    lateinit var rendererFactory: PrivacyDashboardRendererFactory

    @Inject
    lateinit var autoconsentNav: AutoconsentNav

    @Inject
    lateinit var brokenSiteNav: BrokenSiteNav

    @Inject
    lateinit var browserNav: BrowserNav

    @Inject
    lateinit var appTheme: AppTheme

    private val binding: ActivityPrivacyHybridDashboardBinding by viewBinding()

    private val webView
        get() = binding.privacyDashboardWebview

    private val dashboardRenderer by lazy {
        rendererFactory.createRenderer(
            RendererViewHolder.WebviewRenderer(
                holder = webView,
                onPrivacyProtectionSettingChanged = { userChangedValues -> if (userChangedValues) finish() },
                onPrivacyProtectionsClicked = { newValue ->
                    viewModel.onPrivacyProtectionsClicked(newValue)
                },
                onUrlClicked = { payload ->
                    viewModel.onUrlClicked(payload)
                },
                onOpenSettings = { payload ->
                    viewModel.onOpenSettings(payload)
                },
                onBrokenSiteClicked = { viewModel.onReportBrokenSiteSelected() },
                onClose = { this@PrivacyDashboardHybridActivity.finish() },
            ),
        )
    }

    private val viewModel: PrivacyDashboardHybridViewModel by bindViewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        configureWebView()
        dashboardRenderer.loadDashboard(webView)
        configureObservers()
    }

    private fun configureObservers() {
        val tabIdParam = intent.getActivityParams(PrivacyDashboardHybridWithTabIdParam::class.java)!!.tabId
        repository.retrieveSiteData(tabIdParam).observe(
            this,
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
            is OpenURL -> openUrl(it.url)
            is OpenSettings -> openSettings(it.target)
        }
    }

    private fun openUrl(url: String) {
        startActivity(browserNav.openInNewTab(this, url))
        finish()
    }

    private fun openSettings(target: String) {
        if (target == "cpm") {
            startActivity(autoconsentNav.openAutoconsentSettings(this))
        } else {
            finish()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        with(webView.settings) {
            builtInZoomControls = false
            javaScriptEnabled = true
            configureDarkThemeSupport(this)
        }

        webView.webViewClient = object : WebViewClient() {
            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                url: String?,
            ): Boolean {
                return false
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?,
            ): Boolean {
                return false
            }

            override fun onPageFinished(
                view: WebView?,
                url: String?,
            ) {
                super.onPageFinished(view, url)
                configViewStateObserver()
            }
        }
    }

    private fun configureDarkThemeSupport(webSettings: WebSettings) {
        when (appTheme.isLightModeEnabled()) {
            true -> webSettings.enableLightMode()
            false -> webSettings.enableDarkMode()
        }
    }

    private fun configViewStateObserver() {
        lifecycleScope.launch {
            viewModel.viewState()
                .flowWithLifecycle(lifecycle, STARTED)
                .collectLatest {
                    if (it == null) return@collectLatest
                    binding.loadingIndicator.hide()
                    dashboardRenderer.render(it)
                }
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
