/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.subscriptions.impl.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Message
import android.view.MenuItem
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.annotation.AnyThread
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.SpecialUrlDetector
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.dialog.TextAlertDialogBuilder
import com.duckduckgo.common.ui.view.hide
import com.duckduckgo.common.ui.view.makeSnackbarWithNoBottomInset
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.downloads.api.DOWNLOAD_SNACKBAR_DELAY
import com.duckduckgo.downloads.api.DOWNLOAD_SNACKBAR_LENGTH
import com.duckduckgo.downloads.api.DownloadCommand
import com.duckduckgo.downloads.api.DownloadConfirmation
import com.duckduckgo.downloads.api.DownloadConfirmationDialogListener
import com.duckduckgo.downloads.api.DownloadStateListener
import com.duckduckgo.downloads.api.DownloadsFileActions
import com.duckduckgo.downloads.api.FileDownloader
import com.duckduckgo.downloads.api.FileDownloader.PendingFileDownload
import com.duckduckgo.js.messaging.api.JsCallbackData
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.duckduckgo.js.messaging.api.JsMessaging
import com.duckduckgo.js.messaging.api.SubscriptionEventData
import com.duckduckgo.mobile.android.R
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.navigation.api.GlobalActivityStarter.ActivityParams
import com.duckduckgo.navigation.api.getActivityParams
import com.duckduckgo.subscriptions.api.SubscriptionScreens.SubscriptionScreenNoParams
import com.duckduckgo.subscriptions.impl.R.string
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.ACTIVATE_URL
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.BUY_URL
import com.duckduckgo.subscriptions.impl.databinding.ActivitySubscriptionsWebviewBinding
import com.duckduckgo.subscriptions.impl.pir.PirActivity.Companion.PirScreenWithEmptyParams
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixelSender
import com.duckduckgo.subscriptions.impl.ui.RestoreSubscriptionActivity.Companion.RestoreSubscriptionScreenWithParams
import com.duckduckgo.subscriptions.impl.ui.SubscriptionWebViewViewModel.Command
import com.duckduckgo.subscriptions.impl.ui.SubscriptionWebViewViewModel.Command.BackToSettings
import com.duckduckgo.subscriptions.impl.ui.SubscriptionWebViewViewModel.Command.BackToSettingsActivateSuccess
import com.duckduckgo.subscriptions.impl.ui.SubscriptionWebViewViewModel.Command.GoToITR
import com.duckduckgo.subscriptions.impl.ui.SubscriptionWebViewViewModel.Command.GoToNetP
import com.duckduckgo.subscriptions.impl.ui.SubscriptionWebViewViewModel.Command.GoToPIR
import com.duckduckgo.subscriptions.impl.ui.SubscriptionWebViewViewModel.Command.Reload
import com.duckduckgo.subscriptions.impl.ui.SubscriptionWebViewViewModel.Command.RestoreSubscription
import com.duckduckgo.subscriptions.impl.ui.SubscriptionWebViewViewModel.Command.SendJsEvent
import com.duckduckgo.subscriptions.impl.ui.SubscriptionWebViewViewModel.Command.SendResponseToJs
import com.duckduckgo.subscriptions.impl.ui.SubscriptionWebViewViewModel.Command.SubscriptionSelected
import com.duckduckgo.subscriptions.impl.ui.SubscriptionWebViewViewModel.PurchaseStateView
import com.duckduckgo.subscriptions.impl.ui.SubscriptionsWebViewActivityWithParams.ToolbarConfig
import com.duckduckgo.subscriptions.impl.ui.SubscriptionsWebViewActivityWithParams.ToolbarConfig.CustomTitle
import com.duckduckgo.subscriptions.impl.ui.SubscriptionsWebViewActivityWithParams.ToolbarConfig.DaxPrivacyPro
import com.duckduckgo.user.agent.api.UserAgentProvider
import com.google.android.material.snackbar.Snackbar
import java.io.File
import java.io.Serializable
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.json.JSONObject

data class SubscriptionsWebViewActivityWithParams(
    val url: String,
    val toolbarConfig: ToolbarConfig = DaxPrivacyPro,
    val origin: String? = null,
) : ActivityParams {

    sealed class ToolbarConfig : Serializable {
        data object DaxPrivacyPro : ToolbarConfig() {
            private fun readResolve(): Any = DaxPrivacyPro
        }

        data class CustomTitle(val title: String) : ToolbarConfig()
    }
}

@InjectWith(
    scope = ActivityScope::class,
    delayGeneration = true, // Delayed because it has a dependency on DownloadConfirmationFragment from another module
)
@ContributeToActivityStarter(SubscriptionScreenNoParams::class)
@ContributeToActivityStarter(SubscriptionsWebViewActivityWithParams::class)
class SubscriptionsWebViewActivity : DuckDuckGoActivity(), DownloadConfirmationDialogListener {

    @Inject
    @Named("Subscriptions")
    lateinit var subscriptionJsMessaging: JsMessaging

    @Inject
    @Named("Itr")
    lateinit var itrJsMessaging: JsMessaging

    @Inject
    lateinit var userAgent: UserAgentProvider

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    @Inject
    lateinit var specialUrlDetector: SpecialUrlDetector

    @Inject
    lateinit var appBuildConfig: AppBuildConfig

    @Inject
    lateinit var downloadConfirmation: DownloadConfirmation

    @Inject
    lateinit var fileDownloader: FileDownloader

    @Inject
    lateinit var downloadCallback: DownloadStateListener

    @Inject
    lateinit var downloadsFileActions: DownloadsFileActions

    @Inject
    lateinit var pixelSender: SubscriptionPixelSender

    private val viewModel: SubscriptionWebViewViewModel by bindViewModel()

    private val binding: ActivitySubscriptionsWebviewBinding by viewBinding()

    private lateinit var params: SubscriptionsWebViewActivityWithParams

    // Used to represent a file to download, but may first require permission
    private var pendingFileDownload: PendingFileDownload? = null
    private val downloadMessagesJob = ConflatedJob()
    private val toolbar
        get() = binding.includeToolbar.toolbar

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        params = intent.getActivityParams(SubscriptionsWebViewActivityWithParams::class.java)
            ?: SubscriptionsWebViewActivityWithParams(BUY_URL)

        setContentView(binding.root)
        setupInternalToolbar(toolbar)

        binding.webview.let {
            subscriptionJsMessaging.register(
                it,
                object : JsMessageCallback() {
                    override fun process(
                        featureName: String,
                        method: String,
                        id: String?,
                        data: JSONObject?,
                    ) {
                        viewModel.processJsCallbackMessage(featureName, method, id, data)
                    }
                },
            )
            itrJsMessaging.register(it, null)
            it.webChromeClient = object : WebChromeClient() {

                private var url: String? = null

                override fun onCreateWindow(
                    view: WebView?,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    message: Message,
                ): Boolean {
                    val transport = message.obj as WebView.WebViewTransport
                    transport.webView = it
                    message.sendToTarget()
                    return true
                }

                override fun onProgressChanged(
                    view: WebView?,
                    newProgress: Int,
                ) {
                    view?.url.let { currentUrl ->
                        if (currentUrl != url) {
                            url = currentUrl
                            onUrlChanged(url)
                        }
                    }
                    if (newProgress == 100) {
                        if (binding.webview.canGoBack()) {
                            toolbar.setNavigationIcon(R.drawable.ic_arrow_left_24)
                        } else {
                            toolbar.setNavigationIcon(R.drawable.ic_close_24)
                        }
                    }
                    super.onProgressChanged(view, newProgress)
                }
            }
            it.webViewClient = SubscriptionsWebViewClient(specialUrlDetector, this)
            it.settings.apply {
                userAgentString = CUSTOM_UA
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                builtInZoomControls = true
                displayZoomControls = false
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                setSupportMultipleWindows(false)
                setSupportZoom(true)
            }
            it.setDownloadListener { url, _, contentDisposition, mimeType, _ ->
                requestFileDownload(url, contentDisposition, mimeType, true)
            }
        }

        binding.webview.loadUrl(params.url)

        viewModel.start()

        viewModel.commands()
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)

        viewModel.currentPurchaseViewState.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).distinctUntilChanged().onEach {
            renderPurchaseState(it.purchaseState)
        }.launchIn(lifecycleScope)

        if (savedInstanceState == null && params.url == BUY_URL) {
            pixelSender.reportOfferScreenShown()
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
        binding.root.postDelayed({ downloadFailedSnackbar?.show() }, DOWNLOAD_SNACKBAR_DELAY)
    }

    private fun downloadSucceeded(command: DownloadCommand.ShowDownloadSuccessMessage) {
        val downloadSucceededSnackbar = binding.root.makeSnackbarWithNoBottomInset(
            getString(command.messageId, command.fileName),
            Snackbar.LENGTH_LONG,
        )
            .apply {
                this.setAction(string.downloadsDownloadFinishedActionName) {
                    val result = downloadsFileActions.openFile(context, File(command.filePath))
                    if (!result) {
                        view.makeSnackbarWithNoBottomInset(getString(string.downloadsCannotOpenFileErrorMessage), Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        binding.root.postDelayed({ downloadSucceededSnackbar.show() }, DOWNLOAD_SNACKBAR_DELAY)
    }

    private fun requestFileDownload(
        url: String,
        contentDisposition: String?,
        mimeType: String,
        requestUserConfirmation: Boolean,
    ) {
        pendingFileDownload = PendingFileDownload(
            url = url,
            contentDisposition = contentDisposition,
            mimeType = mimeType,
            subfolder = Environment.DIRECTORY_DOWNLOADS,
        )

        if (hasWriteStoragePermission()) {
            downloadFile(requestUserConfirmation && !URLUtil.isDataUrl(url))
        } else {
            requestWriteStoragePermission()
        }
    }

    @AnyThread
    private fun downloadFile(requestUserConfirmation: Boolean) {
        val pendingDownload = pendingFileDownload ?: return

        pendingFileDownload = null

        if (requestUserConfirmation) {
            requestDownloadConfirmation(pendingDownload)
        }
    }

    private fun requestDownloadConfirmation(pendingDownload: PendingFileDownload) {
        val downloadConfirmationFragment = downloadConfirmation.instance(pendingDownload)
        showDialogHidingPrevious(downloadConfirmationFragment, DOWNLOAD_CONFIRMATION_TAG)
    }

    private fun showDialogHidingPrevious(
        dialog: DialogFragment,
        tag: String,
    ) {
        // want to ensure lifecycle is at least resumed before attempting to show dialog
        lifecycleScope.launchWhenResumed {
            hideDialogWithTag(tag)
            dialog.show(supportFragmentManager, tag)
        }
    }

    private fun hideDialogWithTag(tag: String) {
        supportFragmentManager.findFragmentByTag(tag)?.let {
            supportFragmentManager.beginTransaction().remove(it).commitNow()
        }
    }

    private fun minSdk30(): Boolean {
        return appBuildConfig.sdkInt >= Build.VERSION_CODES.R
    }

    @Suppress("NewApi") // we use appBuildConfig
    private fun hasWriteStoragePermission(): Boolean {
        return minSdk30() ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestWriteStoragePermission() {
        requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE)
    }

    private fun setupInternalToolbar(toolbar: Toolbar) {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        toolbar.setNavigationIcon(
            when (params.toolbarConfig) {
                is CustomTitle -> R.drawable.ic_arrow_left_24
                DaxPrivacyPro -> R.drawable.ic_close_24
            },
        )

        updateToolbarTitle(params.toolbarConfig)
    }

    private fun updateToolbarTitle(config: ToolbarConfig) {
        when (config) {
            is CustomTitle -> {
                supportActionBar?.setDisplayShowTitleEnabled(true)
                binding.includeToolbar.logoToolbar.hide()
                binding.includeToolbar.titleToolbar.hide()
                title = config.title
            }
            DaxPrivacyPro -> {
                supportActionBar?.setDisplayShowTitleEnabled(false)
                binding.includeToolbar.logoToolbar.show()
                binding.includeToolbar.titleToolbar.show()
                title = null
                toolbar.setNavigationOnClickListener { onBackPressed() }
            }
        }
    }

    private fun onUrlChanged(url: String?) {
        val addEmailPath = SubscriptionSettingsActivity.ADD_EMAIL_URL.toUri().path ?: return

        val shouldOverrideTitleForAddEmailFlow = url?.toUri()?.path?.startsWith(addEmailPath) ?: false

        updateToolbarTitle(
            if (shouldOverrideTitleForAddEmailFlow) {
                CustomTitle(getString(string.addEmailText))
            } else {
                params.toolbarConfig
            },
        )
    }

    private fun processCommand(command: Command) {
        when (command) {
            is BackToSettings, BackToSettingsActivateSuccess -> backToSettings()
            is SendJsEvent -> sendJsEvent(command.event)
            is SendResponseToJs -> sendResponseToJs(command.data)
            is SubscriptionSelected -> selectSubscription(command.id)
            is RestoreSubscription -> restoreSubscription()
            is GoToITR -> goToITR()
            is GoToPIR -> goToPIR()
            is GoToNetP -> goToNetP(command.activityParams)
            Reload -> binding.webview.reload()
        }
    }

    private fun sendJsEvent(event: SubscriptionEventData) {
        subscriptionJsMessaging.sendSubscriptionEvent(event)
    }

    private fun goToITR() {
        globalActivityStarter.start(
            this,
            SubscriptionsWebViewActivityWithParams(
                url = SubscriptionsConstants.ITR_URL,
            ),
        )
    }

    private fun goToPIR() {
        globalActivityStarter.start(this, PirScreenWithEmptyParams)
    }

    private fun goToNetP(params: ActivityParams) {
        globalActivityStarter.start(this, params)
    }

    private fun renderPurchaseState(purchaseState: PurchaseStateView) {
        when (purchaseState) {
            is PurchaseStateView.InProgress, PurchaseStateView.Inactive -> {
                // NO OP
            }
            is PurchaseStateView.Waiting -> {
                onPurchaseSuccess(null)
            }
            is PurchaseStateView.Success -> {
                pixelSender.reportPurchaseSuccessOrigin(params.origin)
                onPurchaseSuccess(purchaseState.subscriptionEventData)
            }
            is PurchaseStateView.Recovered -> {
                onPurchaseRecovered()
            }
            is PurchaseStateView.Failure -> {
                onPurchaseFailure()
            }
        }
    }

    private fun onPurchaseRecovered() {
        TextAlertDialogBuilder(this)
            .setTitle(getString(string.purchaseCompletedTitle))
            .setMessage(getString(string.purchaseRecoveredText))
            .setPositiveButton(string.ok)
            .addEventListener(
                object : TextAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked() {
                        finish()
                    }
                },
            )
            .show()
    }

    private fun onPurchaseSuccess(subscriptionEventData: SubscriptionEventData?) {
        TextAlertDialogBuilder(this)
            .setTitle(getString(string.purchaseCompletedTitle))
            .setMessage(getString(string.purchaseCompletedText))
            .setPositiveButton(string.ok)
            .addEventListener(
                object : TextAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked() {
                        if (subscriptionEventData != null) {
                            subscriptionJsMessaging.sendSubscriptionEvent(subscriptionEventData)
                        } else {
                            finish()
                        }
                    }
                },
            )
            .show()
    }

    private fun onPurchaseFailure() {
        TextAlertDialogBuilder(this)
            .setTitle(getString(string.purchaseErrorTitle))
            .setMessage(getString(string.purchaseError))
            .setPositiveButton(string.backToSettings)
            .addEventListener(
                object : TextAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked() {
                        finish()
                    }
                },
            )
            .show()
    }

    private fun selectSubscription(id: String) {
        viewModel.purchaseSubscription(this, id)
    }

    private fun sendResponseToJs(data: JsCallbackData) {
        subscriptionJsMessaging.onResponse(data)
    }

    private fun backToSettings() {
        if (params.url == ACTIVATE_URL) {
            setResult(RESULT_OK)
        }
        finish()
    }

    private val startForResultRestore = registerForActivityResult(StartActivityForResult()) { result: ActivityResult ->
        if (result.resultCode == RESULT_OK) {
            viewModel.onSubscriptionRestored()
        }
    }

    private fun restoreSubscription() {
        startForResultRestore.launch(globalActivityStarter.startIntent(this, RestoreSubscriptionScreenWithParams(isOriginWeb = true)))
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
        if (binding.webview.canGoBack()) {
            binding.webview.goBack()
        } else {
            super.onBackPressed()
        }
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
        private const val DOWNLOAD_CONFIRMATION_TAG = "DOWNLOAD_CONFIRMATION_TAG"
        private const val PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE = 200
        private const val CUSTOM_UA =
            "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/124.0.0.0 Mobile DuckDuckGo/5 Safari/537.36"
    }
}
