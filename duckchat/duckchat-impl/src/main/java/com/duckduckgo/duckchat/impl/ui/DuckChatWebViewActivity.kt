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
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.os.Message
import android.util.Log
import android.view.MenuItem
import android.view.MotionEvent
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.annotation.AnyThread
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.tabs.BrowserNav
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.experiments.visual.store.VisualDesignExperimentDataStore
import com.duckduckgo.common.ui.view.dialog.ActionBottomSheetDialog
import com.duckduckgo.common.ui.view.makeSnackbarWithNoBottomInset
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.extensions.hideKeyboard
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.downloads.api.DOWNLOAD_SNACKBAR_DELAY
import com.duckduckgo.downloads.api.DOWNLOAD_SNACKBAR_LENGTH
import com.duckduckgo.downloads.api.DownloadCommand
import com.duckduckgo.downloads.api.DownloadConfirmationDialogListener
import com.duckduckgo.downloads.api.DownloadStateListener
import com.duckduckgo.downloads.api.DownloadsFileActions
import com.duckduckgo.downloads.api.FileDownloader
import com.duckduckgo.downloads.api.FileDownloader.PendingFileDownload
import com.duckduckgo.duckchat.impl.ChatState.BLOCKED
import com.duckduckgo.duckchat.impl.ChatState.ERROR
import com.duckduckgo.duckchat.impl.ChatState.HIDE
import com.duckduckgo.duckchat.impl.ChatState.LOADING
import com.duckduckgo.duckchat.impl.ChatState.READY
import com.duckduckgo.duckchat.impl.ChatState.SHOW
import com.duckduckgo.duckchat.impl.ChatState.START_STREAM_NEW_PROMPT
import com.duckduckgo.duckchat.impl.ChatState.STREAMING
import com.duckduckgo.duckchat.impl.DuckChatInternal
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.databinding.ActivityDuckChatWebviewBinding
import com.duckduckgo.duckchat.impl.feature.AIChatDownloadFeature
import com.duckduckgo.duckchat.impl.helper.DuckChatJSHelper
import com.duckduckgo.duckchat.impl.helper.RealDuckChatJSHelper.Companion.DUCK_CHAT_FEATURE_NAME
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.duckduckgo.js.messaging.api.JsMessaging
import com.duckduckgo.js.messaging.api.SubscriptionEventData
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.navigation.api.getActivityParams
import com.google.android.material.snackbar.Snackbar
import java.io.File
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal data class DuckChatWebViewActivityWithParams(
    val url: String,
) : GlobalActivityStarter.ActivityParams

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(DuckChatWebViewActivityWithParams::class)
class DuckChatWebViewActivity : DuckDuckGoActivity(), DownloadConfirmationDialogListener {

    @Inject
    lateinit var webViewClient: DuckChatWebViewClient

    @Inject
    @Named("ContentScopeScripts")
    lateinit var contentScopeScripts: JsMessaging

    @Inject
    lateinit var duckChatJSHelper: DuckChatJSHelper

    @Inject
    @AppCoroutineScope
    lateinit var appCoroutineScope: CoroutineScope

    @Inject
    lateinit var dispatcherProvider: DispatcherProvider

    @Inject
    lateinit var browserNav: BrowserNav

    @Inject
    lateinit var appBuildConfig: AppBuildConfig

    @Inject
    lateinit var fileDownloader: FileDownloader

    @Inject
    lateinit var downloadCallback: DownloadStateListener

    @Inject
    lateinit var downloadsFileActions: DownloadsFileActions

    @Inject
    lateinit var duckChat: DuckChatInternal

    @Inject
    lateinit var aiChatDownloadFeature: AIChatDownloadFeature

    @Inject
    lateinit var experimentDataStore: VisualDesignExperimentDataStore

    @Inject
    lateinit var appBrowserNav: BrowserNav

    private val binding: ActivityDuckChatWebviewBinding by viewBinding()

    private var pendingFileDownload: PendingFileDownload? = null
    private val downloadMessagesJob = ConflatedJob()

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        duckChat.observeCloseEvent(this) {
            finish()
        }

        setContentView(binding.root)
        configureUI()

        val params = intent.getActivityParams(DuckChatWebViewActivityWithParams::class.java)
        val url = params?.url

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                duckChat.chatState.collect { state ->
                    Log.d("DuckChatWebViewActivity", "ChatState changed to: $state")

                    when (state) {
                        START_STREAM_NEW_PROMPT -> binding.duckChatOmnibar.hideStopButton()
                        LOADING -> binding.duckChatOmnibar.showStopButton()
                        STREAMING -> binding.duckChatOmnibar.showStopButton()
                        ERROR -> binding.duckChatOmnibar.hideStopButton()
                        READY -> {
                            binding.duckChatOmnibar.hideStopButton()
                            binding.duckChatOmnibar.isVisible = true
                        }
                        BLOCKED -> binding.duckChatOmnibar.hideStopButton()
                        HIDE -> binding.duckChatOmnibar.isVisible = false
                        SHOW -> binding.duckChatOmnibar.isVisible = true
                    }
                }
            }
        }

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
                        startActivity(browserNav.openInNewTab(this@DuckChatWebViewActivity, newWindowUrl))
                        return true
                    }
                    return false
                }
            }
            binding.simpleWebview.setOnTouchListener { v, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    hideKeyboard(binding.duckChatOmnibar.duckChatInput)
                }
                false
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
                            else -> {}
                        }
                    }
                },
            )
        }

        url?.let {
            binding.simpleWebview.loadUrl(it)
        }
    }

    private fun configureUI() {
        binding.duckChatOmnibar.isVisible = experimentDataStore.isDuckAIPoCEnabled.value && experimentDataStore.isExperimentEnabled.value
        binding.duckChatOmnibar.selectTab(1)

        binding.duckChatOmnibar.apply {
            onFire = {
                ActionBottomSheetDialog.Builder(this@DuckChatWebViewActivity)
                    .setTitle(context.getString(R.string.duck_chat_delete_this_chat))
                    .setPrimaryItem(context.getString(R.string.duck_chat_delete_chat))
                    .setSecondaryItem(context.getString(R.string.duck_chat_cancel))
                    .addEventListener(
                        object : ActionBottomSheetDialog.EventListener() {
                            override fun onPrimaryItemClicked() {
                                contentScopeScripts.sendSubscriptionEvent(
                                    SubscriptionEventData(
                                        featureName = DUCK_CHAT_FEATURE_NAME,
                                        subscriptionName = "submitFireButtonAction",
                                        params = JSONObject("{}"),
                                    ),
                                )
                            }
                        },
                    )
                    .show()
            }
            onNewChat = {
                contentScopeScripts.sendSubscriptionEvent(
                    SubscriptionEventData(
                        featureName = DUCK_CHAT_FEATURE_NAME,
                        subscriptionName = "submitNewChatAction",
                        params = JSONObject("{}"),
                    ),
                )
            }
            onSearchSent = { message ->
                context.startActivity(appBrowserNav.openInNewTab(context, message))
                finish()
            }
            onDuckChatSent = { message ->
                contentScopeScripts.sendSubscriptionEvent(
                    SubscriptionEventData(
                        featureName = DUCK_CHAT_FEATURE_NAME,
                        subscriptionName = "submitAIChatNativePrompt",
                        params = JSONObject(
                            """
                            {
                              "platform": "android",
                              "query": {
                                "prompt": "$message",
                                "autoSubmit": true
                              }
                            }
                            """,
                        ),
                    ),
                )
                hideKeyboard(duckChatInput)
            }
            onStop = {
                contentScopeScripts.sendSubscriptionEvent(
                    SubscriptionEventData(
                        featureName = DUCK_CHAT_FEATURE_NAME,
                        subscriptionName = "submitPromptInterruption",
                        params = JSONObject("{}"),
                    ),
                )
            }
            onBack = { onBackPressed() }
        }
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

    override fun continueDownload(pendingFileDownload: PendingFileDownload) {
        fileDownloader.enqueueDownload(pendingFileDownload)
    }

    override fun cancelDownload() {
        // NOOP
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
        binding.root.makeSnackbarWithNoBottomInset(getString(command.messageId, command.fileName), DOWNLOAD_SNACKBAR_LENGTH)?.show()
    }

    private fun downloadFailed(command: DownloadCommand.ShowDownloadFailedMessage) {
        val downloadFailedSnackbar = binding.root.makeSnackbarWithNoBottomInset(getString(command.messageId), Snackbar.LENGTH_LONG)
        binding.root.postDelayed({ downloadFailedSnackbar.show() }, DOWNLOAD_SNACKBAR_DELAY)
    }

    private fun downloadSucceeded(command: DownloadCommand.ShowDownloadSuccessMessage) {
        val downloadSucceededSnackbar = binding.root.makeSnackbarWithNoBottomInset(
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
        binding.root.postDelayed({ downloadSucceededSnackbar.show() }, DOWNLOAD_SNACKBAR_DELAY)
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

    @AnyThread
    private fun downloadFile() {
        val pendingDownload = pendingFileDownload ?: return

        pendingFileDownload = null

        continueDownload(pendingDownload)
    }

    private fun minSdk30(): Boolean {
        return appBuildConfig.sdkInt >= 30
    }

    @Suppress("NewApi")
    private fun hasWriteStoragePermission(): Boolean {
        return minSdk30() ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestWriteStoragePermission() {
        requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE)
    }

    override fun onResume() {
        launchDownloadMessagesJob()
        super.onResume()
    }

    override fun onDestroy() {
        downloadMessagesJob.cancel()
        super.onDestroy()
    }

    companion object {
        private const val PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE = 200
        private const val CUSTOM_UA =
            "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/124.0.0.0 Mobile DuckDuckGo/5 Safari/537.36"
    }
}
