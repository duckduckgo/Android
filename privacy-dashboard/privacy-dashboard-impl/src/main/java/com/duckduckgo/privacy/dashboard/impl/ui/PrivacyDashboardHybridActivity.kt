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
import com.duckduckgo.browser.api.ui.BrowserScreens.FeedbackActivityWithEmptyParams
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.navigation.api.getActivityParams
import com.duckduckgo.privacy.dashboard.api.ui.DashboardOpener
import com.duckduckgo.privacy.dashboard.api.ui.PrivacyDashboardHybridScreenParams
import com.duckduckgo.privacy.dashboard.api.ui.PrivacyDashboardHybridScreenParams.BrokenSiteForm
import com.duckduckgo.privacy.dashboard.api.ui.PrivacyDashboardHybridScreenParams.PrivacyDashboardPrimaryScreen
import com.duckduckgo.privacy.dashboard.api.ui.PrivacyDashboardHybridScreenParams.PrivacyDashboardToggleReportScreen
import com.duckduckgo.privacy.dashboard.api.ui.PrivacyDashboardHybridScreenResult
import com.duckduckgo.privacy.dashboard.impl.databinding.ActivityPrivacyHybridDashboardBinding
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.Command
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.Command.FetchToggleData
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.Command.GoBack
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.Command.LaunchAppFeedback
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.Command.LaunchToggleReport
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
    lateinit var browserNav: BrowserNav

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

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
                onClose = { this@PrivacyDashboardHybridActivity.finish() },
                onShowNativeFeedback = {
                    viewModel.launchAppFeedbackFlow()
                    finish()
                },
                onSubmitBrokenSiteReport = { payload ->
                    val reportFlow = when (val params = params) {
                        is BrokenSiteForm -> {
                            when (params.reportFlow) {
                                BrokenSiteForm.BrokenSiteFormReportFlow.MENU -> ReportFlow.MENU
                                BrokenSiteForm.BrokenSiteFormReportFlow.RELOAD_THREE_TIMES_WITHIN_20_SECONDS ->
                                    ReportFlow.RELOAD_THREE_TIMES_WITHIN_20_SECONDS
                            }
                        }
                        else -> ReportFlow.DASHBOARD
                    }
                    viewModel.onSubmitBrokenSiteReport(payload, reportFlow)
                    setResult(PrivacyDashboardHybridScreenResult.REPORT_SUBMITTED)
                    finish()
                },
                onGetToggleReportOptions = { viewModel.onGetToggleReportOptions() },
                onSendToggleReport = {
                    val opener = params?.opener ?: DashboardOpener.DASHBOARD
                    viewModel.onSubmitToggleReport(opener)
                },
                onRejectToggleReport = {
                    viewModel.onToggleReportPromptDismissed()
                    this@PrivacyDashboardHybridActivity.finish()
                },
                onSeeWhatIsSent = {},
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
            is PrivacyDashboardToggleReportScreen -> InitialScreen.TOGGLE_REPORT
        }

        val toggleOpener = params?.opener ?: DashboardOpener.NONE

        dashboardRenderer.loadDashboard(webView, initialScreen, toggleOpener)
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
            is LaunchAppFeedback -> {
                globalActivityStarter.startIntent(this, FeedbackActivityWithEmptyParams)?.let { startActivity(it) }
            }
            is FetchToggleData -> fetchToggleData(it.toggleData)
            is LaunchToggleReport -> {
                params?.tabId?.let { tabId ->
                    globalActivityStarter.startIntent(this, PrivacyDashboardToggleReportScreen(tabId, opener = DashboardOpener.DASHBOARD))
                        ?.let { startActivity(it) }
                }
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

    private fun fetchToggleData(data: String) {
        webView.post {
            webView.evaluateJavascript(
                "javascript:window.onGetToggleReportOptionsResponse($data);",
                null,
            )
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
        if (params is PrivacyDashboardToggleReportScreen) {
            viewModel.onToggleReportPromptDismissed()
        }
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
