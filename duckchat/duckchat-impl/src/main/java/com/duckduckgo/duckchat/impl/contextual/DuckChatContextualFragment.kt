/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.duckchat.impl.contextual

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Message
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.annotation.AnyThread
import androidx.core.content.ContextCompat
import androidx.core.view.isInvisible
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.tabs.BrowserNav
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.ui.DuckDuckGoFragment
import com.duckduckgo.common.ui.view.dialog.ActionBottomSheetDialog
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.makeSnackbarWithNoBottomInset
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.FragmentViewModelFactory
import com.duckduckgo.common.utils.extensions.hideKeyboard
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.downloads.api.DOWNLOAD_SNACKBAR_DELAY
import com.duckduckgo.downloads.api.DOWNLOAD_SNACKBAR_LENGTH
import com.duckduckgo.downloads.api.DownloadCommand
import com.duckduckgo.downloads.api.DownloadConfirmationDialogListener
import com.duckduckgo.downloads.api.DownloadStateListener
import com.duckduckgo.downloads.api.DownloadsFileActions
import com.duckduckgo.downloads.api.FileDownloader
import com.duckduckgo.duckchat.impl.DuckChatInternal
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.databinding.FragmentContextualDuckAiBinding
import com.duckduckgo.duckchat.impl.feature.AIChatDownloadFeature
import com.duckduckgo.duckchat.impl.helper.DuckChatJSHelper
import com.duckduckgo.duckchat.impl.helper.Mode
import com.duckduckgo.duckchat.impl.helper.RealDuckChatJSHelper
import com.duckduckgo.duckchat.impl.ui.DuckChatWebViewClient
import com.duckduckgo.duckchat.impl.ui.SubscriptionsHandler
import com.duckduckgo.duckchat.impl.ui.filechooser.FileChooserIntentBuilder
import com.duckduckgo.duckchat.impl.ui.filechooser.capture.camera.CameraHardwareChecker
import com.duckduckgo.duckchat.impl.ui.filechooser.capture.launcher.UploadFromExternalMediaAppLauncher
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.duckduckgo.js.messaging.api.JsMessaging
import com.duckduckgo.js.messaging.api.SubscriptionEventData
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.subscriptions.api.SUBSCRIPTIONS_FEATURE_NAME
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.snackbar.BaseTransientBottomBar
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
import javax.inject.Inject
import javax.inject.Named

@InjectWith(FragmentScope::class)
class DuckChatContextualFragment :
    DuckDuckGoFragment(R.layout.fragment_contextual_duck_ai),
    DownloadConfirmationDialogListener {

    @Inject
    lateinit var viewModelFactory: FragmentViewModelFactory

    private val viewModel: DuckChatContextualViewModel by lazy {
        ViewModelProvider(this, viewModelFactory)[DuckChatContextualViewModel::class.java]
    }

    private val sharedContextualViewModel: DuckChatContextualSharedViewModel by viewModels({ requireParentFragment() })

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

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    @Inject
    lateinit var faviconManager: FaviconManager

    private val cookieManager: CookieManager by lazy { CookieManager.getInstance() }

    private var pendingFileDownload: FileDownloader.PendingFileDownload? = null
    private val downloadMessagesJob = ConflatedJob()

    private val binding: FragmentContextualDuckAiBinding by viewBinding()
    private var pendingUploadTask: ValueCallback<Array<Uri>>? = null

    private val root: ViewGroup by lazy { binding.root }

    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>
    private lateinit var backPressedCallback: OnBackPressedCallback
    internal val simpleWebview: WebView by lazy { binding.simpleWebview }
    private var isKeyboardVisible = false

    private val keyboardVisibilityListener =
        ViewTreeObserver.OnGlobalLayoutListener {
            runCatching {
                val rootView = binding.root
                val rect = Rect()
                rootView.getWindowVisibleDisplayFrame(rect)
                val visibleHeight = rect.height()
                val totalHeight = rootView.rootView.height
                val heightDiff = totalHeight - visibleHeight
                val threshold = (resources.displayMetrics.density * 100).toInt()
                val imeVisible = heightDiff > threshold
                if (imeVisible != isKeyboardVisible) {
                    isKeyboardVisible = imeVisible
                    if (binding.inputField.hasFocus()) {
                        viewModel.onKeyboardVisibilityChanged(imeVisible)
                    }
                }
            }
        }
    private val bottomSheetCallback =
        object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(
                bottomSheet: View,
                newState: Int,
            ) {
                if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                    viewModel.onSheetClosed()
                }
                backPressedCallback.isEnabled = newState != BottomSheetBehavior.STATE_HIDDEN
            }

            override fun onSlide(
                bottomSheet: View,
                slideOffset: Float,
            ) {
            }
        }

    private var lastWebViewX = 0f
    private var lastWebViewY = 0f

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        simpleWebview.let {
            it.webViewClient = webViewClient
            webViewClient.onPageFinishedListener = { url ->
                viewModel.onChatPageLoaded(url)
            }
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
                        startActivity(browserNav.openInNewTab(requireContext(), newWindowUrl))
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

            it.setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        lastWebViewX = event.x
                        lastWebViewY = event.y
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.x - lastWebViewX
                        val dy = event.y - lastWebViewY
                        lastWebViewX = event.x
                        lastWebViewY = event.y

                        val isHorizontal = kotlin.math.abs(dx) > kotlin.math.abs(dy)
                        val atBottom = !v.canScrollVertically(1)
                        val allowSheetDrag = !isHorizontal && atBottom && dy > 0

                        v.parent?.requestDisallowInterceptTouchEvent(!allowSheetDrag)
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.parent?.requestDisallowInterceptTouchEvent(false)
                    }
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
                        logcat { "Duck.ai JS Helper: process $featureName $method $id $data" }
                        when (featureName) {
                            RealDuckChatJSHelper.DUCK_CHAT_FEATURE_NAME -> {
                                appCoroutineScope.launch(dispatcherProvider.io()) {
                                    if (!viewModel.handleJSCall(method)) {
                                        duckChatJSHelper.processJsCallbackMessage(
                                            featureName,
                                            method,
                                            id,
                                            data,
                                            Mode.CONTEXTUAL,
                                            viewModel.updatedPageContext,
                                            viewModel.sheetTabId,
                                        )?.let { response ->
                                            logcat { "Duck.ai: response $response" }
                                            withContext(dispatcherProvider.main()) {
                                                contentScopeScripts.onResponse(response)
                                            }
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

        externalCameraLauncher.registerForResult(this) {
            when (it) {
                is UploadFromExternalMediaAppLauncher.MediaCaptureResult.MediaCaptured -> pendingUploadTask?.onReceiveValue(
                    arrayOf(
                        Uri.fromFile(it.file),
                    ),
                )

                is UploadFromExternalMediaAppLauncher.MediaCaptureResult.CouldNotCapturePermissionDenied -> {
                    pendingUploadTask?.onReceiveValue(null)
                    externalCameraLauncher.showPermissionRationaleDialog(requireActivity(), it.inputAction)
                }

                is UploadFromExternalMediaAppLauncher.MediaCaptureResult.NoMediaCaptured -> pendingUploadTask?.onReceiveValue(null)
                is UploadFromExternalMediaAppLauncher.MediaCaptureResult.ErrorAccessingMediaApp -> {
                    pendingUploadTask?.onReceiveValue(null)
                    Snackbar.make(root, it.messageId, BaseTransientBottomBar.LENGTH_SHORT).show()
                }
            }
            pendingUploadTask = null
        }

        configureBottomSheet(view)
        setupBackPressHandling()
        observeViewModel()

        requireArguments().getString(KEY_DUCK_AI_CONTEXTUAL_TAB_ID)?.let { tabId ->
            viewModel.onSheetOpened(tabId)
            setupKeyboardVisibilityListener()
        }
    }

    private fun configureBottomSheet(view: View) {
        val parent = view.parent as? View ?: return
        bottomSheetBehavior = BottomSheetBehavior.from(parent)

        configureBehaviour(bottomSheetBehavior)
        configureButtons()
    }

    private fun configureBehaviour(bottomSheetBehavior: BottomSheetBehavior<View>) {
        bottomSheetBehavior.isShouldRemoveExpandedCorners = false
        bottomSheetBehavior.skipCollapsed = true
        bottomSheetBehavior.isDraggable = true
        bottomSheetBehavior.isHideable = true
        bottomSheetBehavior.isFitToContents = true
        bottomSheetBehavior.expandedOffset = 0
    }

    private fun setupBackPressHandling() {
        backPressedCallback =
            object : OnBackPressedCallback(bottomSheetBehavior.state != BottomSheetBehavior.STATE_HIDDEN) {
                override fun handleOnBackPressed() {
                    viewModel.onContextualClose()
                }
            }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backPressedCallback)

        bottomSheetBehavior.addBottomSheetCallback(bottomSheetCallback)
    }

    private fun configureButtons() {
        binding.contextualClose.setOnClickListener {
            viewModel.onContextualClose()
        }
        binding.contextualNewChat.setOnClickListener {
            hideKeyboard(binding.inputField)
            viewModel.onNewChatRequested()
        }
        binding.contextualModeButtons.setOnClickListener { }
        binding.contextualModeRoot.setOnClickListener { }
        binding.inputField.setOnEditorActionListener(
            TextView.OnEditorActionListener { _, actionId, keyEvent ->
                if (actionId == EditorInfo.IME_ACTION_GO || keyEvent?.keyCode == KeyEvent.KEYCODE_ENTER) {
                    sendNativePrompt()
                    return@OnEditorActionListener true
                }
                false
            },
        )
        binding.inputField.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int,
                ) {
                    // NOOP
                }

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int,
                ) {
                    // NOOP
                }

                override fun afterTextChanged(text: Editable?) {
                    binding.duckAiContextualSend.isEnabled = !text.toString().isEmpty()
                    binding.duckAiContextualClearText.isInvisible = text.toString().isEmpty()
                }
            },
        )
        binding.duckAiContextualSend.setOnClickListener {
            sendNativePrompt()
        }

        binding.duckAiContextualClearText.setOnClickListener {
            viewModel.onPromptCleared()
        }

        binding.contextualFullScreen.setOnClickListener {
            viewModel.onFullModeRequested()
        }
        binding.duckAiContextualPageRemove.setOnClickListener {
            viewModel.removePageContext()
        }
        binding.duckAiAttachContextLayout.setOnClickListener {
            viewModel.addPageContext()
        }
        binding.contextualPromptSummarize.setOnClickListener {
            val prompt = getString(R.string.duckAIContextualPromptSummarize)
            viewModel.replacePrompt(binding.inputField.text.toString(), prompt)
        }
    }

    private fun clearInputField() {
        binding.inputField.text.clear()
        binding.inputField.setSelection(0)
        binding.inputField.scrollTo(0, 0)
    }

    private fun sendNativePrompt() {
        val prompt = binding.inputField.text.toString()
        if (prompt.isNotEmpty()) {
            viewModel.onPromptSent(prompt)
            clearInputField()
            hideKeyboard(binding.inputField)
        }
    }

    private fun observeViewModel() {
        viewModel.commands
            .onEach { command ->
                when (command) {
                    is DuckChatContextualViewModel.Command.SendSubscriptionAuthUpdateEvent -> {
                        val authUpdateEvent = SubscriptionEventData(
                            featureName = SUBSCRIPTIONS_FEATURE_NAME,
                            subscriptionName = "authUpdate",
                            params = JSONObject(),
                        )
                        contentScopeScripts.sendSubscriptionEvent(authUpdateEvent)
                    }

                    is DuckChatContextualViewModel.Command.LoadUrl -> {
                        logcat { "Duck.ai Contextual: load url ${command.url}" }
                        simpleWebview.loadUrl(command.url)
                    }

                    is DuckChatContextualViewModel.Command.OpenFullscreenMode -> {
                        binding.root.viewTreeObserver.removeOnGlobalLayoutListener(keyboardVisibilityListener)
                        val result = Bundle().apply {
                            putString(KEY_DUCK_AI_URL, command.url)
                        }

                        setFragmentResult(KEY_DUCK_AI_CONTEXTUAL_RESULT, result)
                    }

                    is DuckChatContextualViewModel.Command.ChangeSheetState -> {
                        bottomSheetBehavior.state = command.newState
                    }
                }
            }.launchIn(lifecycleScope)

        sharedContextualViewModel.commands
            .onEach { command ->
                when (command) {
                    is DuckChatContextualSharedViewModel.Command.PageContextAttached -> {
                        logcat { "Duck.ai Contextual: page context received" }
                        viewModel.onPageContextReceived(command.tabId, command.pageContext)
                    }

                    DuckChatContextualSharedViewModel.Command.OpenSheet -> {
                        setupKeyboardVisibilityListener()
                        viewModel.onSheetReopened()
                    }
                }
            }.launchIn(lifecycleScope)

        viewModel.viewState
            .onEach { viewState ->
                renderViewState(viewState)
            }.launchIn(lifecycleScope)

        observeSubscriptionEventDataChannel()
    }

    private fun setupKeyboardVisibilityListener() {
        binding.root.viewTreeObserver.removeOnGlobalLayoutListener(keyboardVisibilityListener)
        binding.root.viewTreeObserver.addOnGlobalLayoutListener(keyboardVisibilityListener)
    }

    private fun renderViewState(viewState: DuckChatContextualViewModel.ViewState) {
        logcat { "Duck.ai Contextual: render $viewState" }
        if (viewState.showFullscreen) {
            binding.contextualFullScreen.show()
        } else {
            binding.contextualFullScreen.gone()
        }

        when (viewState.sheetMode) {
            DuckChatContextualViewModel.SheetMode.INPUT -> {
                binding.contextualModeNativeContent.show()
                binding.simpleWebview.gone()

                binding.contextualNewChat.gone()

                renderPageContext(viewState.contextTitle, viewState.contextUrl, viewState.tabId)

                if (viewState.showContext) {
                    binding.duckAiContextualLayout.show()
                    binding.duckAiAttachContextLayout.gone()
                } else {
                    binding.duckAiContextualLayout.gone()
                    binding.duckAiAttachContextLayout.show()
                }
                if (viewState.prompt.isNotEmpty()) {
                    binding.inputField.setText(viewState.prompt)
                    binding.inputField.setSelection(viewState.prompt.length)
                } else {
                    clearInputField()
                }
            }

            DuckChatContextualViewModel.SheetMode.WEBVIEW -> {
                binding.contextualModeNativeContent.gone()
                binding.simpleWebview.show()
                binding.contextualNewChat.show()
            }
        }
    }

    private fun observeSubscriptionEventDataChannel() {
        viewModel.subscriptionEventDataFlow.onEach { subscriptionEventData ->
            contentScopeScripts.sendSubscriptionEvent(subscriptionEventData)
        }.launchIn(lifecycleScope)
    }

    private fun renderPageContext(
        pageTitle: String,
        pageUrl: String,
        tabId: String,
    ) {
        binding.duckAiContextualPageTitle.text = pageTitle
        viewModel.viewModelScope.launch {
            faviconManager.loadToViewFromLocalWithPlaceholder(tabId, pageUrl, binding.duckAiContextualFavicon)
        }
    }

    data class FileChooserRequestedParams(
        val filePickingMode: Int,
        val acceptMimeTypes: List<String>,
    )

    fun showFileChooser(
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: WebChromeClient.FileChooserParams,
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
        val canChooseMultipleFiles = fileChooserParams.filePickingMode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE
        val intent = fileChooserIntentBuilder.intent(fileChooserParams.acceptMimeTypes.toTypedArray(), canChooseMultipleFiles)
        startActivityForResult(intent, REQUEST_CODE_CHOOSE_FILE)
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

    override fun continueDownload(pendingFileDownload: FileDownloader.PendingFileDownload) {
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
        root.makeSnackbarWithNoBottomInset(
            getString(command.messageId, command.fileName),
            DOWNLOAD_SNACKBAR_LENGTH,
        )?.show()
    }

    private fun downloadFailed(command: DownloadCommand.ShowDownloadFailedMessage) {
        val downloadFailedSnackbar = root.makeSnackbarWithNoBottomInset(getString(command.messageId), Snackbar.LENGTH_LONG)
        root.postDelayed({ downloadFailedSnackbar?.show() }, DOWNLOAD_SNACKBAR_DELAY)
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
        root.postDelayed({ downloadSucceededSnackbar?.show() }, DOWNLOAD_SNACKBAR_DELAY)
    }

    private fun requestFileDownload(
        url: String,
        contentDisposition: String?,
        mimeType: String,
    ) {
        pendingFileDownload = FileDownloader.PendingFileDownload(
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
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
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
        if (resultCode != Activity.RESULT_OK || intent == null) {
            pendingUploadTask?.onReceiveValue(null)
            return
        }

        val uris = fileChooserIntentBuilder.extractSelectedFileUris(intent)
        pendingUploadTask?.onReceiveValue(uris)
    }

    override fun onResume() {
        simpleWebview.onResume()
        super.onResume()
        launchDownloadMessagesJob()
    }

    override fun onPause() {
        viewModel.onSheetClosed()
        downloadMessagesJob.cancel()
        simpleWebview.onPause()
        appCoroutineScope.launch(dispatcherProvider.io()) {
            cookieManager.flush()
        }
        super.onPause()
    }

    override fun onDestroyView() {
        bottomSheetBehavior.removeBottomSheetCallback(bottomSheetCallback)
        binding.root.viewTreeObserver.removeOnGlobalLayoutListener(keyboardVisibilityListener)
        super.onDestroyView()
        appCoroutineScope.launch(dispatcherProvider.io()) {
            cookieManager.flush()
        }
    }

    companion object {
        private const val PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE = 200
        private const val CUSTOM_UA =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/124.0.0.0 Mobile DuckDuckGo/5 Safari/537.36"
        const val REQUEST_CODE_CHOOSE_FILE = 100

        const val KEY_DUCK_AI_URL: String = "KEY_DUCK_AI_URL"
        const val KEY_DUCK_AI_CONTEXTUAL_RESULT: String = "KEY_DUCK_AI_CONTEXTUAL_RESULT"
        const val KEY_DUCK_AI_CONTEXTUAL_TAB_ID: String = "KEY_DUCK_AI_CONTEXTUAL_TAB_ID"
    }
}
