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

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Message
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebChromeClient.FileChooserParams
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.app.tabs.BrowserNav
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.ui.view.dialog.ActionBottomSheetDialog
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.FragmentViewModelFactory
import com.duckduckgo.downloads.api.DownloadStateListener
import com.duckduckgo.downloads.api.DownloadsFileActions
import com.duckduckgo.downloads.api.FileDownloader
import com.duckduckgo.duckchat.impl.DuckChatInternal
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.databinding.BottomSheetDuckAiContextualBinding
import com.duckduckgo.duckchat.impl.feature.AIChatDownloadFeature
import com.duckduckgo.duckchat.impl.helper.DuckChatJSHelper
import com.duckduckgo.duckchat.impl.helper.RealDuckChatJSHelper.Companion.DUCK_CHAT_FEATURE_NAME
import com.duckduckgo.duckchat.impl.ui.DuckChatWebViewFragment.Companion.KEY_DUCK_AI_URL
import com.duckduckgo.duckchat.impl.ui.DuckChatWebViewFragment.Companion.REQUEST_CODE_CHOOSE_FILE
import com.duckduckgo.duckchat.impl.ui.DuckChatWebViewFragment.FileChooserRequestedParams
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
import com.duckduckgo.subscriptions.api.SUBSCRIPTIONS_FEATURE_NAME
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.logcat
import org.json.JSONObject

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
    private val duckChat: DuckChatInternal,
    private val aiChatDownloadFeature: AIChatDownloadFeature,
    private val fileChooserIntentBuilder: FileChooserIntentBuilder,
    private val cameraHardwareChecker: CameraHardwareChecker,
    private val externalCameraLauncher: UploadFromExternalMediaAppLauncher,
    private val globalActivityStarter: GlobalActivityStarter,
) : BottomSheetDialogFragment() {

    internal lateinit var simpleWebview: WebView
    internal lateinit var inputControls: View

    private val viewModel: DuckChatWebViewViewModel by lazy {
        ViewModelProvider(this, viewModelFactory)[DuckChatWebViewViewModel::class.java]
    }

    private var pendingUploadTask: ValueCallback<Array<Uri>>? = null

    override fun getTheme(): Int {
        return R.style.DuckChatBottomSheetDialogTheme
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val binding = BottomSheetDuckAiContextualBinding.inflate(inflater, container, false)
        simpleWebview = binding.simpleWebview // Initialize simpleWebview from the binding
        inputControls = binding.inputModeWidgetCard // Initialize simpleWebview from the binding
        configureViews(binding)
        return binding.root
    }

    private fun configureViews(binding: BottomSheetDuckAiContextualBinding) {
        (dialog as BottomSheetDialog).behavior.state = BottomSheetBehavior.STATE_COLLAPSED
        configureDialogButtons(binding)
    }

    private fun configureDialogButtons(binding: BottomSheetDuckAiContextualBinding) {
        binding.actionSend.setOnClickListener {
            inputControls.gone()
            (dialog as BottomSheetDialog).behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        val url = arguments?.getString(KEY_DUCK_AI_URL) ?: "https://duckduckgo.com/?q=DuckDuckGo+AI+Chat&ia=chat&duckai=5"

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
                        if (viewModel.handleOnSameWebView(newWindowUrl)) {
                            simpleWebview.loadUrl(newWindowUrl)
                        } else {
                            startActivity(browserNav.openInNewTab(requireContext(), newWindowUrl))
                        }
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
                        // requestFileDownload(url, contentDisposition, mimeType)
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
            simpleWebview.loadUrl(it)
        }

        externalCameraLauncher.registerForResult(this) {
            when (it) {
                is MediaCaptured -> pendingUploadTask?.onReceiveValue(arrayOf(Uri.fromFile(it.file)))
                is CouldNotCapturePermissionDenied -> {
                    pendingUploadTask?.onReceiveValue(null)
                    externalCameraLauncher.showPermissionRationaleDialog(requireActivity(), it.inputAction)
                }

                is NoMediaCaptured -> pendingUploadTask?.onReceiveValue(null)
                is ErrorAccessingMediaApp -> {
                    pendingUploadTask?.onReceiveValue(null)
                    Snackbar.make(simpleWebview, it.messageId, BaseTransientBottomBar.LENGTH_SHORT).show()
                }
            }
            pendingUploadTask = null
        }

        observeViewModel()
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

    private fun acceptsOnly(
        type: String,
        acceptTypes: Array<String>,
    ): Boolean {
        return acceptTypes.filter { it.startsWith(type) }.size == acceptTypes.size
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

        ActionBottomSheetDialog.Builder(requireContext())
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

    private fun launchCameraCapture(
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: FileChooserRequestedParams,
        inputAction: String,
    ) {
        if (Intent(inputAction).resolveActivity(requireActivity().packageManager) == null) {
            launchFilePicker(filePathCallback, fileChooserParams)
            return
        }

        pendingUploadTask = filePathCallback
        externalCameraLauncher.launch(inputAction)
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

    companion object {
        const val TAG = "DuckChatBottomSheet"
        private const val CUSTOM_UA =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/124.0.0.0 Mobile DuckDuckGo/5 Safari/537.36"
    }
}
