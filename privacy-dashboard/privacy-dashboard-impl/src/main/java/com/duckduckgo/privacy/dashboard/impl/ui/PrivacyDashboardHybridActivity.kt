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
import com.duckduckgo.brokensite.api.ReportFlow
import com.duckduckgo.browser.api.brokensite.BrokenSiteNav
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.navigation.api.getActivityParams
import com.duckduckgo.privacy.dashboard.api.ui.PrivacyDashboardHybridScreenParams
import com.duckduckgo.privacy.dashboard.api.ui.PrivacyDashboardHybridScreenParams.BrokenSiteForm
import com.duckduckgo.privacy.dashboard.api.ui.PrivacyDashboardHybridScreenParams.PrivacyDashboardPrimaryScreen
import com.duckduckgo.privacy.dashboard.impl.databinding.ActivityPrivacyHybridDashboardBinding
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.Command
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.Command.GoBack
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.Command.LaunchReportBrokenSite
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.Command.OpenSettings
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.Command.OpenURL
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardRenderer.InitialScreen
import javax.inject.Inject
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(PrivacyDashboardHybridScreenParams::class)
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

    private val binding: ActivityPrivacyHybridDashboardBinding by viewBinding()

    private val webView
        get() = binding.privacyDashboardWebview

    private val dashboardRenderer by lazy {
        rendererFactory.createRenderer(
            RendererViewHolder.WebviewRenderer(
                holder = webView,
                onPrivacyProtectionSettingChanged = { userChangedValues -> if (userChangedValues) finish() },
                onPrivacyProtectionsClicked = { payload ->
                    viewModel.onPrivacyProtectionsClicked(payload, dashboardOpenedFromCustomTab())
                },
                onUrlClicked = { payload ->
                    viewModel.onUrlClicked(payload)
                },
                onOpenSettings = { payload ->
                    viewModel.onOpenSettings(payload)
                },
                onBrokenSiteClicked = { viewModel.onReportBrokenSiteSelected() },
                onClose = { this@PrivacyDashboardHybridActivity.finish() },
                onSubmitBrokenSiteReport = { payload ->
                    val reportFlow = when (params) {
                        is PrivacyDashboardPrimaryScreen, null -> ReportFlow.DASHBOARD
                        is BrokenSiteForm -> ReportFlow.MENU
                    }
                    viewModel.onSubmitBrokenSiteReport(payload, reportFlow)
                },
            ),
        )
    }

    private val viewModel: PrivacyDashboardHybridViewModel by bindViewModel()

    private val params: PrivacyDashboardHybridScreenParams?
        get() = intent.getActivityParams(PrivacyDashboardHybridScreenParams::class.java)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        configureWebView()

        val initialScreen = when (params) {
            is PrivacyDashboardPrimaryScreen, null -> InitialScreen.PRIMARY
            is BrokenSiteForm -> InitialScreen.BREAKAGE_FORM
        }

        dashboardRenderer.loadDashboard(webView, initialScreen)
        configureObservers()
    }

    private fun configureObservers() {
        repository.retrieveSiteData(params!!.tabId).observe(
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
            GoBack -> {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    finish()
                }
            }
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

    private fun dashboardOpenedFromCustomTab(): Boolean {
        return params?.tabId?.startsWith("CustomTab-") ?: false
    }
}
