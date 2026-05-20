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

package com.duckduckgo.duckchat.impl.ui.nativeinput.views

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.text.InputType
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.ValueCallback
import android.widget.EditText
import android.widget.FrameLayout
import androidx.core.view.doOnAttach
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.ViewViewModelFactory
import com.duckduckgo.common.utils.extensions.hideKeyboard
import com.duckduckgo.common.utils.extensions.showKeyboard
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.duckchat.api.nativeinput.NativeInputState
import com.duckduckgo.duckchat.api.nativeinput.NativeInputStateProvider
import com.duckduckgo.duckchat.impl.ChatState
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.duckduckgo.duckchat.impl.helper.PendingNativeFile
import com.duckduckgo.duckchat.impl.helper.PendingNativeImage
import com.duckduckgo.duckchat.impl.inputscreen.ui.view.InputModeWidget
import com.duckduckgo.duckchat.impl.inputscreen.ui.view.InputScreenButtons
import com.duckduckgo.duckchat.impl.nativeinput.NativeInputHost
import com.duckduckgo.duckchat.impl.store.DefaultTogglePosition
import com.duckduckgo.duckchat.impl.ui.NativeInputModeWidgetViewModel
import com.google.android.material.card.MaterialCardView
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import logcat.logcat
import org.json.JSONArray
import javax.inject.Inject

interface NativeInputWidget {

    var text: String
    var onBack: (() -> Unit)?
    var onSearchSelected: ((animate: Boolean) -> Unit)?
    var onChatSelected: ((animate: Boolean) -> Unit)?
    var onClearTextTapped: (() -> Unit)?
    var onStopTapped: (() -> Unit)?
    var onVoiceSearchClick: (() -> Unit)?
    var onVoiceChatClick: (() -> Unit)?
    var onImageClick: (() -> Unit)?
    var onPaidTierChanged: ((Boolean) -> Unit)?
    var onAttachmentChooserStateChanged: ((Boolean) -> Unit)?
    val isModelMenuVisible: Boolean

    fun onBackPressed()
    fun focusInput(activity: Activity?)
    fun hasInputFocus(): Boolean
    fun clearInputFocus()
    fun requestInputFocus()
    fun beginEnterAnimationPreview()
    fun endEnterAnimationPreview()
    fun selectAllText()
    fun hideKeyboard()
    fun showKeyboard()
    fun selectChatTab()
    fun applyDefaultTogglePosition()
    fun saveLastUsedTogglePosition(isChat: Boolean)
    fun isChatTabSelected(): Boolean
    fun hideMainButtons()
    fun setVoiceSearchAvailable(available: Boolean)
    fun setVoiceChatAvailable(available: Boolean)
    fun submitMessage(message: String?)
    fun submitAsChat(): Boolean
    fun setImageButtonVisible(visible: Boolean)
    fun setToggleVisible(visible: Boolean)
    fun setFloatingSubmitContainer(container: ViewGroup)
    fun getSelectedModelId(): String?
    fun getResolvedReasoningEffort(): String?
    fun getSelectedTool(): String?
    fun clearSelectedTool()
    fun setModelPickerEnabled(enabled: Boolean)

    /**
     * Binds a reactive source of "should the model picker be enabled?"
     */
    fun bindModelPickerEnabledSource(source: Flow<Boolean>)
    fun getImageAttachmentsJson(): JSONArray?
    fun getFileAttachmentsJson(): JSONArray?
    fun clearAttachments()
    fun storePendingPrompt(query: String)
    fun configure(tabId: String, isDuckAiMode: Boolean, isBottom: Boolean)
    fun configureContextual(tabId: String)
    fun isWidgetBottom(): Boolean
    fun setWidgetPosition(isBottom: Boolean)
    fun setWidgetRootView(view: View)

    fun bindAttachmentCallbacks(
        onCameraCaptureRequested: (ValueCallback<Array<Uri>>) -> Unit,
        onFilePickerRequested: (ValueCallback<Array<Uri>>, List<String>) -> Unit,
    )

    fun bindInputEvents(
        onSearchTextChanged: (String) -> Unit,
        onSearchSubmitted: (String) -> Unit,
        onChatSubmitted: (String) -> Unit,
    )

    fun bindTabCount(
        lifecycleOwner: LifecycleOwner,
        tabCount: LiveData<Int>,
    )

    fun bindChatSuggestions(
        lifecycleOwner: LifecycleOwner,
        onChatSuggestionSelected: (String) -> Unit,
        onChatUrlSuggestionClicked: (AutoCompleteSuggestion) -> Unit,
        onSearchForQuerySubmitted: (String) -> Unit,
        onShowSuggestions: (RecyclerView.Adapter<*>) -> Unit,
        onClearSuggestions: (Boolean) -> Unit,
    )

    fun asView(): View
}

@InjectWith(ViewScope::class)
class NativeInputModeWidget @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : InputModeWidget(context, attrs, defStyle, R.layout.view_native_input_mode_switch_widget), NativeInputWidget, NativeInputHost {

    @Inject
    lateinit var viewModelFactory: ViewViewModelFactory

    private val viewModel: NativeInputModeWidgetViewModel by lazy {
        ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[NativeInputModeWidgetViewModel::class.java]
    }

    @Inject
    lateinit var duckChatFeature: DuckChatFeature

    @Inject
    lateinit var dispatchers: DispatcherProvider

    @Inject
    lateinit var chatSuggestionsBinder: NativeInputChatSuggestionsBinder

    @Inject
    lateinit var nativeInputStateProvider: NativeInputStateProvider

    private var activeTabId: String? = null

    private var tabCountLiveData: LiveData<Int>? = null
    private var tabCountObserver: Observer<Int>? = null
    private var submitButtons: InputScreenButtons? = null
    private var floatingButtons: InputScreenButtons? = null
    private var floatingSubmitContainer: ViewGroup? = null
    private var chatStateJob: Job? = null
    private var chatSuggestionsSettingJob: Job? = null
    private var chatSuggestionsJob: Job? = null
    private var tierJob: Job? = null
    private var nativeInputStateJob: Job? = null
    private var pluginsJob: Job? = null
    private var modelPickerEnabledJob: Job? = null
    private var modelPickerEnabledSource: Flow<Boolean>? = null
    private var modelPickerView: ModelPicker? = null
    private var optionsView: OptionsView? = null
    private var chatSuggestionsUserEnabled: Boolean = true
    private var isStreaming: Boolean = false
    private var attachmentLimitExceeded: Boolean = false
    private var hasAttachments: Boolean = false
    private var supportsUpload: Boolean = true
    private var nativeInputState: NativeInputState? = null
    private var chatSuggestionsBinding: NativeInputChatSuggestionsBinder.Binding? = null
    private var onShowSuggestions: ((RecyclerView.Adapter<*>) -> Unit)? = null
    private var onClearSuggestions: ((Boolean) -> Unit)? = null
    private var voiceSearchAvailable: Boolean = false
    private var voiceChatAvailable: Boolean = false
    private var widgetRoot: View? = null
    override var onStopTapped: (() -> Unit)? = null
    override var onImageClick: (() -> Unit)? = null
    override var onVoiceSearchClick: (() -> Unit)? = null
        set(value) {
            field = value
            voiceHostButtons()?.onVoiceSearchClick = value
            onVoiceClick = value
        }
    override var onVoiceChatClick: (() -> Unit)? = null
        set(value) {
            field = value
            voiceHostButtons()?.onVoiceChatClick = value
        }
    override var onPaidTierChanged: ((Boolean) -> Unit)? = null
        set(value) {
            field = value
            if (value != null && isAttachedToWindow) observeTier()
        }
    override var onAttachmentChooserStateChanged: ((Boolean) -> Unit)? = null
    override var isModelMenuVisible: Boolean = false
        private set

    private var pendingCameraCaptureCallback: ((ValueCallback<Array<Uri>>) -> Unit)? = null
    private var pendingFilePickerCallback: ((ValueCallback<Array<Uri>>, List<String>) -> Unit)? = null
    private var pendingIsDuckAiMode: Boolean = false

    // True when this widget instance hosts the contextual sheet. Set in configureContextual();
    // never reset. Used to prevent the shared per-tab NativeInputStateProvider from leaking
    // BROWSER-state mutations from the main widget into the contextual UI: both widgets
    // observe the same `tabId` slot, so the contextual widget can briefly receive a state
    // with inputContext=BROWSER and toggleVisible=true/false that would otherwise show the
    // toggle row and reset the parent card's corners.
    private var isContextualWidget: Boolean = false

    // Held during the enter animation so padding/bottom-row compute as if focused; without
    // it they'd flip from 4dp→8dp after focusInput and look like a second step.
    private var previewEnterFocus = false

    private var attachmentView: AttachmentView? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        setupPlugins()
        observeModelPickerEnabledSource()
        applyNativeStyling()
        observeChatState()
        observeChatSuggestionsEnabled()
        observeNativeInputState()
        if (onPaidTierChanged != null) observeTier()
    }

    private fun setupPlugins() {
        pluginsJob?.cancel()
        val scope = findViewTreeLifecycleOwner()?.lifecycleScope ?: return
        pluginsJob = scope.launch {
            launch {
                viewModel.plugins.collect { plugins ->
                    for (plugin in plugins) {
                        val container = findViewById<FrameLayout?>(plugin.containerId) ?: continue
                        val pluginView = plugin.createView(context, this@NativeInputModeWidget)
                        container.removeAllViews()
                        container.addView(pluginView)
                        if (plugin.containerId != R.id.startChatContainer) {
                            container.isVisible = isChatTabSelected()
                        }
                        if (pluginView is ModelPicker) {
                            modelPickerView = pluginView
                            // Apply the current enabled state. The host may have set it
                            // before plugins were created.
                            pluginView.setPickerEnabled(viewModel.modelPickerEnabled.value)
                        }
                        if (pluginView is OptionsView) {
                            optionsView = pluginView
                        }
                        wirePluginView(pluginView, scope)
                    }
                    optionsView?.updateCapabilitiesFrom(modelPickerView)
                }
            }
            launch {
                viewModel.commands.collect { command ->
                    when (command) {
                        is NativeInputModeWidgetViewModel.Command.UpdatePluginVisibility -> {
                            for (containerId in command.containerIds) {
                                if (containerId == R.id.startChatContainer) continue
                                findViewById<FrameLayout?>(containerId)?.isVisible = command.visible
                            }
                            findViewById<FrameLayout?>(R.id.attachmentsContainer)?.isVisible =
                                command.visible && hasAttachments
                        }
                    }
                }
            }
            launch {
                viewModel.modelPickerEnabled.collect { enabled ->
                    modelPickerView?.setPickerEnabled(enabled)
                }
            }
        }
    }

    // This function will be removed with the new plugin architecture
    private fun wirePluginView(pluginView: View, scope: CoroutineScope) {
        if (pluginView is AttachmentView) {
            attachmentView = pluginView
            pluginView.onCameraCaptureRequested = pendingCameraCaptureCallback
            pluginView.onFilePickerRequested = pendingFilePickerCallback
            pluginView.bind(scope, viewModelFactory)
            pluginView.setDuckAiMode(pendingIsDuckAiMode)
        }
        (pluginView as? ModelPicker)?.let { picker ->
            picker.onMenuShown = { isModelMenuVisible = true }
            picker.onMenuDismissed = { isModelMenuVisible = false }
            picker.onModelSelected = {
                optionsView?.updateCapabilitiesFrom(picker)
            }
        }
    }

    override fun bindAttachmentCallbacks(
        onCameraCaptureRequested: (ValueCallback<Array<Uri>>) -> Unit,
        onFilePickerRequested: (ValueCallback<Array<Uri>>, List<String>) -> Unit,
    ) {
        pendingCameraCaptureCallback = onCameraCaptureRequested
        pendingFilePickerCallback = onFilePickerRequested
        attachmentView?.onCameraCaptureRequested = onCameraCaptureRequested
        attachmentView?.onFilePickerRequested = onFilePickerRequested
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        chatStateJob?.cancel()
        chatStateJob = null
        chatSuggestionsSettingJob?.cancel()
        chatSuggestionsSettingJob = null
        tierJob?.cancel()
        tierJob = null
        nativeInputStateJob?.cancel()
        nativeInputStateJob = null
        pluginsJob?.cancel()
        pluginsJob = null
        modelPickerEnabledJob?.cancel()
        modelPickerEnabledJob = null
        modelPickerView = null
        optionsView = null
        widgetRoot = null
        tearDownChatSuggestions()
    }

    override fun setWidgetRootView(view: View) {
        widgetRoot = view
    }

    private fun observeChatState() {
        var isFocussed = false

        chatStateJob?.cancel()
        chatStateJob = viewModel.chatState
            .drop(1)
            .onEach { state ->
                setChatStreaming(state == ChatState.STREAMING || state == ChatState.LOADING)
                when (state) {
                    ChatState.HIDE -> {
                        isFocussed = hasInputFocus()
                        (context as? Activity)?.hideKeyboard()
                        clearInputFocus()
                        widgetRoot?.visibility = GONE
                    }
                    ChatState.SHOW -> {
                        widgetRoot?.visibility = VISIBLE
                        if (isFocussed) {
                            requestInputFocus()
                            (context as? Activity)?.showKeyboard(inputField)
                        }
                    }
                    ChatState.READY -> {
                        widgetRoot?.visibility = VISIBLE
                    }
                    else -> {}
                }
            }
            .launchIn(findViewTreeLifecycleOwner()?.lifecycleScope ?: return)
    }

    private fun applyNativeStyling() {
        setBackgroundColor(Color.TRANSPARENT)
        hideInputFieldBackground()
        removeMargins()
        applyTrailingButtonMargin()
        prepareSubmitButtons()
        configureMainButtonsVisibility()
        configureBottomRowFocusVisibility()
        inputField.doOnTextChanged { _, _, _, _ ->
            updateSendButtonVisibility()
            updateVoiceButtonVisibility()
            updateNewLineButtonVisibility()
        }
    }

    private fun configureBottomRowFocusVisibility() {
        updateBottomRowVisibility()
        applyVerticalPaddingForFocus()
        inputField.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            // Toggle visibility is intentionally NOT updated here: it's owned by applyState
            // (state-driven) and by NativeInputManager.setToggleVisible (keyboard-visibility
            // driven on duck.ai). Re-evaluating it from the focus listener would race with
            // setToggleVisible — e.g. re-show the toggle after the keyboard has been dismissed.
            // Only animate on focus-gain — focus-loss pairs with an instant setToggleVisible hide,
            // and animating the padding shrink afterwards looks like a two-step collapse.
            if (hasFocus) beginFocusTransition()
            updateBottomRowVisibility()
            applyVerticalPaddingForFocus()
            if (!hasFocus && isDuckAiPageContext()) {
                hideKeyboard()
            }
        }
    }

    private fun beginFocusTransition() {
        val root = parent as? ViewGroup ?: return
        TransitionManager.beginDelayedTransition(
            root,
            AutoTransition().apply { duration = FOCUS_TRANSITION_DURATION_MS },
        )
    }

    private fun updateBottomRowVisibility() {
        val bottomRow = findViewById<View?>(R.id.inputModeWidgetBottomRow) ?: return
        val visible = isChatTabSelected() && (inputField.hasFocus() || previewEnterFocus || isStreaming)
        bottomRow.visibility = if (visible) VISIBLE else GONE
    }

    private fun updateToggleVisibilityForState() {
        if (isDuckAiPageContext()) return
        applyToggleVisibility(nativeInputState?.toggleVisible ?: false)
    }

    private fun applyToggleVisibility(visible: Boolean) {
        // Contextual sheet always hides the toggle row, regardless of what state the per-tab
        // store emits — the main widget can publish toggleVisible=true for the same tabId.
        val effective = visible && !isContextualWidget
        findViewById<View?>(R.id.inputModeSwitchRow)?.visibility = if (effective) VISIBLE else GONE
    }

    private fun applyVerticalPaddingForFocus() {
        // 4dp when minimized, 8dp when expanded on focus. The browser omnibar with the toggle
        // disabled stays minimized regardless of focus; everywhere else (duck.ai omnibar,
        // duck.ai contextual, browser omnibar with toggle enabled) expands on focus.
        val isBrowserOmnibarMinimized = nativeInputState?.let {
            it.inputContext == NativeInputState.InputContext.BROWSER && !it.toggleVisible
        } ?: true
        val expanded = !isBrowserOmnibarMinimized && (inputField.hasFocus() || previewEnterFocus)
        val verticalPadAttr = if (expanded) {
            com.duckduckgo.mobile.android.R.dimen.keyline_2
        } else {
            com.duckduckgo.mobile.android.R.dimen.keyline_1
        }
        val verticalPad = resources.getDimensionPixelSize(verticalPadAttr)
        setPadding(paddingLeft, verticalPad, paddingRight, verticalPad)
    }

    private fun isDuckAiPageContext(): Boolean = nativeInputState?.let {
        it.inputContext == NativeInputState.InputContext.DUCK_AI ||
            it.inputContext == NativeInputState.InputContext.DUCK_AI_CONTEXTUAL
    } ?: false

    override fun setVoiceSearchAvailable(available: Boolean) {
        voiceSearchAvailable = available
        updateVoiceButtonVisibility()
    }

    override fun setVoiceChatAvailable(available: Boolean) {
        voiceChatAvailable = available
        updateVoiceButtonVisibility()
    }

    private fun updateVoiceButtonVisibility() {
        val isBlank = inputField.text.isNullOrBlank() && !hasAttachments
        setVoiceButtonVisible(voiceSearchAvailable && isBlank)
        val host = voiceHostButtons()
        host?.setVoiceSearchVisible(false)
        host?.setVoiceChatVisible(voiceChatAvailable && isBlank && !isStreaming)
    }

    private fun updateSendButtonVisibility() {
        val hasContent = isStreaming || inputField.text.isNotBlank() || hasAttachments
        val visible = isChatTabSelected() && hasContent
        submitButtons?.setSendButtonVisible(visible)
        if (!isStreaming) {
            submitButtons?.setSendButtonEnabled(hasContent && !attachmentLimitExceeded)
        }
    }

    private fun updateNewLineButtonVisibility() {
        val isBrowserContext = nativeInputState?.inputContext == NativeInputState.InputContext.BROWSER
        val hasText = inputField.text.isNotBlank()
        val visible = isBrowserContext && isChatTabSelected() && hasText && !isStreaming
        floatingButtons?.setNewLineButtonVisible(visible)
    }

    private fun applyState(state: NativeInputState) {
        val contextChanged = nativeInputState?.inputContext != state.inputContext
        nativeInputState = state
        findViewById<TabLayout?>(R.id.inputModeSwitch)?.let { toggle ->
            setToggleMatchParent()
            updateToggleVisibility(toggle, state)
        }
        updateBackButtons(state)
        updateBottomRowVisibility()
        applyVerticalPaddingForFocus()
        updateNewLineButtonVisibility()
        applyOmnibarShape()
        if (contextChanged && isChatTabSelected()) {
            inputField.applyChatInputType()
            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).restartInput(inputField)
        }
    }

    private fun updateSelectedTab(toggle: TabLayout, state: NativeInputState) {
        val targetIndex = if (state.toggleSelection == NativeInputState.ToggleSelection.DUCK_AI) 1 else 0
        if (toggle.selectedTabPosition != targetIndex) {
            toggle.getTabAt(targetIndex)?.select()
        }
    }

    private fun updateToggleVisibility(toggle: TabLayout, state: NativeInputState) {
        // Always sync the TabLayout selection to state.toggleSelection so a tab switch (which
        // resets the stored selection via configure()) doesn't leave the previous tab's position
        // showing. updateSelectedTab is a no-op when current and target match; the resulting
        // onTabSelected listener call is gated by pushToggleSelectionIfUserDriven which guards
        // against feedback loops.
        updateSelectedTab(toggle, state)
        updateToggleVisibilityForState()
    }

    private fun updateBackButtons(state: NativeInputState) {
        findViewById<View?>(R.id.inputModeWidgetBack)?.visibility =
            if (state.shouldShowToggleRowBack()) VISIBLE else GONE
        findViewById<View?>(R.id.inputModeUnifiedBack)?.visibility =
            if (state.shouldShowCardRowBack()) VISIBLE else GONE
        findViewById<View?>(R.id.inputModeWidgetBack)?.setBackgroundResource(
            com.duckduckgo.mobile.android.R.drawable.selectable_circular_container_ripple,
        )
    }

    private fun removeMargins() {
        findViewById<EditText?>(R.id.inputField)?.updateLayoutParams<MarginLayoutParams> {
            marginStart = 0
        }
        findViewById<FrameLayout?>(R.id.inputScreenButtonsContainer)?.updateLayoutParams<MarginLayoutParams> {
            marginEnd = 0
        }
        findViewById<FrameLayout?>(R.id.attachButtonContainer)?.updateLayoutParams<MarginLayoutParams> {
            marginStart = 0
        }
    }

    private fun applyTrailingButtonMargin() {
        findViewById<View?>(R.id.inputModeWidgetLayout)?.updateLayoutParams<MarginLayoutParams> {
            marginEnd = resources.getDimensionPixelSize(R.dimen.inputScreenOmnibarCardMarginHorizontal)
        }
    }

    private fun prepareSubmitButtons() {
        configureSubmitButtons()
        submitButtons?.setSendButtonVisible(false)
        findViewById<FrameLayout?>(R.id.inputScreenButtonsContainer)?.visibility = VISIBLE
    }

    private fun hideInputFieldBackground() {
        findViewById<View?>(R.id.backgroundLayer)?.setBackgroundColor(Color.TRANSPARENT)
        findViewById<MaterialCardView?>(R.id.inputModeWidgetCard)?.apply {
            setCardBackgroundColor(Color.TRANSPARENT)
            strokeWidth = 0
            cardElevation = 0f
            elevation = 0f
            outlineProvider = null
        }
    }

    private fun setToggleMatchParent() {
        findViewById<TabLayout?>(R.id.inputModeSwitch)?.let { toggle ->
            // The toggle now lives inside the card's vertical LinearLayout with match_parent
            // width and 8dp horizontal margins, so it fills the card width automatically.
            toggle.tabMode = TabLayout.MODE_FIXED
            toggle.tabGravity = TabLayout.GRAVITY_FILL
        }
    }

    private fun configureMainButtonsVisibility() {
        val toggle = findViewById<TabLayout?>(R.id.inputModeSwitch) ?: return
        applyTabUi()
        toggle.addOnTabSelectedListener(
            object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) {
                    applyTabUi()
                    pushToggleSelectionIfUserDriven()
                    viewModel.updatePluginContainerVisibility(isChatTabSelected())
                }
                override fun onTabUnselected(tab: TabLayout.Tab) {}
                override fun onTabReselected(tab: TabLayout.Tab) {
                    applyTabUi()
                    pushToggleSelectionIfUserDriven()
                    viewModel.updatePluginContainerVisibility(isChatTabSelected())
                }
            },
        )
    }

    // Only propagate the TabLayout selection into NativeInputState when the toggle row is actually
    // visible — i.e. the change came from a user tap. Programmatic selections from paths like
    // applyDefaultTogglePosition() can run for SEARCH_ONLY users (toggle hidden); pushing those
    // would make the chat-tab selection sticky in state and prevent updateSelectedTab() from
    // reverting it to the context-derived default when applyState() next fires.
    private fun pushToggleSelectionIfUserDriven() {
        if (nativeInputState?.toggleVisible != true) return
        val selection = if (isChatTabSelected()) {
            NativeInputState.ToggleSelection.DUCK_AI
        } else {
            NativeInputState.ToggleSelection.SEARCH
        }
        viewModel.setToggleSelection(selection)
    }

    override fun EditText.applyChatInputType() {
        hint = context.getString(R.string.native_input_chat_hint)
        val isDuckAiChat = isDuckAiPageContext()
        val actionFlag = if (isDuckAiChat) EditorInfo.IME_ACTION_NONE else EditorInfo.IME_ACTION_GO
        imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING or actionFlag
        val baseInputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_AUTO_CORRECT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        setRawInputType(
            if (isDuckAiChat) baseInputType or InputType.TYPE_TEXT_FLAG_MULTI_LINE else baseInputType,
        )
        setHorizontallyScrolling(false)
    }

    override fun shouldSubmitOnHardwareEnter(): Boolean =
        !(isDuckAiPageContext() && isChatTabSelected())

    override fun submitMessage(message: String?) {
        if (message == null && isChatTabSelected() && attachmentLimitExceeded) {
            logcat { "submitMessage: suppressed - attachment limit exceeded" }
            return
        }
        if (message == null && inputField.text.isNullOrBlank() && hasAttachments && isChatTabSelected()) {
            onChatSent?.invoke("")
            inputField.clearFocus()
        } else {
            super.submitMessage(message)
        }
    }

    override fun focusInput(activity: Activity?) {
        inputField.requestFocus()
        activity?.showKeyboard(inputField)
    }

    override fun hasInputFocus(): Boolean = inputField.hasFocus()

    override fun requestInputFocus() {
        if (!inputField.hasFocus()) {
            inputField.requestFocus()
        }
    }

    override fun beginEnterAnimationPreview() {
        if (inputField.hasFocus()) return
        // State observation is async; apply it synchronously so toggle-row visibility etc. are
        // in their final positions before the enter animation measures the widget.
        if (nativeInputState == null) {
            activeTabId?.let { nativeInputStateProvider.stateForTab(it).value }?.let(::applyState)
        }
        previewEnterFocus = true
        updateBottomRowVisibility()
        applyVerticalPaddingForFocus()
        // Bottom omnibar only: trigger the IME slide-up now so it overlaps the enter animation —
        // the activity shrinks (and the widget's bottom-anchored FrameLayout slides up) alongside
        // the widget's expansion instead of pushing it up afterwards as a second step. Top omnibar
        // keyboard position is independent of the widget, so its show stays deferred to focusInput
        // in onEnterComplete.
        //
        // Side effect to be aware of: Activity.showKeyboard() calls inputField.requestFocus()
        // under the hood, so the input field gains focus here — ~200ms earlier than in top mode
        // (where focus is set in onEnterComplete via focusInput). The onFocusChangeListener will
        // therefore fire synchronously inside showKeyboard(). The asymmetry is intentional, but
        // it does mean any cancel path has to undo focus + IME for bottom mode (see the manager's
        // animateEnter.onCancel).
        if (isWidgetBottom()) {
            showKeyboard()
        }
    }

    override fun endEnterAnimationPreview() {
        if (!previewEnterFocus) return
        previewEnterFocus = false
        updateBottomRowVisibility()
        applyVerticalPaddingForFocus()
    }

    override fun hideKeyboard() {
        (context as? Activity)?.hideKeyboard(inputField)
    }

    override fun showKeyboard() {
        (context as? Activity)?.showKeyboard(inputField)
    }

    override fun selectChatTab() {
        val toggle = findViewById<TabLayout?>(R.id.inputModeSwitch) ?: return
        if (toggle.selectedTabPosition != 1) {
            toggle.getTabAt(1)?.select()
        }
    }

    override fun applyDefaultTogglePosition() {
        doOnAttach {
            if (!duckChatFeature.rememberTogglePosition().isEnabled()) return@doOnAttach
            findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
                // Wait for the first state emission so we know whether the toggle row is even shown.
                // For SEARCH_ONLY users (input-screen setting off) the toggle is hidden, and flipping
                // selectedTabPosition to 1 would leak chat-tab UI (plugin containers, chat styling)
                // into the search-only experience.
                val state = viewModel.state.firstOrNull() ?: return@launch
                if (!state.toggleVisible) return@launch
                val position = viewModel.defaultTogglePosition.firstOrNull() ?: return@launch
                val resolved = if (position == DefaultTogglePosition.LAST_USED) {
                    DefaultTogglePosition.fromName(viewModel.lastUsedTogglePosition.firstOrNull())
                } else {
                    position
                }
                if (resolved == DefaultTogglePosition.DUCK_AI) {
                    selectChatTab()
                }
            }
        }
    }

    override fun saveLastUsedTogglePosition(isChat: Boolean) {
        if (!duckChatFeature.rememberTogglePosition().isEnabled()) return
        val position = if (isChat) DefaultTogglePosition.DUCK_AI else DefaultTogglePosition.SEARCH
        findViewTreeLifecycleOwner()?.lifecycleScope?.launch(dispatchers.io()) {
            viewModel.saveLastUsedTogglePosition(position.name)
        }
    }

    override fun hideMainButtons() {
        setMainButtonsVisible(false)
    }

    override fun setToggleVisible(visible: Boolean) {
        val isVisible = visible && (nativeInputState?.toggleVisible ?: false)
        suspendLayoutTransitions {
            applyToggleVisibility(isVisible)
        }
    }

    private inline fun suspendLayoutTransitions(block: () -> Unit) {
        val ancestors = generateSequence(this as ViewGroup) { it.parent as? ViewGroup }
            .take(3)
            .toList()
        val saved = ancestors.map { it.layoutTransition }
        ancestors.forEach { it.layoutTransition = null }
        block()
        ancestors.zip(saved).forEach { (vg, lt) -> vg.layoutTransition = lt }
    }

    override fun getSelectedModelId(): String? = viewModel.getSelectedModelId()

    override fun getResolvedReasoningEffort(): String? = viewModel.getResolvedReasoningEffort()

    override fun getSelectedTool(): String? = viewModel.getSelectedTool()

    override fun clearSelectedTool() {
        optionsView?.clearSelection()
    }

    override fun setModelPickerEnabled(enabled: Boolean) {
        viewModel.setModelPickerEnabled(enabled)
    }

    override fun bindModelPickerEnabledSource(source: Flow<Boolean>) {
        modelPickerEnabledSource = source
        if (isAttachedToWindow) observeModelPickerEnabledSource()
    }

    private fun observeModelPickerEnabledSource() {
        val source = modelPickerEnabledSource ?: return
        val scope = findViewTreeLifecycleOwner()?.lifecycleScope ?: return
        modelPickerEnabledJob?.cancel()
        modelPickerEnabledJob = source
            .distinctUntilChanged()
            .onEach { viewModel.setModelPickerEnabled(it) }
            .launchIn(scope)
    }

    override fun getImageAttachmentsJson(): JSONArray? = attachmentView?.getImageAttachmentsJson()

    override fun getFileAttachmentsJson(): JSONArray? = attachmentView?.getFileAttachmentsJson()

    override fun clearAttachments() {
        attachmentView?.clearAttachments()
    }

    override fun storePendingPrompt(query: String) {
        val images = attachmentView?.getImageAttachments()?.map {
            PendingNativeImage(base64Data = it.base64Data, format = it.format)
        } ?: emptyList()
        val files = attachmentView?.getFileAttachments()?.map {
            PendingNativeFile(base64Data = it.base64Data, fileName = it.fileName, mimeType = it.mimeType)
        } ?: emptyList()
        viewModel.storePendingPrompt(query, getSelectedModelId(), getResolvedReasoningEffort(), getSelectedTool(), images, files)
        attachmentView?.clearAttachmentsForNewChat()
        optionsView?.clearSelection()
    }

    override fun configure(tabId: String, isDuckAiMode: Boolean, isBottom: Boolean) {
        activeTabId = tabId
        pendingIsDuckAiMode = isDuckAiMode
        doOnAttach {
            viewModel.configure(tabId, isDuckAiMode, isBottom)
            if (isDuckAiMode) selectChatTab()
            attachmentView?.setDuckAiMode(isDuckAiMode)
        }
    }

    override fun configureContextual(tabId: String) {
        activeTabId = tabId
        pendingIsDuckAiMode = true
        isContextualWidget = true
        doOnAttach {
            viewModel.configureContextual(tabId)
            selectChatTab()
            attachmentView?.setDuckAiMode(true)
        }
    }

    override fun isWidgetBottom(): Boolean = nativeInputState?.isBottom ?: false

    override fun setWidgetPosition(isBottom: Boolean) {
        doOnAttach {
            viewModel.setWidgetPosition(isBottom)
        }
    }

    private fun applyOmnibarShape() {
        // The contextual sheet's parent card has a top-only rounded shape applied by
        // ContextualNativeInputManager.applyCardShape(); never overwrite it from here. The
        // shared per-tab state store can briefly emit a BROWSER state with toggleVisible=false
        // (e.g. SEARCH_ONLY users when the main widget publishes first), which would otherwise
        // fall through and reset card.radius to largeShapeCornerRadius on all four corners.
        if (isContextualWidget) return
        val state = nativeInputState ?: return
        if (state.inputContext == NativeInputState.InputContext.DUCK_AI_CONTEXTUAL) return
        if (state.isBottom) return
        if (state.toggleVisible) return
        val card = parent as? MaterialCardView ?: return
        card.radius = card.resources.getDimension(com.duckduckgo.mobile.android.R.dimen.largeShapeCornerRadius)
        val targetTopMargin = card.resources.getDimensionPixelSize(com.duckduckgo.mobile.android.R.dimen.omnibarCardMarginTop)
        val targetHorizontalMargin = card.resources.getDimensionPixelSize(com.duckduckgo.mobile.android.R.dimen.omnibarCardMarginHorizontal)
        (card.layoutParams as? MarginLayoutParams)?.let { lp ->
            lp.topMargin = targetTopMargin - card.paddingTop
            lp.marginStart = targetHorizontalMargin - card.paddingLeft
            lp.marginEnd = targetHorizontalMargin - card.paddingRight
            card.layoutParams = lp
        }
    }

    override fun bindInputEvents(
        onSearchTextChanged: (String) -> Unit,
        onSearchSubmitted: (String) -> Unit,
        onChatSubmitted: (String) -> Unit,
    ) {
        this.onSearchTextChanged = onSearchTextChanged
        this.onSearchSelected = { _ ->
            onSearchTextChanged(text)
        }
        this.onSearchSent = onSearchSubmitted
        this.onChatSent = onChatSubmitted
    }

    override fun bindTabCount(
        lifecycleOwner: LifecycleOwner,
        tabCount: LiveData<Int>,
    ) {
        tabCountObserver?.let { existing ->
            tabCountLiveData?.removeObserver(existing)
        }
        val observer = Observer<Int> { count ->
            setTabCount(count)
        }
        tabCountLiveData = tabCount
        tabCountObserver = observer
        tabCount.observe(lifecycleOwner, observer)
        setTabCount(tabCount.value ?: 0)
    }

    override fun bindChatSuggestions(
        lifecycleOwner: LifecycleOwner,
        onChatSuggestionSelected: (String) -> Unit,
        onChatUrlSuggestionClicked: (AutoCompleteSuggestion) -> Unit,
        onSearchForQuerySubmitted: (String) -> Unit,
        onShowSuggestions: (RecyclerView.Adapter<*>) -> Unit,
        onClearSuggestions: (Boolean) -> Unit,
    ) {
        this.onShowSuggestions = onShowSuggestions
        this.onClearSuggestions = onClearSuggestions

        // Lazy: bindChatSuggestions runs before attach, so @Inject fields and the ViewModel
        // aren't ready yet. showSuggestions() only fires post-attach.
        fun ensureBinding(): NativeInputChatSuggestionsBinder.Binding {
            return chatSuggestionsBinding ?: chatSuggestionsBinder.create(
                onChatSuggestionSelected = { suggestion ->
                    viewModel.fireChatHistorySelectedPixel(suggestion.pinned)
                    onChatSuggestionSelected(viewModel.buildChatSuggestionUrl(suggestion))
                },
                onChatUrlSuggestionClicked = { suggestion ->
                    viewModel.fireChatUrlSuggestionPixel(suggestion)
                    onChatUrlSuggestionClicked(suggestion)
                },
                onSearchForQuerySubmitted = onSearchForQuerySubmitted,
            ).also { chatSuggestionsBinding = it }
        }

        fun showSuggestions(query: String) {
            fetchChatTabSuggestions(lifecycleOwner, query, ensureBinding())
        }

        val previousOnSearchSelected = this.onSearchSelected
        this.onSearchSelected = { animate ->
            hideChatSuggestions(hideList = false)
            previousOnSearchSelected?.invoke(animate)
        }

        val previousOnChatSelected = this.onChatSelected
        this.onChatSelected = { animate ->
            previousOnChatSelected?.invoke(animate)
            showSuggestions(text)
        }

        this.onChatTextChanged = { text ->
            showSuggestions(text)
        }
    }

    private fun tearDownChatSuggestions() {
        hideChatSuggestions(hideList = true)
        chatSuggestionsBinding = null
        onShowSuggestions = null
        onClearSuggestions = null
    }

    override fun asView(): View = this

    fun setTabCount(count: Int) {
        tabSwitcherButton.count = count
    }

    private fun observeChatSuggestionsEnabled() {
        chatSuggestionsSettingJob?.cancel()
        chatSuggestionsSettingJob = viewModel.chatSuggestionsUserEnabled
            .onEach { enabled -> chatSuggestionsUserEnabled = enabled }
            .launchIn(findViewTreeLifecycleOwner()?.lifecycleScope ?: return)
    }

    private fun observeNativeInputState() {
        nativeInputStateJob?.cancel()
        val lifecycleOwner = findViewTreeLifecycleOwner() ?: return
        // Use viewModel.state for the widget's own UI rather than the provider: the provider's
        // per-tab StateFlow is seeded with NativeInputState.zero() so a subscriber that attaches
        // before the widget VM has published the real state would briefly receive the placeholder
        // (SEARCH_AND_DUCK_AI by default) and render the wrong UI for users in SEARCH_ONLY mode.
        // viewModel.state combines activeTabId.filterNotNull() and only emits real values.
        nativeInputStateJob = viewModel.state
            .onEach(::applyState)
            .launchIn(lifecycleOwner.lifecycleScope)
    }

    private fun observeTier() {
        tierJob?.cancel()
        tierJob = viewModel.isPaidTier
            .onEach { hasDuckAiPlus -> onPaidTierChanged?.invoke(hasDuckAiPlus) }
            .launchIn(findViewTreeLifecycleOwner()?.lifecycleScope ?: return)
    }

    private fun fetchChatTabSuggestions(
        lifecycleOwner: LifecycleOwner,
        query: String,
        binding: NativeInputChatSuggestionsBinder.Binding,
    ) {
        chatSuggestionsJob?.cancel()
        chatSuggestionsJob = lifecycleOwner.lifecycleScope.launch {
            val result = viewModel.fetchChatTabSuggestions(query, chatSuggestionsUserEnabled)
            // Defer the adapter swap until the chat history arrives, so the RecyclerView
            // lays out from position 0 with chat history on top and avoids repositioning.
            binding.submit(result, query) { hasContent ->
                if (hasContent) {
                    onShowSuggestions?.invoke(binding.adapter)
                } else {
                    onClearSuggestions?.invoke(true)
                }
            }
        }
    }

    private fun hideChatSuggestions(hideList: Boolean) {
        chatSuggestionsJob?.cancel()
        chatSuggestionsBinding?.clear()
        viewModel.cancelChatSuggestions()
        onClearSuggestions?.invoke(hideList)
    }

    private fun setChatStreaming(streaming: Boolean) {
        isStreaming = streaming
        configureSubmitButtons()
        if (streaming) {
            submitButtons?.showStopButton()
        } else {
            submitButtons?.showSendButton()
            applyTabUi()
            floatingSubmitContainer?.visibility = if (attachmentLimitExceeded) GONE else VISIBLE
        }
        updateBottomRowVisibility()
        updateSendButtonVisibility()
        updateVoiceButtonVisibility()
        updateNewLineButtonVisibility()
    }

    private fun applyTabUi() {
        val toggle = findViewById<TabLayout?>(R.id.inputModeSwitch) ?: return
        val isChatTab = toggle.selectedTabPosition == 1
        setImageButtonVisible(isChatTab && supportsUpload)
        submitButtons?.setSendButtonIcon(R.drawable.ic_arrow_right_24_inverted)
        if (isChatTab) {
            inputField.minLines = 1
            inputField.maxLines = MAX_LINES
        }
        updateSendButtonVisibility()
        updateNewLineButtonVisibility()
        updateBottomRowVisibility()
    }

    override fun setImageButtonVisible(visible: Boolean) {
        findViewById<FrameLayout?>(R.id.attachButtonContainer)?.isVisible = visible
    }

    override fun setFloatingSubmitContainer(container: ViewGroup) {
        floatingSubmitContainer = container
    }

    override fun submit() {
        // in Duck.ai mode we treat this as submitting prompts.
        // In non-Duck.ai mode we treat this as starting a chat with or without a prompt.
        if (!submitAsChat()) viewModel.openNewChat()
    }

    override fun showAttachmentChooser(showing: Boolean) {
        onAttachmentChooserStateChanged?.invoke(showing)
    }

    override fun attachmentChanged(
        hasAttachments: Boolean,
        limitExceeded: Boolean,
        supportsUpload: Boolean,
    ) {
        val hadLimitError = attachmentLimitExceeded
        attachmentLimitExceeded = limitExceeded
        this.hasAttachments = hasAttachments
        this.supportsUpload = supportsUpload
        setImageButtonVisible(isChatTabSelected() && supportsUpload)
        if (hadLimitError != attachmentLimitExceeded && !isStreaming) {
            floatingSubmitContainer?.visibility = if (attachmentLimitExceeded) GONE else VISIBLE
        }
        updateSendButtonVisibility()
        updateVoiceButtonVisibility()
    }

    override fun toolSelected(tool: String?) {
        viewModel.setSelectedTool(tool)
    }

    override fun showModelPicker(showing: Boolean) {
        findViewById<FrameLayout?>(R.id.modelPickerContainer)?.isVisible = showing
    }

    override fun showReasoningPicker(showing: Boolean) {
        findViewById<FrameLayout?>(R.id.reasoningModePickerContainer)?.isVisible = showing
    }

    private fun configureSubmitButtons() {
        if (submitButtons == null) {
            val bottomContainer = findViewById<FrameLayout?>(R.id.inputScreenButtonsContainer) ?: return
            val buttons = InputScreenButtons(
                context = context,
                useTopBar = false,
                layoutResId = R.layout.view_native_input_screen_buttons,
            ).apply {
                onSendClick = { submitMessage() }
                onStopClick = { this@NativeInputModeWidget.onStopTapped?.invoke() }
                setSendButtonVisible(false)
                setNewLineButtonVisible(false)
            }
            bottomContainer.addView(buttons)
            submitButtons = buttons
        }

        val floating = floatingSubmitContainer
        if (floating != null && floatingButtons == null) {
            val buttons = InputScreenButtons(
                context = context,
                useTopBar = true,
                layoutResId = R.layout.view_native_input_screen_buttons,
            ).apply {
                onNewLineClick = { printNewLine() }
                onVoiceSearchClick = this@NativeInputModeWidget.onVoiceSearchClick
                onVoiceChatClick = this@NativeInputModeWidget.onVoiceChatClick
                setSendButtonVisible(false)
                setNewLineButtonVisible(false)
            }
            floating.addView(buttons)
            floatingButtons = buttons
        }
        updateVoiceButtonVisibility()
    }

    private fun voiceHostButtons(): InputScreenButtons? = floatingButtons ?: submitButtons

    companion object {
        private const val MAX_LINES = 5
        private const val FOCUS_TRANSITION_DURATION_MS = 100L
    }
}

internal fun NativeInputState.shouldShowToggleRowBack(): Boolean =
    toggleVisible && inputContext == NativeInputState.InputContext.BROWSER

internal fun NativeInputState.shouldShowCardRowBack(): Boolean =
    !toggleVisible && inputContext == NativeInputState.InputContext.BROWSER
