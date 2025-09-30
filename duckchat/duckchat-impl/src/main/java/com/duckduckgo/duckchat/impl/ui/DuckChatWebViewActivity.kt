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
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Message
import android.provider.MediaStore
import android.view.MenuItem
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebChromeClient.FileChooserParams
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.annotation.AnyThread
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.tabs.BrowserNav
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.dialog.ActionBottomSheetDialog
import com.duckduckgo.common.ui.view.makeSnackbarWithNoBottomInset
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.downloads.api.DOWNLOAD_SNACKBAR_DELAY
import com.duckduckgo.downloads.api.DOWNLOAD_SNACKBAR_LENGTH
import com.duckduckgo.downloads.api.DownloadCommand
import com.duckduckgo.downloads.api.DownloadConfirmationDialogListener
import com.duckduckgo.downloads.api.DownloadStateListener
import com.duckduckgo.downloads.api.DownloadsFileActions
import com.duckduckgo.downloads.api.FileDownloader
import com.duckduckgo.downloads.api.FileDownloader.PendingFileDownload
import com.duckduckgo.duckchat.impl.DuckChatInternal
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.feature.AIChatDownloadFeature
import com.duckduckgo.duckchat.impl.helper.DuckChatJSHelper
import com.duckduckgo.duckchat.impl.helper.RealDuckChatJSHelper.Companion.DUCK_CHAT_FEATURE_NAME
import com.duckduckgo.duckchat.impl.ui.filechooser.FileChooserIntentBuilder
import com.duckduckgo.duckchat.impl.ui.filechooser.capture.camera.CameraHardwareChecker
import com.duckduckgo.duckchat.impl.ui.filechooser.capture.launcher.UploadFromExternalMediaAppLauncher
import com.duckduckgo.duckchat.impl.ui.filechooser.capture.launcher.UploadFromExternalMediaAppLauncher.MediaCaptureResult.CouldNotCapturePermissionDenied
import com.duckduckgo.duckchat.impl.ui.filechooser.capture.launcher.UploadFromExternalMediaAppLauncher.MediaCaptureResult.ErrorAccessingMediaApp
import com.duckduckgo.duckchat.impl.ui.filechooser.capture.launcher.UploadFromExternalMediaAppLauncher.MediaCaptureResult.MediaCaptured
import com.duckduckgo.duckchat.impl.ui.filechooser.capture.launcher.UploadFromExternalMediaAppLauncher.MediaCaptureResult.NoMediaCaptured
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.duckduckgo.js.messaging.api.JsMessaging
import com.duckduckgo.js.messaging.api.SubscriptionEventData
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.navigation.api.getActivityParams
import com.duckduckgo.subscriptions.api.SUBSCRIPTIONS_FEATURE_NAME
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Named

internal data class DuckChatWebViewActivityWithParams(
    val url: String,
) : GlobalActivityStarter.ActivityParams

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(DuckChatWebViewActivityWithParams::class)
open class DuckChatWebViewActivity : DuckDuckGoActivity(), DownloadConfirmationDialogListener {

    private val viewModel: DuckChatWebViewActivityViewModel by bindViewModel()

    @Inject
    lateinit var webViewClient: DuckChatWebViewClient

    @Inject
    @Named("ContentScopeScripts")
    lateinit var contentScopeScripts: JsMessaging

    @Inject
    lateinit var duckChatJSHelper: DuckChatJSHelper

    @Inject
    lateinit var subscriptionsHandler: SubscriptionsHandler

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
    lateinit var fileChooserIntentBuilder: FileChooserIntentBuilder

    @Inject
    lateinit var cameraHardwareChecker: CameraHardwareChecker

    @Inject
    lateinit var externalCameraLauncher: UploadFromExternalMediaAppLauncher

    private var pendingFileDownload: PendingFileDownload? = null
    private val downloadMessagesJob = ConflatedJob()

    private var pendingUploadTask: ValueCallback<Array<Uri>>? = null

    private val root: ViewGroup by lazy { findViewById(android.R.id.content) }
    private val toolbar: Toolbar? by lazy { findViewById(com.duckduckgo.mobile.android.R.id.toolbar) }
    internal val simpleWebview: WebView by lazy { findViewById(R.id.simpleWebview) }

    protected open val layoutResId: Int = R.layout.activity_duck_chat_webview

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        duckChat.observeCloseEvent(this) {
            finish()
        }

        setContentView(layoutResId)
        toolbar?.let {
            setupToolbar(it)
        }

        val params = intent.getActivityParams(DuckChatWebViewActivityWithParams::class.java)
        val url = params?.url

        simpleWebview.let {
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

                override fun onShowFileChooser(
                    webView: WebView,
                    filePathCallback: ValueCallback<Array<Uri>>,
                    fileChooserParams: FileChooserParams,
                ): Boolean {
                    return try {
                        showFileChooser(filePathCallback, fileChooserParams)
                        true
                    } catch (e: Throwable) {
                        // cancel the request using the documented way
                        filePathCallback.onReceiveValue(null)
                        throw e
                    }
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
                        when (featureName) {
                            DUCK_CHAT_FEATURE_NAME -> {
                                appCoroutineScope.launch(dispatcherProvider.io()) {
                                    duckChatJSHelper.processJsCallbackMessage(featureName, method, id, data)?.let { response ->
                                        contentScopeScripts.onResponse(response)
                                    }
                                }
                            }

                            SUBSCRIPTIONS_FEATURE_NAME -> {
                                subscriptionsHandler.handleSubscriptionsFeature(
                                    featureName,
                                    method,
                                    id,
                                    data,
                                    this@DuckChatWebViewActivity,
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

        url?.let {
            simpleWebview.loadUrl(it)
        }

        externalCameraLauncher.registerForResult(this) {
            when (it) {
                is MediaCaptured -> pendingUploadTask?.onReceiveValue(arrayOf(Uri.fromFile(it.file)))
                is CouldNotCapturePermissionDenied -> {
                    pendingUploadTask?.onReceiveValue(null)
                    externalCameraLauncher.showPermissionRationaleDialog(this, it.inputAction)
                }

                is NoMediaCaptured -> pendingUploadTask?.onReceiveValue(null)
                is ErrorAccessingMediaApp -> {
                    pendingUploadTask?.onReceiveValue(null)
                    Snackbar.make(root, it.messageId, BaseTransientBottomBar.LENGTH_SHORT).show()
                }
            }
            pendingUploadTask = null
        }

        // Observe ViewModel commands
        viewModel.commands
            .onEach { command ->
                when (command) {
                    is DuckChatWebViewActivityViewModel.Command.SendSubscriptionAuthUpdateEvent -> {
                        val authUpdateEvent = SubscriptionEventData(
                            featureName = SUBSCRIPTIONS_FEATURE_NAME,
                            subscriptionName = "authUpdate",
                            params = org.json.JSONObject(),
                        )
                        contentScopeScripts.sendSubscriptionEvent(authUpdateEvent)
                    }
                }
            }.launchIn(lifecycleScope)
    }

    data class FileChooserRequestedParams(
        val filePickingMode: Int,
        val acceptMimeTypes: List<String>,
    )

    fun showFileChooser(
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: FileChooserParams,
    ) {
        val mimeTypes = convertAcceptTypesToMimeTypes(fileChooserParams.acceptTypes)
        val fileChooserRequestedParams = FileChooserRequestedParams(fileChooserParams.mode, mimeTypes)
        val cameraHardwareAvailable = cameraHardwareChecker.hasCameraHardware()

        when {
            fileChooserParams.isCaptureEnabled -> {
                when {
                    acceptsOnly("image/", fileChooserParams.acceptTypes) && cameraHardwareAvailable ->
                        launchCameraCapture(filePathCallback, fileChooserRequestedParams, MediaStore.ACTION_IMAGE_CAPTURE)

                    acceptsOnly("video/", fileChooserParams.acceptTypes) && cameraHardwareAvailable ->
                        launchCameraCapture(filePathCallback, fileChooserRequestedParams, MediaStore.ACTION_VIDEO_CAPTURE)

                    acceptsOnly("audio/", fileChooserParams.acceptTypes) ->
                        launchCameraCapture(filePathCallback, fileChooserRequestedParams, MediaStore.Audio.Media.RECORD_SOUND_ACTION)

                    else ->
                        launchFilePicker(filePathCallback, fileChooserRequestedParams)
                }
            }

            fileChooserParams.acceptTypes.any { it.startsWith("image/") && cameraHardwareAvailable } ->
                launchImageOrCameraChooser(filePathCallback, fileChooserRequestedParams, MediaStore.ACTION_IMAGE_CAPTURE)

            fileChooserParams.acceptTypes.any { it.startsWith("video/") && cameraHardwareAvailable } ->
                launchImageOrCameraChooser(filePathCallback, fileChooserRequestedParams, MediaStore.ACTION_VIDEO_CAPTURE)

            else ->
                launchFilePicker(filePathCallback, fileChooserRequestedParams)
        }
    }

    private fun launchFilePicker(
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: FileChooserRequestedParams,
    ) {
        pendingUploadTask = filePathCallback
        val canChooseMultipleFiles = fileChooserParams.filePickingMode == FileChooserParams.MODE_OPEN_MULTIPLE
        val intent = fileChooserIntentBuilder.intent(fileChooserParams.acceptMimeTypes.toTypedArray(), canChooseMultipleFiles)
        startActivityForResult(intent, REQUEST_CODE_CHOOSE_FILE)
    }

    private fun launchCameraCapture(
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: FileChooserRequestedParams,
        inputAction: String,
    ) {
        if (Intent(inputAction).resolveActivity(packageManager) == null) {
            launchFilePicker(filePathCallback, fileChooserParams)
            return
        }

        pendingUploadTask = filePathCallback
        externalCameraLauncher.launch(inputAction)
    }

    private fun launchImageOrCameraChooser(
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: FileChooserRequestedParams,
        inputAction: String,
    ) {
        val cameraString = getString(R.string.imageCaptureCameraGalleryDisambiguationCameraOption)
        val cameraIcon = com.duckduckgo.mobile.android.R.drawable.ic_camera_24

        val galleryString = getString(R.string.imageCaptureCameraGalleryDisambiguationGalleryOption)
        val galleryIcon = com.duckduckgo.mobile.android.R.drawable.ic_image_24

        ActionBottomSheetDialog.Builder(this)
            .setTitle(getString(R.string.imageCaptureCameraGalleryDisambiguationTitle))
            .setPrimaryItem(galleryString, galleryIcon)
            .setSecondaryItem(cameraString, cameraIcon)
            .addEventListener(
                object : ActionBottomSheetDialog.EventListener() {
                    override fun onPrimaryItemClicked() {
                        launchFilePicker(filePathCallback, fileChooserParams)
                    }

                    override fun onSecondaryItemClicked() {
                        launchCameraCapture(filePathCallback, fileChooserParams, inputAction)
                    }

                    override fun onBottomSheetDismissed() {
                        filePathCallback.onReceiveValue(null)
                        pendingUploadTask = null
                    }
                },
            )
            .show()
    }

    private fun acceptsOnly(
        type: String,
        acceptTypes: Array<String>,
    ): Boolean {
        return acceptTypes.filter { it.startsWith(type) }.size == acceptTypes.size
    }

    private fun convertAcceptTypesToMimeTypes(acceptTypes: Array<String>): List<String> {
        val mimeTypeMap = MimeTypeMap.getSingleton()
        val mimeTypes = mutableSetOf<String>()
        acceptTypes.forEach { type ->
            // Attempt to convert any identified file extensions into corresponding MIME types.
            val fileExtension = MimeTypeMap.getFileExtensionFromUrl(type)
            if (fileExtension.isNotEmpty()) {
                mimeTypeMap.getMimeTypeFromExtension(type.substring(1))?.let {
                    mimeTypes.add(it)
                }
            } else {
                mimeTypes.add(type)
            }
        }
        return mimeTypes.toList()
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
        if (simpleWebview.canGoBack()) {
            simpleWebview.goBack()
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
        root.makeSnackbarWithNoBottomInset(getString(command.messageId, command.fileName), DOWNLOAD_SNACKBAR_LENGTH)?.show()
    }

    private fun downloadFailed(command: DownloadCommand.ShowDownloadFailedMessage) {
        val downloadFailedSnackbar = root.makeSnackbarWithNoBottomInset(getString(command.messageId), Snackbar.LENGTH_LONG)
        root.postDelayed({ downloadFailedSnackbar.show() }, DOWNLOAD_SNACKBAR_DELAY)
    }

    private fun downloadSucceeded(command: DownloadCommand.ShowDownloadSuccessMessage) {
        val downloadSucceededSnackbar = root.makeSnackbarWithNoBottomInset(
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
        root.postDelayed({ downloadSucceededSnackbar.show() }, DOWNLOAD_SNACKBAR_DELAY)
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

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_CHOOSE_FILE) {
            handleFileUploadResult(resultCode, data)
        }
    }

    private fun handleFileUploadResult(
        resultCode: Int,
        intent: Intent?,
    ) {
        if (resultCode != RESULT_OK || intent == null) {
            pendingUploadTask?.onReceiveValue(null)
            return
        }

        val uris = fileChooserIntentBuilder.extractSelectedFileUris(intent)
        pendingUploadTask?.onReceiveValue(uris)
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
        private const val REQUEST_CODE_CHOOSE_FILE = 100
    }
}
