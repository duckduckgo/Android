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

package com.duckduckgo.duckchat.impl.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.os.Message
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.annotation.AnyThread
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.core.view.updatePadding
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.app.tabs.BrowserNav
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.makeSnackbarWithNoBottomInset
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.FragmentViewModelFactory
import com.duckduckgo.common.utils.extensions.hideKeyboard
import com.duckduckgo.downloads.api.DOWNLOAD_SNACKBAR_DELAY
import com.duckduckgo.downloads.api.DOWNLOAD_SNACKBAR_LENGTH
import com.duckduckgo.downloads.api.DownloadCommand
import com.duckduckgo.downloads.api.DownloadConfirmationDialogListener
import com.duckduckgo.downloads.api.DownloadStateListener
import com.duckduckgo.downloads.api.DownloadsFileActions
import com.duckduckgo.downloads.api.FileDownloader
import com.duckduckgo.downloads.api.FileDownloader.PendingFileDownload
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.databinding.BottomSheetDuckAiContextualBinding
import com.duckduckgo.duckchat.impl.feature.AIChatDownloadFeature
import com.duckduckgo.duckchat.impl.helper.DuckChatJSHelper
import com.duckduckgo.duckchat.impl.helper.RealDuckChatJSHelper.Companion.DUCK_CHAT_FEATURE_NAME
import com.duckduckgo.duckchat.impl.ui.DuckChatWebViewFragment.Companion.KEY_DUCK_AI_URL
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.duckduckgo.js.messaging.api.JsMessaging
import com.duckduckgo.js.messaging.api.SubscriptionEventData
import com.duckduckgo.subscriptions.api.SUBSCRIPTIONS_FEATURE_NAME
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.logcat
import org.json.JSONObject
import java.io.File

class DuckChatContextualBottomSheet(
    private val viewModelFactory: FragmentViewModelFactory,
    private val webViewClient: DuckChatWebViewClient,
    private val contentScopeScripts: JsMessaging,
    private val duckChatJSHelper: DuckChatJSHelper,
    private val subscriptionsHandler: SubscriptionsHandler,
    private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val browserNav: BrowserNav,
    private val appBuildConfig: AppBuildConfig,
    private val fileDownloader: FileDownloader,
    private val downloadCallback: DownloadStateListener,
    private val downloadsFileActions: DownloadsFileActions,
    private val aiChatDownloadFeature: AIChatDownloadFeature,
    private val duckChat: DuckChat,
) : BottomSheetDialogFragment(), DownloadConfirmationDialogListener {

    private var _binding: BottomSheetDuckAiContextualBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DuckChatWebViewViewModel by lazy {
        ViewModelProvider(this, viewModelFactory)[DuckChatWebViewViewModel::class.java]
    }

    private var pendingFileDownload: PendingFileDownload? = null
    private val downloadMessagesJob = ConflatedJob()
    private var isExpanded = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val bottomSheetDialog = BottomSheetDialog(requireContext(), R.style.DuckChatBottomSheetDialogTheme)

        bottomSheetDialog.window?.let {
            ViewCompat.setOnApplyWindowInsetsListener(it.decorView) { view, insets ->
                if (!isExpanded) {
                    val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
                    val systemBottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
                    val extraMargin = (imeBottom - systemBottom).coerceAtLeast(0)

                    view.updatePadding(bottom = extraMargin)
                }
                insets
            }
            ViewCompat.requestApplyInsets(it.decorView)
        }

        bottomSheetDialog.let {
            it.behavior.addBottomSheetCallback(
                object : BottomSheetBehavior.BottomSheetCallback() {
                    override fun onStateChanged(
                        bottomSheet: View,
                        newState: Int,
                    ) {
                        if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                            it.behavior.skipCollapsed = true
                            it.behavior.isDraggable = true
                            isExpanded = true
                            binding.simpleWebview.show()
                        }
                    }

                    override fun onSlide(
                        bottomSheet: View,
                        slideOffset: Float,
                    ) {
                    }
                },
            )
        }
        return bottomSheetDialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = BottomSheetDuckAiContextualBinding.inflate(inflater, container, false)
        configureViews(binding)
        return binding.root
    }

    private fun configureViews(binding: BottomSheetDuckAiContextualBinding) {
        val bottomSheetDialog = dialog as? BottomSheetDialog
        bottomSheetDialog?.let {
            binding.root.doOnLayout {
                val peekHeight = calculatePeekHeight(binding)
                if (peekHeight > 0) {
                    bottomSheetDialog.behavior.peekHeight = peekHeight
                    bottomSheetDialog.behavior.state == BottomSheetBehavior.STATE_COLLAPSED
                }
            }
        }
    }

    private fun configureDialogButtons(binding: BottomSheetDuckAiContextualBinding) {
        binding.contextualClose.setOnClickListener {
            dismiss()
        }
        binding.actionSend.setOnClickListener { showDuckAi() }
    }

    private fun showDuckAi() {
        val prompt = binding.inputField.text.toString()
        if (prompt.isNotEmpty()) {
            val url = duckChat.getDuckChatUrl(prompt, true)
            binding.simpleWebview.loadUrl(url)
        }

        hideKeyboard(binding.inputField)
        binding.inputModeWidgetCard.gone()
        binding.contextualModePrompts.gone()
        binding.contextualWebViewContainer.show()
        val bottomSheetDialog = dialog as? BottomSheetDialog
        bottomSheetDialog?.let {
            bottomSheetDialog.window?.decorView?.updatePadding(bottom = 0)
            binding.root.doOnLayout {
                bottomSheetDialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        configureDialogButtons(binding)

        val url = arguments?.getString(KEY_DUCK_AI_URL) ?: "https://duckduckgo.com/?q=DuckDuckGo+AI+Chat&ia=chat&duckai=5"

        binding.simpleWebview.let {
            it.webViewClient = webViewClient
            it.webChromeClient = object : WebChromeClient() {
                override fun onCreateWindow(
                    view: WebView?,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: Message?,
                ): Boolean {
                    view?.requestFocusNodeHref(resultMsg)
                    val newWindowUrl = resultMsg?.data?.getString("url")
                    if (newWindowUrl != null) {
                        if (viewModel.handleOnSameWebView(newWindowUrl)) {
                            binding.simpleWebview.loadUrl(newWindowUrl)
                        } else {
                            startActivity(browserNav.openInNewTab(requireContext(), newWindowUrl))
                        }
                        return true
                    }
                    return false
                }
            }

            it.settings.apply {
                userAgentString = CUSTOM_UA
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

            it.setDownloadListener { url, _, contentDisposition, mimeType, _ ->
                appCoroutineScope.launch(dispatcherProvider.io()) {
                    if (aiChatDownloadFeature.self().isEnabled()) {
                        requestFileDownload(url, contentDisposition, mimeType)
                    }
                }
            }

            contentScopeScripts.register(
                it,
                object : JsMessageCallback() {
                    override fun process(
                        featureName: String,
                        method: String,
                        id: String?,
                        data: JSONObject?,
                    ) {
                        logcat { "Duck.ai: process $featureName $method $id $data" }
                        when (featureName) {
                            DUCK_CHAT_FEATURE_NAME -> {
                                appCoroutineScope.launch(dispatcherProvider.io()) {
                                    duckChatJSHelper.processJsCallbackMessage(featureName, method, id, data)?.let { response ->
                                        withContext(dispatcherProvider.main()) {
                                            contentScopeScripts.onResponse(response)
                                        }
                                    }
                                }
                            }

                            SUBSCRIPTIONS_FEATURE_NAME -> {
                                subscriptionsHandler.handleSubscriptionsFeature(
                                    featureName,
                                    method,
                                    id,
                                    data,
                                    requireActivity(),
                                    appCoroutineScope,
                                    contentScopeScripts,
                                )
                            }

                            else -> {}
                        }
                    }
                },
            )
        }

        url.let {
            binding.simpleWebview.loadUrl(it)
        }

        observeViewModel()
        launchDownloadMessagesJob()
    }

    private fun observeViewModel() {
        viewModel.commands
            .onEach { command ->
                when (command) {
                    is DuckChatWebViewViewModel.Command.SendSubscriptionAuthUpdateEvent -> {
                        val authUpdateEvent = SubscriptionEventData(
                            featureName = SUBSCRIPTIONS_FEATURE_NAME,
                            subscriptionName = "authUpdate",
                            params = JSONObject(),
                        )
                        contentScopeScripts.sendSubscriptionEvent(authUpdateEvent)
                    }
                }
            }.launchIn(lifecycleScope)
    }

    private fun requestFileDownload(
        url: String,
        contentDisposition: String?,
        mimeType: String,
    ) {
        pendingFileDownload = PendingFileDownload(
            url = url,
            contentDisposition = contentDisposition,
            mimeType = mimeType,
            subfolder = Environment.DIRECTORY_DOWNLOADS,
            fileName = "duck.ai_${System.currentTimeMillis()}",
        )

        if (hasWriteStoragePermission()) {
            downloadFile()
        } else {
            requestWriteStoragePermission()
        }
    }

    private fun minSdk30(): Boolean {
        return appBuildConfig.sdkInt >= 30
    }

    @Suppress("NewApi")
    private fun hasWriteStoragePermission(): Boolean {
        return minSdk30() ||
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestWriteStoragePermission() {
        requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE)
    }

    @AnyThread
    private fun downloadFile() {
        val pendingDownload = pendingFileDownload ?: return

        pendingFileDownload = null

        continueDownload(pendingDownload)
    }

    override fun continueDownload(pendingFileDownload: PendingFileDownload) {
        fileDownloader.enqueueDownload(pendingFileDownload)
    }

    override fun cancelDownload() {
        // NOOP
    }

    private fun calculatePeekHeight(binding: BottomSheetDuckAiContextualBinding): Int {
        val headerHeight = viewHeightWithMargins(binding.contextualModeButtons)
        val promptsHeight = viewHeightWithMargins(binding.contextualModePrompts)
        val inputHeight = viewHeightWithMargins(binding.inputModeWidgetCard)
        val rootPadding = binding.root.paddingTop + binding.root.paddingBottom
        return (headerHeight + promptsHeight + inputHeight + rootPadding).coerceAtLeast(0)
    }

    private fun viewHeightWithMargins(view: View): Int {
        val layoutParams = view.layoutParams as? ViewGroup.MarginLayoutParams
        val verticalMargins = (layoutParams?.topMargin ?: 0) + (layoutParams?.bottomMargin ?: 0)
        val baseHeight = if (view.height > 0) view.height else view.measuredHeight
        return baseHeight + verticalMargins
    }

    private fun launchDownloadMessagesJob() {
        downloadMessagesJob += lifecycleScope.launch {
            downloadCallback.commands().cancellable().collect {
                processFileDownloadedCommand(it)
            }
        }
    }

    private fun processFileDownloadedCommand(command: DownloadCommand) {
        when (command) {
            is DownloadCommand.ShowDownloadStartedMessage -> downloadStarted(command)
            is DownloadCommand.ShowDownloadFailedMessage -> downloadFailed(command)
            is DownloadCommand.ShowDownloadSuccessMessage -> downloadSucceeded(command)
        }
    }

    @SuppressLint("WrongConstant")
    private fun downloadStarted(command: DownloadCommand.ShowDownloadStartedMessage) {
        binding.simpleWebview.makeSnackbarWithNoBottomInset(getString(command.messageId, command.fileName), DOWNLOAD_SNACKBAR_LENGTH)?.show()
    }

    private fun downloadFailed(command: DownloadCommand.ShowDownloadFailedMessage) {
        val downloadFailedSnackbar = binding.simpleWebview.makeSnackbarWithNoBottomInset(getString(command.messageId), Snackbar.LENGTH_LONG)
        binding.simpleWebview.postDelayed({ downloadFailedSnackbar.show() }, DOWNLOAD_SNACKBAR_DELAY)
    }

    private fun downloadSucceeded(command: DownloadCommand.ShowDownloadSuccessMessage) {
        val downloadSucceededSnackbar = binding.simpleWebview.makeSnackbarWithNoBottomInset(
            getString(command.messageId, command.fileName),
            Snackbar.LENGTH_LONG,
        )
            .apply {
                this.setAction(R.string.duck_chat_download_finished_action_name) {
                    val result = downloadsFileActions.openFile(context, File(command.filePath))
                    if (!result) {
                        view.makeSnackbarWithNoBottomInset(getString(R.string.duck_chat_cannot_open_file_error_message), Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        binding.simpleWebview.postDelayed({ downloadSucceededSnackbar.show() }, DOWNLOAD_SNACKBAR_DELAY)
    }

    companion object {
        const val TAG = "DuckChatBottomSheet"
        private const val CUSTOM_UA =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/124.0.0.0 Mobile DuckDuckGo/5 Safari/537.36"
        private const val PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE = 200
        private const val TOP_OFFSET_DP = 140f
    }
}
