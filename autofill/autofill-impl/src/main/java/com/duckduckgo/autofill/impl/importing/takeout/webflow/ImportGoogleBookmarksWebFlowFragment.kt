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
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autofill.impl.R
import com.duckduckgo.autofill.impl.databinding.FragmentImportGoogleBookmarksWebflowBinding
import com.duckduckgo.autofill.impl.importing.takeout.zip.TakeoutBookmarkExtractor
import com.duckduckgo.autofill.impl.importing.takeout.zip.TakeoutZipDownloader
import com.duckduckgo.common.ui.DuckDuckGoFragment
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.FragmentViewModelFactory
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.savedsites.api.service.ImportSavedSitesResult
import com.duckduckgo.savedsites.api.service.SavedSitesImporter
import com.duckduckgo.user.agent.api.UserAgentProvider
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import logcat.logcat

@InjectWith(FragmentScope::class)
class ImportGoogleBookmarksWebFlowFragment :
    DuckDuckGoFragment(R.layout.fragment_import_google_bookmarks_webflow),
    ImportGoogleBookmarksWebFlowWebViewClient.NewPageCallback {

    @Inject
    lateinit var userAgentProvider: UserAgentProvider

    @Inject
    lateinit var dispatchers: DispatcherProvider

    @Inject
    lateinit var pixel: Pixel

    @Inject
    lateinit var viewModelFactory: FragmentViewModelFactory

    @Inject
    lateinit var takeoutZipDownloader: TakeoutZipDownloader

    @Inject
    lateinit var takeoutZipTakeoutBookmarkExtractor: TakeoutBookmarkExtractor

    @Inject
    lateinit var savedSitesImporter: SavedSitesImporter

    private var binding: FragmentImportGoogleBookmarksWebflowBinding? = null

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
        lifecycleScope.launch {
            viewModel.loadInitialWebpage()
        }
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
                    is ImportGoogleBookmarksWebFlowViewModel.ViewState.UserFinishedImportFlow -> exitFlowAsSuccess()
                    is ImportGoogleBookmarksWebFlowViewModel.ViewState.UserCancelledImportFlow -> exitFlowAsCancellation(viewState.stage)
                    is ImportGoogleBookmarksWebFlowViewModel.ViewState.UserFinishedCannotImport -> exitFlowAsError(viewState.reason)
                    is ImportGoogleBookmarksWebFlowViewModel.ViewState.LoadingWebPage -> loadFirstWebpage(viewState.url)
                    is ImportGoogleBookmarksWebFlowViewModel.ViewState.NavigatingBack -> binding?.webView?.goBack()
                    is ImportGoogleBookmarksWebFlowViewModel.ViewState.Initializing -> {
                        // Handle initialization
                    }
                    is ImportGoogleBookmarksWebFlowViewModel.ViewState.ShowWebPage -> {
                        // Web page is shown, JavaScript can be injected
                    }
                    is ImportGoogleBookmarksWebFlowViewModel.ViewState.ShowError -> exitFlowAsError(viewState.reason)
                }
            }.launchIn(lifecycleScope)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        binding?.webView?.let { webView ->
            webView.webViewClient = ImportGoogleBookmarksWebFlowWebViewClient(this)

            webView.settings.apply {
                userAgentString = userAgentProvider.userAgent()
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                builtInZoomControls = true
                displayZoomControls = false
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                setSupportMultipleWindows(true)
                setSupportZoom(true)
            }

            // Enable cookies for authentication
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(webView, true)

            configureDownloadInterceptor(webView)
            configureAutofill(webView)

            lifecycleScope.launch {
                // bookmarkBlobConsumer.configureWebViewForBlobDownload(webView, this@ImportGoogleBookmarksWebFlowFragment)
                configureBookmarkImportJavascript(webView)
            }
        }
    }

    private fun configureDownloadInterceptor(webView: WebView) {
        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
            logcat { "Download intercepted: $url, mimeType: $mimeType, contentDisposition: $contentDisposition" }

            // Check if this looks like a Google Takeout bookmark export
            when {
                mimeType == "application/zip" || mimeType == "application/octet-stream" ||
                    url.contains(".zip") ||
                    url.contains("takeout.google.com") ||
                    contentDisposition?.contains("attachment") == true -> {
                    logcat { "Potential bookmark zip detected, starting download..." }
                    handleBookmarkZipDownload(url, userAgent, contentDisposition, mimeType, contentLength)
                }
                else -> {
                    logcat { "Non-zip download detected, ignoring. URL: $url" }
                }
            }
        }
    }

    private fun handleBookmarkZipDownload(
        url: String,
        userAgent: String,
        contentDisposition: String?,
        mimeType: String,
        contentLength: Long,
    ) {
        lifecycleScope.launch {
            try {
                // Get the current page URL as referrer for proper authentication context
                val currentUrl = binding?.webView?.url
                logcat { "Downloading with context - Current page: $currentUrl, Download URL: $url" }

                val zipData = takeoutZipDownloader.downloadZip(url, userAgent, currentUrl)
                handleDownloadedZipData(zipData)
            } catch (e: Exception) {
                logcat { "Bookmark zip download failed: ${e.message}" }
                withContext(dispatchers.main()) {
                    viewModel.onBookmarkError()
                }
            }
        }
    }

    private fun handleDownloadedZipData(zipData: ByteArray) {
        lifecycleScope.launch(dispatchers.io()) {
            var tempZipFile: File? = null
            try {
                logcat { "cdr Processing ${zipData.size} bytes of bookmark zip data" }

                // Create temp zip file and get Uri
                tempZipFile = File(requireContext().cacheDir, "temp_takeout_${System.currentTimeMillis()}.zip")
                tempZipFile.writeBytes(zipData)

                val zipUri = Uri.fromFile(tempZipFile)
                logcat {
                    "cdr created temp zip file: ${tempZipFile.path}, zipUri: $zipUri, " +
                        "size: ${tempZipFile.length()}, exists: ${tempZipFile.exists()}"
                }

                // Extract HTML content from zip
                val extractionResult = takeoutZipTakeoutBookmarkExtractor.extractBookmarksHtml(zipUri)
                logcat { "cdr Bookmark extraction result: $extractionResult" }

                when (extractionResult) {
                    is TakeoutBookmarkExtractor.ExtractionResult.Success -> {
                        val tempHtmlFile = File(requireContext().cacheDir, "extracted_bookmarks_${System.currentTimeMillis()}.html")

                        try {
                            tempHtmlFile.writeText(extractionResult.bookmarkHtmlContent)
                            val tempHtmlUri = Uri.fromFile(tempHtmlFile)

                            logcat { "cdr Temp bookmark HTML file created: ${tempHtmlFile.path}, size: ${tempHtmlFile.length()}" }

                            // Import using the temp file URI
                            val importResult = savedSitesImporter.import(tempHtmlUri)
                            when (importResult) {
                                is ImportSavedSitesResult.Success -> {
                                    logcat { "cdr Successfully imported ${importResult.savedSites.size} bookmarks" }
                                    withContext(dispatchers.main()) {
                                        viewModel.onBookmarkZipAvailable(zipData)
                                        exitFlowAsSuccess()
                                    }
                                }
                                is ImportSavedSitesResult.Error -> {
                                    logcat(LogPriority.WARN) { "cdr Error importing bookmarks: ${importResult.exception.message}" }
                                    withContext(dispatchers.main()) {
                                        viewModel.onBookmarkError()
                                    }
                                }
                            }
                        } finally {
                            // Clean up temp HTML file
                            // tempHtmlFile.takeIf { it.exists() }?.delete()
                            logcat { "cdr Cleaned up temp bookmark HTML file: ${tempHtmlFile.path}" }
                        }
                    }
                    is TakeoutBookmarkExtractor.ExtractionResult.Error -> {
                        logcat(LogPriority.WARN) { "cdr Error extracting bookmarks: ${extractionResult.exception.message}" }
                        withContext(dispatchers.main()) {
                            viewModel.onBookmarkError()
                        }
                    }
                }
            } catch (e: Exception) {
                logcat { "cdr Error processing downloaded bookmark zip: ${e.message}" }
                withContext(dispatchers.main()) {
                    viewModel.onBookmarkError()
                }
            } finally {
                // Clean up temp zip file
                tempZipFile?.takeIf { it.exists() }?.delete()
                logcat { "Cleaned up temp zip file: ${tempZipFile?.path}" }
            }
        }
    }

    private fun configureAutofill(webView: WebView) {
        // TODO: Add autofill configuration for bookmark import if needed
    }

    @SuppressLint("RequiresFeature")
    private suspend fun configureBookmarkImportJavascript(webView: WebView) {
        // TODO: Add bookmark import config check when available
        // if (importBookmarkConfig.getConfig().canInjectJavascript) {
        //     val script = bookmarkImporterScriptLoader.getScript()
        //     WebViewCompat.addDocumentStartJavaScript(webView, script, setOf("*"))
        // }
    }

    private fun initialiseToolbar() {
        with(getToolbar()) {
            title = getString(R.string.autofillManagementImportBookmarks)
            setNavigationIconAsCross()
            setNavigationOnClickListener { viewModel.onCloseButtonPressed(binding?.webView?.url) }
        }
    }

    private fun Toolbar.setNavigationIconAsCross() {
        setNavigationIcon(com.duckduckgo.mobile.android.R.drawable.ic_close_24)
    }

    private fun getToolbar() = (activity as ImportGoogleBookmarksWebFlowActivity).binding.includeToolbar.toolbar

    // Callback for WebViewClient
    override fun onPageStarted(url: String?) {
        binding?.let {
            // TODO: Add page start handling if needed
        }
    }

    override fun onPageFinished(url: String?) {
        binding?.let {
            logcat { "onPageFinished: $url" }
            // TODO: Inject bookmark export JavaScript here when available
        }
    }

    private fun configureBackButtonHandler() {
        val onBackPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val canGoBack = binding?.webView?.canGoBack() ?: false
                viewModel.onBackButtonPressed(binding?.webView?.url, canGoBack)
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, onBackPressedCallback)
    }

    private fun exitFlowAsSuccess() {
        val result = Bundle().apply {
            putParcelable(ImportGoogleBookmarkResult.Companion.RESULT_KEY_DETAILS, ImportGoogleBookmarkResult.Success)
        }
        setFragmentResult(ImportGoogleBookmarkResult.Companion.RESULT_KEY, result)
    }

    private fun exitFlowAsCancellation(stage: String) {
        val result = Bundle().apply {
            putParcelable(ImportGoogleBookmarkResult.Companion.RESULT_KEY_DETAILS, ImportGoogleBookmarkResult.UserCancelled(stage))
        }
        setFragmentResult(ImportGoogleBookmarkResult.Companion.RESULT_KEY, result)
    }

    private fun exitFlowAsError(reason: ImportGoogleBookmarksWebFlowViewModel.UserCannotImportReason) {
        val result = Bundle().apply {
            putParcelable(ImportGoogleBookmarkResult.Companion.RESULT_KEY_DETAILS, ImportGoogleBookmarkResult.Error(reason))
        }
        setFragmentResult(ImportGoogleBookmarkResult.Companion.RESULT_KEY, result)
    }
}
