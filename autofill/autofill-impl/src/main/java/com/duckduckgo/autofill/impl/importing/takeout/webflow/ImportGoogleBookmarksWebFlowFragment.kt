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

package com.duckduckgo.autofill.impl.importing.takeout.webflow

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withStarted
import androidx.webkit.WebViewCompat
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.autofill.api.AutofillFeature
import com.duckduckgo.autofill.api.AutofillFragmentResultsPlugin
import com.duckduckgo.autofill.api.BrowserAutofill
import com.duckduckgo.autofill.api.CredentialAutofillDialogFactory
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.api.domain.app.LoginTriggerType
import com.duckduckgo.autofill.api.domain.app.LoginTriggerType.AUTOPROMPT
import com.duckduckgo.autofill.impl.InternalCallback
import com.duckduckgo.autofill.impl.R
import com.duckduckgo.autofill.impl.configuration.InternalBrowserAutofillConfigurator
import com.duckduckgo.autofill.impl.databinding.FragmentImportGoogleBookmarksWebflowBinding
import com.duckduckgo.autofill.impl.importing.gpm.webflow.GoogleImporterScriptLoader
import com.duckduckgo.autofill.impl.importing.gpm.webflow.autofill.NoOpAutofillEventListener
import com.duckduckgo.autofill.impl.importing.gpm.webflow.autofill.NoOpEmailProtectionInContextSignupFlowListener
import com.duckduckgo.autofill.impl.importing.gpm.webflow.autofill.NoOpEmailProtectionUserPromptListener
import com.duckduckgo.autofill.impl.importing.takeout.store.BookmarkImportConfigStore
import com.duckduckgo.autofill.impl.importing.takeout.webflow.ImportGoogleBookmarksWebFlowViewModel.Command.ExitFlowAsFailure
import com.duckduckgo.autofill.impl.importing.takeout.webflow.ImportGoogleBookmarksWebFlowViewModel.Command.ExitFlowWithSuccess
import com.duckduckgo.autofill.impl.importing.takeout.webflow.ImportGoogleBookmarksWebFlowViewModel.Command.InjectCredentialsFromReauth
import com.duckduckgo.autofill.impl.importing.takeout.webflow.ImportGoogleBookmarksWebFlowViewModel.Command.NoCredentialsAvailable
import com.duckduckgo.autofill.impl.importing.takeout.webflow.ImportGoogleBookmarksWebFlowViewModel.Command.PromptUserToConfirmFlowCancellation
import com.duckduckgo.autofill.impl.importing.takeout.webflow.ImportGoogleBookmarksWebFlowViewModel.Command.PromptUserToSelectFromStoredCredentials
import com.duckduckgo.autofill.impl.importing.takeout.webflow.ImportGoogleBookmarksWebFlowViewModel.ViewState.HideWebPage
import com.duckduckgo.autofill.impl.importing.takeout.webflow.ImportGoogleBookmarksWebFlowViewModel.ViewState.Initializing
import com.duckduckgo.autofill.impl.importing.takeout.webflow.ImportGoogleBookmarksWebFlowViewModel.ViewState.LoadingWebPage
import com.duckduckgo.autofill.impl.importing.takeout.webflow.ImportGoogleBookmarksWebFlowViewModel.ViewState.NavigatingBack
import com.duckduckgo.autofill.impl.importing.takeout.webflow.ImportGoogleBookmarksWebFlowViewModel.ViewState.ShowError
import com.duckduckgo.autofill.impl.importing.takeout.webflow.ImportGoogleBookmarksWebFlowViewModel.ViewState.ShowWebPage
import com.duckduckgo.autofill.impl.importing.takeout.webflow.ImportGoogleBookmarksWebFlowViewModel.ViewState.UserCancelledImportFlow
import com.duckduckgo.autofill.impl.importing.takeout.webflow.ImportGoogleBookmarksWebFlowViewModel.ViewState.UserFinishedCannotImport
import com.duckduckgo.autofill.impl.importing.takeout.webflow.UserCannotImportReason.DownloadError
import com.duckduckgo.autofill.impl.importing.takeout.webflow.UserCannotImportReason.ErrorParsingBookmarks
import com.duckduckgo.autofill.impl.importing.takeout.webflow.UserCannotImportReason.Unknown
import com.duckduckgo.autofill.impl.importing.takeout.webflow.UserCannotImportReason.WebAutomationError
import com.duckduckgo.autofill.impl.importing.takeout.webflow.UserCannotImportReason.WebViewError
import com.duckduckgo.autofill.impl.jsbridge.request.SupportedAutofillInputSubType
import com.duckduckgo.autofill.impl.jsbridge.request.SupportedAutofillInputSubType.PASSWORD
import com.duckduckgo.autofill.impl.store.ReAuthenticationDetails
import com.duckduckgo.common.ui.DuckDuckGoFragment
import com.duckduckgo.common.ui.view.button.ButtonType.DESTRUCTIVE
import com.duckduckgo.common.ui.view.button.ButtonType.GHOST_ALT
import com.duckduckgo.common.ui.view.dialog.DaxAlertDialog
import com.duckduckgo.common.ui.view.dialog.TextAlertDialogBuilder
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.FragmentViewModelFactory
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.user.agent.api.UserAgentProvider
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority.WARN
import logcat.logcat
import javax.inject.Inject

@InjectWith(FragmentScope::class)
class ImportGoogleBookmarksWebFlowFragment :
    DuckDuckGoFragment(R.layout.fragment_import_google_bookmarks_webflow),
    InternalCallback,
    NoOpEmailProtectionInContextSignupFlowListener,
    NoOpEmailProtectionUserPromptListener,
    NoOpAutofillEventListener,
    ImportGoogleBookmarksWebFlowWebViewClient.WebFlowCallback {
    @Inject
    lateinit var userAgentProvider: UserAgentProvider

    @Inject
    lateinit var dispatchers: DispatcherProvider

    @Inject
    lateinit var credentialAutofillDialogFactory: CredentialAutofillDialogFactory

    @Inject
    lateinit var googleImporterScriptLoader: GoogleImporterScriptLoader

    @Inject
    lateinit var importBookmarkConfig: BookmarkImportConfigStore

    @Inject
    lateinit var viewModelFactory: FragmentViewModelFactory

    @Inject
    lateinit var browserAutofill: BrowserAutofill

    @Inject
    lateinit var autofillFragmentResultListeners: PluginPoint<AutofillFragmentResultsPlugin>

    @Inject
    lateinit var browserAutofillConfigurator: InternalBrowserAutofillConfigurator

    @Inject
    lateinit var autofillFeature: AutofillFeature

    private var binding: FragmentImportGoogleBookmarksWebflowBinding? = null
    private var cancellationDialog: DaxAlertDialog? = null
    private var webFlowIsEnding = false

    private val viewModel by lazy {
        ViewModelProvider(requireActivity(), viewModelFactory)[ImportGoogleBookmarksWebFlowViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        binding = FragmentImportGoogleBookmarksWebflowBinding.inflate(inflater, container, false)
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
        observeCommands()

        lifecycleScope.launch {
            viewModel.loadInitialWebpage()
        }
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
                    is UserCancelledImportFlow -> exitFlowAsCancellation(viewState.stage)
                    is UserFinishedCannotImport -> exitFlowAsError(viewState.reason)
                    is ShowError -> exitFlowAsError(viewState.reason)
                    is LoadingWebPage -> loadFirstWebpage(viewState.url)
                    is NavigatingBack -> binding?.webView?.goBack()
                    is Initializing -> {}
                    is ShowWebPage -> hideImportProgressDialog()
                    is HideWebPage -> showImportProgressDialog()
                }
            }.launchIn(lifecycleScope)
    }

    private fun observeCommands() {
        viewModel.commands
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { command ->
                logcat { "Received command: ${command::class.simpleName}" }
                when (command) {
                    is InjectCredentialsFromReauth -> {
                        injectReauthenticationCredentials(url = command.url, username = command.username, password = command.password)
                    }
                    is NoCredentialsAvailable -> {
                        // Inject null to indicate no credentials available
                        browserAutofill.injectCredentials(null)
                    }
                    is PromptUserToSelectFromStoredCredentials -> showCredentialChooserDialog(
                        command.originalUrl,
                        command.credentials,
                        command.triggerType,
                    )
                    is ExitFlowWithSuccess -> exitFlowAsSuccess(command.importedCount)
                    is ExitFlowAsFailure -> exitFlowAsError(command.reason)
                    is PromptUserToConfirmFlowCancellation -> askUserToConfirmCancellation()
                }
            }.launchIn(lifecycleScope)
    }

    private suspend fun injectReauthenticationCredentials(
        url: String?,
        username: String?,
        password: String?,
    ) {
        withContext(dispatchers.main()) {
            binding?.webView?.let {
                if (it.url != url) {
                    logcat(WARN) { "WebView url has changed since autofill request; bailing" }
                    return@withContext
                }

                val credentials = LoginCredentials(
                    domain = url,
                    username = username,
                    password = password,
                )

                logcat { "Injecting re-authentication credentials" }
                browserAutofill.injectCredentials(credentials)
            }
        }
    }

    private fun configureWebView() {
        binding?.webView?.let { webView ->
            webView.webViewClient = ImportGoogleBookmarksWebFlowWebViewClient(this)
            configureWebViewSettings(webView)
            configureDownloadInterceptor(webView)
            configureAutofill(webView)

            lifecycleScope.launch {
                configureBookmarkImportJavascript(webView)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebViewSettings(webView: WebView) {
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
            setSupportZoom(true)
        }
    }

    private fun configureDownloadInterceptor(webView: WebView) {
        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            logcat { "Download intercepted: $url, mimeType: $mimeType, contentDisposition: $contentDisposition" }

            val folderName = context?.getString(R.string.importBookmarksFromGoogleChromeFolderName) ?: return@setDownloadListener
            viewModel.onDownloadDetected(
                url = url,
                userAgent = userAgent,
                contentDisposition = contentDisposition,
                mimeType = mimeType,
                folderName = folderName,
            )
        }
    }

    private fun configureAutofill(it: WebView) {
        lifecycleScope.launch {
            browserAutofill.addJsInterface(
                it,
                this@ImportGoogleBookmarksWebFlowFragment,
                this@ImportGoogleBookmarksWebFlowFragment,
                this@ImportGoogleBookmarksWebFlowFragment,
                CUSTOM_FLOW_TAB_ID,
            )
        }

        autofillFragmentResultListeners.getPlugins().forEach { plugin ->
            setFragmentResultListener(plugin.resultKey(CUSTOM_FLOW_TAB_ID)) { _, result ->
                context?.let { ctx ->
                    lifecycleScope.launch {
                        plugin.processResult(
                            result = result,
                            context = ctx,
                            tabId = CUSTOM_FLOW_TAB_ID,
                            fragment = this@ImportGoogleBookmarksWebFlowFragment,
                            autofillCallback = this@ImportGoogleBookmarksWebFlowFragment,
                            webView = binding?.webView,
                        )
                    }
                }
            }
        }
    }

    @SuppressLint("RequiresFeature", "AddDocumentStartJavaScriptUsage", "AddWebMessageListenerUsage")
    private suspend fun configureBookmarkImportJavascript(webView: WebView) {
        if (importBookmarkConfig.getConfig().canInjectJavascript) {
            val script = googleImporterScriptLoader.getScriptForBookmarkImport()
            WebViewCompat.addDocumentStartJavaScript(webView, script, setOf("*"))
        } else {
            logcat(WARN) { "Bookmark-import: Not able to inject bookmark import JavaScript" }
        }

        val canAddMessageListener = withContext(dispatchers.io()) {
            autofillFeature.canUseWebMessageListenerDuringBookmarkImport().isEnabled()
        }

        if (canAddMessageListener) {
            WebViewCompat.addWebMessageListener(webView, "ddgBookmarkImport", setOf("*")) { _, message, sourceOrigin, _, _ ->
                if (webFlowIsEnding) {
                    logcat(WARN) { "Bookmark-import: web flow is ending, ignoring message" }
                    return@addWebMessageListener
                }

                val data = message.data ?: return@addWebMessageListener
                viewModel.onWebMessageReceived(data)
            }
        } else {
            logcat(WARN) { "Bookmark-import: Not able to add WebMessage listener for bookmark import" }
        }
    }

    private fun initialiseToolbar() {
        with(getToolbar()) {
            setNavigationOnClickListener { askUserToConfirmCancellation() }
        }
    }

    private fun askUserToConfirmCancellation() {
        context?.let {
            // dismiss any existing dialog before creating a new one
            dismissCancellationDialog()

            cancellationDialog = TextAlertDialogBuilder(it)
                .setTitle(R.string.importBookmarksFromGoogleCancelConfirmationDialogTitle)
                .setMessage(R.string.importBookmarksFromGoogleCancelConfirmationDialogMessage)
                .setPositiveButton(R.string.importBookmarksFromGoogleCancelConfirmationDialogCancelImport, DESTRUCTIVE)
                .setNegativeButton(R.string.importBookmarksFromGoogleCancelConfirmationDialogContinue, GHOST_ALT)
                .setCancellable(true)
                .addEventListener(
                    object : TextAlertDialogBuilder.EventListener() {
                        override fun onPositiveButtonClicked() {
                            cancellationDialog = null
                            viewModel.onCloseButtonPressed()
                        }
                    },
                ).build().also { dialog -> dialog.show() }
        }
    }

    private fun dismissCancellationDialog() {
        cancellationDialog?.dismiss()
        cancellationDialog = null
    }

    private fun getToolbar() = (activity as ImportGoogleBookmarksWebFlowActivity).binding.includeToolbar.toolbar

    override fun onPageStarted(url: String?) {
        if (webFlowIsEnding) {
            return
        }

        viewModel.onPageStarted(url)
        lifecycleScope.launch(dispatchers.main()) {
            binding?.let {
                val reauthDetails = url?.let { viewModel.getReauthData(url) } ?: ReAuthenticationDetails()
                browserAutofillConfigurator.configureAutofillForCurrentPage(it.webView, url, reauthDetails)
            }
        }
    }

    override fun onFatalWebViewError() {
        logcat(WARN) { "Bookmark-import: Fatal WebView error received" }
        exitFlowAsError(WebViewError)
    }

    private fun configureBackButtonHandler() {
        val onBackPressedCallback =
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val canGoBack = binding?.webView?.canGoBack() ?: false
                    viewModel.onBackButtonPressed(canGoBack)
                }
            }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, onBackPressedCallback)
    }

    private fun exitFlowAsSuccess(bookmarkCount: Int) {
        logcat { "Bookmark-import: Reporting import success with bookmarkCount: $bookmarkCount" }
        onWebFlowEnding()

        lifecycleScope.launch {
            lifecycle.withStarted {
                dismissCancellationDialog()
                val result = Bundle().apply {
                    putParcelable(ImportGoogleBookmarkResult.RESULT_KEY_DETAILS, ImportGoogleBookmarkResult.Success(bookmarkCount))
                }
                setFragmentResult(ImportGoogleBookmarkResult.RESULT_KEY, result)
            }
        }
    }

    private fun exitFlowAsCancellation(stage: String) {
        logcat { "Bookmark-import: Flow cancelled at stage: $stage" }
        onWebFlowEnding()

        lifecycleScope.launch {
            lifecycle.withStarted {
                dismissCancellationDialog()

                val result =
                    Bundle().apply {
                        putParcelable(ImportGoogleBookmarkResult.Companion.RESULT_KEY_DETAILS, ImportGoogleBookmarkResult.UserCancelled(stage))
                    }
                setFragmentResult(ImportGoogleBookmarkResult.Companion.RESULT_KEY, result)
            }
        }
    }

    private fun exitFlowAsError(reason: UserCannotImportReason) {
        logcat { "Bookmark-import: Flow error at stage: ${reason.mapToStage()}" }
        onWebFlowEnding()

        lifecycleScope.launch {
            lifecycle.withStarted {
                dismissCancellationDialog()

                val result = Bundle().apply {
                    putParcelable(ImportGoogleBookmarkResult.Companion.RESULT_KEY_DETAILS, ImportGoogleBookmarkResult.Error(reason))
                }
                setFragmentResult(ImportGoogleBookmarkResult.Companion.RESULT_KEY, result)
            }
        }
    }

    /**
     * Does a best-effort to attempt to stop the web flow from any further processing.
     */
    private fun onWebFlowEnding() {
        webFlowIsEnding = true
        binding?.webView?.run {
            stopLoading()
            loadUrl("about:blank")
        }
    }

    private suspend fun showCredentialChooserDialog(
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

    override suspend fun onCredentialsAvailableToInject(
        originalUrl: String,
        credentials: List<LoginCredentials>,
        triggerType: LoginTriggerType,
    ) {
        viewModel.onStoredCredentialsAvailable(originalUrl, credentials, triggerType, scenarioAllowsReAuthentication = false)
    }

    override suspend fun onCredentialsAvailableToInjectWithReauth(
        originalUrl: String,
        credentials: List<LoginCredentials>,
        triggerType: LoginTriggerType,
        requestSubType: SupportedAutofillInputSubType,
    ) {
        val reauthAllowed = requestSubType == PASSWORD && triggerType == AUTOPROMPT
        viewModel.onStoredCredentialsAvailable(originalUrl, credentials, triggerType, reauthAllowed)
    }

    override fun noCredentialsAvailable(originalUrl: String) {
        viewModel.onNoStoredCredentialsAvailable(originalUrl)
    }

    override suspend fun promptUserToImportPassword(originalUrl: String) {
        logcat { "Autofill-import: we don't prompt the user to import in this flow" }
        viewModel.onNoStoredCredentialsAvailable(originalUrl)
    }

    override suspend fun onCredentialsAvailableToSave(
        currentUrl: String,
        credentials: LoginCredentials,
    ) {
        viewModel.onCredentialsAvailableToSave(currentUrl, credentials)
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
        viewModel.onCredentialsAutofilled(originalUrl, selectedCredentials.password)
    }

    override fun onNoCredentialsChosenForAutofill(originalUrl: String) {
        if (binding?.webView?.url != originalUrl) {
            logcat(WARN) { "WebView url has changed since autofill request; bailing" }
            return
        }
        browserAutofill.injectCredentials(null)
    }

    override suspend fun onGeneratedPasswordAvailableToUse(
        originalUrl: String,
        username: String?,
        generatedPassword: String,
    ) {
        // no-op, password generation not used in this flow
    }

    override fun onCredentialsSaved(savedCredentials: LoginCredentials) {
        // no-op
    }

    private fun showImportProgressDialog() {
        logcat { "Bookmark-import: Notifying activity to show progress" }
        (activity as? WebViewVisibilityListener)?.showLoadingState()
    }

    private fun hideImportProgressDialog() {
        logcat { "Bookmark-import: Notifying activity to hide progress" }
        (activity as? WebViewVisibilityListener)?.hideLoadingState()
    }

    override fun onDestroyView() {
        dismissCancellationDialog()
        binding = null
        super.onDestroyView()
    }

    interface WebViewVisibilityListener {
        fun showLoadingState()
        fun hideLoadingState()
    }

    companion object {
        private const val CUSTOM_FLOW_TAB_ID = "bookmark-import-webflow"
        private const val SELECT_CREDENTIALS_FRAGMENT_TAG = "autofillSelectCredentialsDialog"
    }
}

private fun UserCannotImportReason.mapToStage(): String =
    when (this) {
        is DownloadError -> "zip-download-error"
        is ErrorParsingBookmarks -> "zip-parse-error"
        is Unknown -> "import-error-unknown"
        is WebViewError -> "webview-error"
        is WebAutomationError -> "web-automation-step-failure-${this.step}"
    }
