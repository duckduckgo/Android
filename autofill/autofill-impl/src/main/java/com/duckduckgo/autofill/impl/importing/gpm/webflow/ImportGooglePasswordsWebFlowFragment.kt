/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.autofill.impl.importing.gpm.webflow

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebViewCompat
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autofill.api.AutofillCapabilityChecker
import com.duckduckgo.autofill.api.AutofillFragmentResultsPlugin
import com.duckduckgo.autofill.api.BrowserAutofill
import com.duckduckgo.autofill.api.CredentialAutofillDialogFactory
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.api.domain.app.LoginTriggerType
import com.duckduckgo.autofill.impl.R
import com.duckduckgo.autofill.impl.databinding.FragmentImportGooglePasswordsWebflowBinding
import com.duckduckgo.autofill.impl.importing.blob.GooglePasswordBlobConsumer
import com.duckduckgo.autofill.impl.importing.gpm.feature.AutofillImportPasswordConfigStore
import com.duckduckgo.autofill.impl.importing.gpm.webflow.ImportGooglePasswordResult.Companion.RESULT_KEY
import com.duckduckgo.autofill.impl.importing.gpm.webflow.ImportGooglePasswordResult.Companion.RESULT_KEY_DETAILS
import com.duckduckgo.autofill.impl.importing.gpm.webflow.ImportGooglePasswordsWebFlowViewModel.UserCannotImportReason
import com.duckduckgo.autofill.impl.importing.gpm.webflow.ImportGooglePasswordsWebFlowViewModel.ViewState.Initializing
import com.duckduckgo.autofill.impl.importing.gpm.webflow.ImportGooglePasswordsWebFlowViewModel.ViewState.LoadStartPage
import com.duckduckgo.autofill.impl.importing.gpm.webflow.ImportGooglePasswordsWebFlowViewModel.ViewState.NavigatingBack
import com.duckduckgo.autofill.impl.importing.gpm.webflow.ImportGooglePasswordsWebFlowViewModel.ViewState.UserCancelledImportFlow
import com.duckduckgo.autofill.impl.importing.gpm.webflow.ImportGooglePasswordsWebFlowViewModel.ViewState.UserFinishedCannotImport
import com.duckduckgo.autofill.impl.importing.gpm.webflow.ImportGooglePasswordsWebFlowViewModel.ViewState.UserFinishedImportFlow
import com.duckduckgo.autofill.impl.importing.gpm.webflow.ImportGooglePasswordsWebFlowViewModel.ViewState.WebContentShowing
import com.duckduckgo.autofill.impl.importing.gpm.webflow.ImportGooglePasswordsWebFlowWebViewClient.NewPageCallback
import com.duckduckgo.autofill.impl.importing.gpm.webflow.autofill.NoOpAutofillCallback
import com.duckduckgo.autofill.impl.importing.gpm.webflow.autofill.NoOpAutofillEventListener
import com.duckduckgo.autofill.impl.importing.gpm.webflow.autofill.NoOpEmailProtectionInContextSignupFlowListener
import com.duckduckgo.autofill.impl.importing.gpm.webflow.autofill.NoOpEmailProtectionUserPromptListener
import com.duckduckgo.common.ui.DuckDuckGoFragment
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.FragmentViewModelFactory
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.user.agent.api.UserAgentProvider
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority.WARN
import logcat.logcat

@InjectWith(FragmentScope::class)
class ImportGooglePasswordsWebFlowFragment :
    DuckDuckGoFragment(R.layout.fragment_import_google_passwords_webflow),
    NewPageCallback,
    NoOpAutofillCallback,
    NoOpEmailProtectionInContextSignupFlowListener,
    NoOpEmailProtectionUserPromptListener,
    NoOpAutofillEventListener,
    GooglePasswordBlobConsumer.Callback {

    @Inject
    lateinit var userAgentProvider: UserAgentProvider

    @Inject
    lateinit var dispatchers: DispatcherProvider

    @Inject
    lateinit var pixel: Pixel

    @Inject
    lateinit var viewModelFactory: FragmentViewModelFactory

    @Inject
    lateinit var autofillCapabilityChecker: AutofillCapabilityChecker

    @Inject
    lateinit var credentialAutofillDialogFactory: CredentialAutofillDialogFactory

    @Inject
    lateinit var browserAutofill: BrowserAutofill

    @Inject
    lateinit var autofillFragmentResultListeners: PluginPoint<AutofillFragmentResultsPlugin>

    @Inject
    lateinit var passwordBlobConsumer: GooglePasswordBlobConsumer

    @Inject
    lateinit var passwordImporterScriptLoader: PasswordImporterScriptLoader

    @Inject
    lateinit var browserAutofillConfigurator: BrowserAutofill.Configurator

    @Inject
    lateinit var importPasswordConfig: AutofillImportPasswordConfigStore

    private var binding: FragmentImportGooglePasswordsWebflowBinding? = null

    private val viewModel by lazy {
        ViewModelProvider(requireActivity(), viewModelFactory)[ImportGooglePasswordsWebFlowViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        binding = FragmentImportGooglePasswordsWebflowBinding.inflate(inflater, container, false)
        return binding?.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        initialiseToolbar()
        configureWebView()
        configureBackButtonHandler()
        observeViewState()
        viewModel.onViewCreated()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private fun loadFirstWebpage(url: String) {
        lifecycleScope.launch(dispatchers.main()) {
            binding?.webView?.let {
                it.loadUrl(url)
                viewModel.firstPageLoading()
            }
        }
    }

    private fun observeViewState() {
        viewModel.viewState
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { viewState ->
                when (viewState) {
                    is UserFinishedImportFlow -> exitFlowAsSuccess()
                    is UserCancelledImportFlow -> exitFlowAsCancellation(viewState.stage)
                    is UserFinishedCannotImport -> exitFlowAsImpossibleToImport(viewState.reason)
                    is NavigatingBack -> binding?.webView?.goBack()
                    is LoadStartPage -> loadFirstWebpage(viewState.initialLaunchUrl)
                    is WebContentShowing, Initializing -> {
                        // no-op
                    }
                }
            }
            .launchIn(lifecycleScope)
    }

    private fun exitFlowAsCancellation(stage: String) {
        (activity as ImportGooglePasswordsWebFlowActivity).exitUserCancelled(stage)
    }

    private fun exitFlowAsSuccess() {
        val resultBundle = Bundle().also {
            it.putParcelable(RESULT_KEY_DETAILS, ImportGooglePasswordResult.Success)
        }
        setFragmentResult(RESULT_KEY, resultBundle)
    }

    private fun exitFlowAsImpossibleToImport(reason: UserCannotImportReason) {
        val resultBundle = Bundle().also {
            it.putParcelable(RESULT_KEY_DETAILS, ImportGooglePasswordResult.Error(reason))
        }
        setFragmentResult(RESULT_KEY, resultBundle)
    }

    private fun configureBackButtonHandler() {
        activity?.let {
            it.onBackPressedDispatcher.addCallback(
                it,
                object : OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() {
                        viewModel.onBackButtonPressed(url = binding?.webView?.url, canGoBack = binding?.webView?.canGoBack() ?: false)
                    }
                },
            )
        }
    }

    private fun initialiseToolbar() {
        with(getToolbar()) {
            title = getString(R.string.autofillImportGooglePasswordsWebFlowTitle)
            setNavigationIconAsCross()
            setNavigationOnClickListener { viewModel.onCloseButtonPressed(binding?.webView?.url) }
        }
    }

    private fun Toolbar.setNavigationIconAsCross() {
        setNavigationIcon(com.duckduckgo.mobile.android.R.drawable.ic_close_24)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        binding?.webView?.let { webView ->
            webView.webViewClient = ImportGooglePasswordsWebFlowWebViewClient(this)

            webView.settings.apply {
                userAgentString = userAgentProvider.userAgent()
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

            configureDownloadInterceptor(webView)
            configureAutofill(webView)

            lifecycleScope.launch {
                passwordBlobConsumer.configureWebViewForBlobDownload(webView, this@ImportGooglePasswordsWebFlowFragment)
                configurePasswordImportJavascript(webView)
            }
        }
    }

    private fun configureAutofill(it: WebView) {
        lifecycleScope.launch {
            browserAutofill.addJsInterface(
                it,
                this@ImportGooglePasswordsWebFlowFragment,
                this@ImportGooglePasswordsWebFlowFragment,
                this@ImportGooglePasswordsWebFlowFragment,
                CUSTOM_FLOW_TAB_ID,
            )
        }

        autofillFragmentResultListeners.getPlugins().forEach { plugin ->
            setFragmentResultListener(plugin.resultKey(CUSTOM_FLOW_TAB_ID)) { _, result ->
                context?.let { ctx ->
                    plugin.processResult(
                        result = result,
                        context = ctx,
                        tabId = CUSTOM_FLOW_TAB_ID,
                        fragment = this@ImportGooglePasswordsWebFlowFragment,
                        autofillCallback = this@ImportGooglePasswordsWebFlowFragment,
                    )
                }
            }
        }
    }

    private fun configureDownloadInterceptor(it: WebView) {
        it.setDownloadListener { url, _, _, _, _ ->
            if (url.startsWith("blob:")) {
                lifecycleScope.launch {
                    passwordBlobConsumer.postMessageToConvertBlobToDataUri(url)
                }
            }
        }
    }

    @SuppressLint("RequiresFeature")
    private suspend fun configurePasswordImportJavascript(webView: WebView) {
        if (importPasswordConfig.getConfig().canInjectJavascript) {
            val script = passwordImporterScriptLoader.getScript()
            WebViewCompat.addDocumentStartJavaScript(webView, script, setOf("*"))
        }
    }

    private fun getToolbar() = (activity as ImportGooglePasswordsWebFlowActivity).binding.includeToolbar.toolbar

    override fun onPageStarted(url: String?) {
        binding?.let {
            browserAutofillConfigurator.configureAutofillForCurrentPage(it.webView, url)
        }
    }

    override suspend fun onCredentialsAvailableToInject(
        originalUrl: String,
        credentials: List<LoginCredentials>,
        triggerType: LoginTriggerType,
    ) {
        withContext(dispatchers.main()) {
            val url = binding?.webView?.url ?: return@withContext
            if (url != originalUrl) {
                logcat(WARN) { "WebView url has changed since autofill request; bailing" }
                return@withContext
            }

            val dialog = credentialAutofillDialogFactory.autofillSelectCredentialsDialog(
                url,
                credentials,
                triggerType,
                CUSTOM_FLOW_TAB_ID,
            )
            dialog.show(childFragmentManager, SELECT_CREDENTIALS_FRAGMENT_TAG)
        }
    }

    override suspend fun onCsvAvailable(csv: String) {
        viewModel.onCsvAvailable(csv)
    }

    override suspend fun onCsvError() {
        viewModel.onCsvError()
    }

    override fun onShareCredentialsForAutofill(
        originalUrl: String,
        selectedCredentials: LoginCredentials,
    ) {
        if (binding?.webView?.url != originalUrl) {
            logcat(WARN) { "WebView url has changed since autofill request; bailing" }
            return
        }
        browserAutofill.injectCredentials(selectedCredentials)
    }

    override fun onNoCredentialsChosenForAutofill(originalUrl: String) {
        if (binding?.webView?.url != originalUrl) {
            logcat(WARN) { "WebView url has changed since autofill request; bailing" }
            return
        }
        browserAutofill.injectCredentials(null)
    }

    companion object {
        private const val CUSTOM_FLOW_TAB_ID = "import-passwords-webflow"
        private const val SELECT_CREDENTIALS_FRAGMENT_TAG = "autofillSelectCredentialsDialog"
    }
}
