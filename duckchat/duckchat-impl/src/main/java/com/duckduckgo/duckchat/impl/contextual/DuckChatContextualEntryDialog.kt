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

import android.app.Activity
import android.app.Dialog
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.ValueCallback
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.tabs.BrowserNav
import com.duckduckgo.common.ui.DuckDuckGoBottomSheetDialogFragment
import com.duckduckgo.common.utils.FragmentViewModelFactory
import com.duckduckgo.common.utils.extensions.hideKeyboard
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.duckchat.impl.DuckChatInternal
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.databinding.DialogContextualDuckAiEntryBinding
import com.duckduckgo.duckchat.impl.ui.filechooser.FileChooserIntentBuilder
import com.duckduckgo.duckchat.impl.ui.filechooser.capture.launcher.UploadFromExternalMediaAppLauncher
import com.duckduckgo.js.messaging.api.JsMessaging
import com.duckduckgo.voice.api.VoiceSearchLauncher
import com.duckduckgo.voice.api.VoiceSearchLauncher.Source.BROWSER
import com.duckduckgo.voice.api.VoiceSearchLauncher.VoiceSearchMode
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Named
import com.google.android.material.R as MaterialR

/**
 * The contextual Duck.ai entry surface shown when "Ask about page" is chosen from the entry menu.
 *
 * Presented as a scrimmed dialog (same scrim as the fire dialog) with a transparent background so
 * only the suggested prompts and the native input float over the page. It owns the INPUT stage: when
 * the user submits a prompt or picks a suggestion, it parks the prompt and asks the host to show the
 * contextual sheet (via the shared view model), which consumes the prompt. The hand-off is derived
 * from the retained tab id, so it survives configuration changes.
 */
@InjectWith(FragmentScope::class)
class DuckChatContextualEntryDialog : DuckDuckGoBottomSheetDialogFragment() {

    @Inject
    lateinit var contextualNativeInputManager: ContextualNativeInputManager

    @Inject
    @Named("ContentScopeScripts")
    lateinit var contentScopeScripts: JsMessaging

    @Inject
    lateinit var browserNav: BrowserNav

    @Inject
    lateinit var duckChat: DuckChatInternal

    @Inject
    lateinit var voiceSearchLauncher: VoiceSearchLauncher

    @Inject
    lateinit var fileChooserIntentBuilder: FileChooserIntentBuilder

    @Inject
    lateinit var externalCameraLauncher: UploadFromExternalMediaAppLauncher

    @Inject
    lateinit var viewModelFactory: FragmentViewModelFactory

    private val viewModel: DuckChatContextualEntryViewModel by lazy {
        ViewModelProvider(this, viewModelFactory)[DuckChatContextualEntryViewModel::class.java]
    }

    private val sharedContextualViewModel: DuckChatContextualSharedViewModel by viewModels({ requireParentFragment() })
    private lateinit var tabId: String

    // Set once when the dialog hands off to the sheet, so onDismiss knows the sheet now owns the
    // contextual input state and must not revert it. Read once on dismiss; the dialog is single-use.
    private var handedOffToSheet = false

    // No running chat in the entry stage; the widget's chat-id-driven affordances stay collapsed.
    private val chatIdFlow = MutableStateFlow<String?>(null)
    private var _binding: DialogContextualDuckAiEntryBinding? = null
    private val binding get() = _binding!!

    // Pending WebView-style file/image upload callback while the picker/camera activity is in flight.
    private var pendingUploadTask: ValueCallback<Array<Uri>>? = null

    override fun show(
        fragmentManager: FragmentManager,
        tag: String?,
    ) {
        if (fragmentManager.isStateSaved) return
        super.show(fragmentManager, tag)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        voiceSearchLauncher.registerResultsCallback(this, requireActivity(), BROWSER) { event ->
            if (event is VoiceSearchLauncher.Event.VoiceRecognitionSuccess) {
                val result = event.result
                onVoiceRecognitionSuccess(
                    query = result.query,
                    isDuckAiResult = result is VoiceSearchLauncher.VoiceRecognitionResult.DuckAiResult,
                )
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return BottomSheetDialog(requireContext(), R.style.Widget_DuckDuckGo_DuckAiContextualEntryDialog)
    }

    override fun onStart() {
        super.onStart()
        // Make the sheet full-height so the root's weighted layout applies: the suggestions area flexes
        // and scrolls while the input stays pinned at the bottom. Without this the modal frame wraps its
        // content and, in a short (landscape) window, the input gets pushed off / the sheet oscillates.
        (dialog as? BottomSheetDialog)
            ?.findViewById<View>(MaterialR.id.design_bottom_sheet)
            ?.updateLayoutParams { height = ViewGroup.LayoutParams.MATCH_PARENT }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = DialogContextualDuckAiEntryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        tabId = requireNotNull(requireArguments().getString(ARG_TAB_ID)) {
            "DuckChatContextualEntryDialog requires $ARG_TAB_ID argument"
        }
        viewModel.start(tabId)
        registerFileChooserResult()
        configureBehavior()
        configureWindowInsets()
        configureInput()
        configureSuggestions()
        configureQuickAction()
        configureDismissOnEmptyTap()
        observeViewModel()
        focusInput()
    }

    private fun observeViewModel() {
        viewModel.viewState
            .onEach { renderViewState(it) }
            .launchIn(viewLifecycleOwner.lifecycleScope)
        viewModel.commands
            .onEach { handleCommand(it) }
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    private fun renderViewState(state: DuckChatContextualEntryViewModel.ViewState) {
        val context = state.attachedContext
        if (context != null) {
            binding.entryNativeInputWidget.setPageContext(context.title, context.url)
        } else {
            binding.entryNativeInputWidget.clearPageContext()
        }
        updateQuickActionVisibility()
    }

    private fun handleCommand(command: DuckChatContextualEntryViewModel.Command) {
        when (command) {
            DuckChatContextualEntryViewModel.Command.HandOffToSheet -> {
                handedOffToSheet = true
                sharedContextualViewModel.requestShowSheet(tabId)
                dismiss()
            }
        }
    }

    private fun registerFileChooserResult() {
        externalCameraLauncher.registerForResult(this) {
            when (it) {
                is UploadFromExternalMediaAppLauncher.MediaCaptureResult.MediaCaptured ->
                    pendingUploadTask?.onReceiveValue(arrayOf(Uri.fromFile(it.file)))

                is UploadFromExternalMediaAppLauncher.MediaCaptureResult.CouldNotCapturePermissionDenied -> {
                    pendingUploadTask?.onReceiveValue(null)
                    externalCameraLauncher.showPermissionRationaleDialog(requireActivity(), it.inputAction)
                }

                is UploadFromExternalMediaAppLauncher.MediaCaptureResult.NoMediaCaptured -> pendingUploadTask?.onReceiveValue(null)
                is UploadFromExternalMediaAppLauncher.MediaCaptureResult.ErrorAccessingMediaApp -> {
                    pendingUploadTask?.onReceiveValue(null)
                    Snackbar.make(binding.entryDialogRoot, it.messageId, Snackbar.LENGTH_SHORT).show()
                }
            }
            pendingUploadTask = null
        }
    }

    private fun configureDismissOnEmptyTap() {
        val dismissOnTap = View.OnClickListener { dismiss() }
        binding.entryDialogRoot.setOnClickListener(dismissOnTap)
        binding.entryPromptsContent.setOnClickListener(dismissOnTap)
        binding.entryNativeInputCard.isClickable = true
    }

    private fun configureBehavior() {
        (dialog as? BottomSheetDialog)?.behavior?.apply {
            skipCollapsed = true
            state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    private fun configureQuickAction() {
        binding.entryPromptQuickAction.setOnClickListener {
            if (viewModel.viewState.value.attachedContext == null) return@setOnClickListener
            viewModel.onPromptSubmitted(NativeInputPrompt(getString(R.string.duckAIContextualPromptSummarize), null, null, null, null, null))
        }
    }

    private fun configureWindowInsets() {
        dialog?.window?.let { WindowCompat.setDecorFitsSystemWindows(it, false) }
        ViewCompat.setOnApplyWindowInsetsListener(binding.entryDialogRoot) { v, insets ->
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val systemBottom = insets.getInsets(
                WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.displayCutout(),
            ).bottom
            v.updatePadding(bottom = maxOf(ime, systemBottom))
            insets
        }
        ViewCompat.requestApplyInsets(binding.entryDialogRoot)
    }

    private fun focusInput() {
        // Auto-focus the composer once laid out so the keyboard comes up as soon as the dialog is shown.
        binding.entryNativeInputWidget.doOnLayout {
            binding.entryNativeInputWidget.focusInput(activity)
        }
    }

    private fun configureSuggestions() {
        binding.entrySuggestionsView.onSuggestionSelected = { suggestion ->
            viewModel.onSuggestionSubmitted(NativeInputPrompt(suggestion.prompt, null, null, null, null, null))
        }

        binding.entrySuggestionsView.onContentChanged = { updateQuickActionVisibility() }
        sharedContextualViewModel.commands
            .onEach { command ->
                when (command) {
                    is DuckChatContextualSharedViewModel.Command.PageContextAttached ->
                        viewModel.onPageContextReceived(command.pageContext)

                    is DuckChatContextualSharedViewModel.Command.MainBrowserPageFinished ->
                        // The dialog may have opened while the page was still loading, so the initial
                        // collection returned no usable context and the suggestions fell back to generic.
                        // Re-collect now that the page finished so the page-specific prompts can resolve.
                        // When storePageContext is enabled the browser re-collects and pushes context on
                        // page finish itself, so only re-request when it isn't.
                        if (!command.isStorePageContextEnabled) sharedContextualViewModel.requestPageContext()

                    else -> {}
                }
            }
            .launchIn(viewLifecycleOwner.lifecycleScope)
        viewModel.viewState
            .map { it.attachedContext?.serialized }
            .filterNotNull()
            .distinctUntilChanged()
            .onEach { binding.entrySuggestionsView.onPageContextUpdated(it) }
            .launchIn(viewLifecycleOwner.lifecycleScope)
        binding.entrySuggestionsView.load()
        sharedContextualViewModel.requestPageContext()
    }

    private fun configureInput() {
        contextualNativeInputManager.init(
            tabId = tabId,
            card = binding.entryNativeInputCard,
            widget = binding.entryNativeInputWidget,
            jsMessaging = contentScopeScripts,
            lifecycleOwner = viewLifecycleOwner,
            chatIdFlow = chatIdFlow,
            onSearchSubmitted = { query ->
                startActivity(browserNav.openInNewTab(requireContext(), query))
                dismiss()
            },
            onPromptSubmitted = { prompt -> viewModel.onPromptSubmitted(prompt) },
            onCameraCaptureRequested = { callback -> launchCameraCapture(callback) },
            onFilePickerRequested = { callback, mimeTypes -> launchFilePicker(callback, mimeTypes) },
            onAskAboutPage = { viewModel.onAttachContextRequested() },
            onPageContextRemoved = { viewModel.onContextRemoved() },
            onVoiceChatRequested = {
                duckChat.openVoiceDuckChat()
                dismiss()
            },
            onVoiceSearchRequested = {
                activity?.hideKeyboard()
                voiceSearchLauncher.launch(requireActivity(), VoiceSearchMode.DUCK_AI)
            },
        )
        contextualNativeInputManager.onInputMode()
        contextualNativeInputManager.onContextualReopened(tabId)
    }

    private fun onVoiceRecognitionSuccess(
        query: String,
        isDuckAiResult: Boolean,
    ) {
        if (query.isBlank()) return
        if (isDuckAiResult) {
            viewModel.onPromptSubmitted(NativeInputPrompt(query, null, null, null, null, null))
        } else {
            startActivity(browserNav.openInNewTab(requireContext(), query))
            dismiss()
        }
    }

    private fun updateQuickActionVisibility() {
        // Summarize acts on the attached page, and suggestions take its place when present.
        val show = viewModel.viewState.value.attachedContext != null && !binding.entrySuggestionsView.hasContent()
        binding.entryPromptQuickAction.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun launchCameraCapture(callback: ValueCallback<Array<Uri>>) {
        val action = MediaStore.ACTION_IMAGE_CAPTURE
        if (Intent(action).resolveActivity(requireActivity().packageManager) == null) {
            // No camera app available; fall back to picking an image from the file picker.
            launchFilePicker(callback, listOf("image/*"))
            return
        }
        pendingUploadTask = callback
        externalCameraLauncher.launch(action)
    }

    private fun launchFilePicker(
        callback: ValueCallback<Array<Uri>>,
        mimeTypes: List<String>,
    ) {
        pendingUploadTask = callback
        val types = mimeTypes.ifEmpty { listOf("*/*") }
        startActivityForResult(fileChooserIntentBuilder.intent(types.toTypedArray(), true), REQUEST_CODE_CHOOSE_FILE)
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_CODE_CHOOSE_FILE) return
        val uploadTask = pendingUploadTask
        pendingUploadTask = null
        if (resultCode != Activity.RESULT_OK || data == null) {
            uploadTask?.onReceiveValue(null)
            return
        }
        uploadTask?.onReceiveValue(fileChooserIntentBuilder.extractSelectedFileUris(data))
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        if (!handedOffToSheet && ::tabId.isInitialized && activity?.isChangingConfigurations != true) {
            contextualNativeInputManager.onContextualClosed(tabId)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "DuckChatContextualEntryDialog"
        private const val ARG_TAB_ID = "tabId"
        private const val REQUEST_CODE_CHOOSE_FILE = 100

        fun newInstance(tabId: String): DuckChatContextualEntryDialog =
            DuckChatContextualEntryDialog().apply {
                arguments = bundleOf(ARG_TAB_ID to tabId)
            }
    }
}
