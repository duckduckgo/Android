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

package com.duckduckgo.duckchat.impl.ui

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.text.InputType
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Space
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
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.utils.ViewViewModelFactory
import com.duckduckgo.common.utils.extensions.hideKeyboard
import com.duckduckgo.common.utils.extensions.showKeyboard
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.duckchat.impl.ChatState
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.inputscreen.ui.suggestions.ChatSuggestionsAdapter
import com.duckduckgo.duckchat.impl.inputscreen.ui.view.InputModeWidget
import com.duckduckgo.duckchat.impl.inputscreen.ui.view.InputScreenButtons
import com.google.android.material.card.MaterialCardView
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

interface NativeInputWidget {

    var text: String
    var onSearchSelected: ((animate: Boolean) -> Unit)?
    var onChatSelected: ((animate: Boolean) -> Unit)?
    var onClearTextTapped: (() -> Unit)?
    var onStopTapped: (() -> Unit)?
    var onVoiceSearchClick: (() -> Unit)?
    var onVoiceChatClick: (() -> Unit)?
    var onImageClick: (() -> Unit)?
    var onPaidTierChanged: ((Boolean) -> Unit)?

    fun focusInput(activity: Activity?)
    fun hasInputFocus(): Boolean
    fun clearInputFocus()
    fun requestInputFocus()
    fun selectAllText()
    fun hideKeyboard()
    fun selectChatTab()
    fun isChatTabSelected(): Boolean
    fun hideMainButtons()
    fun setVoiceSearchAvailable(available: Boolean)
    fun setVoiceChatAvailable(available: Boolean)
    fun submitMessage(message: String?)
    fun setImageButtonVisible(visible: Boolean)
    fun setToggleVisible(visible: Boolean)
    fun setFloatingSubmitContainer(container: ViewGroup)
    fun getSelectedModelId(): String?
    fun isModelMenuVisible(): Boolean
    fun storePendingPrompt(query: String)
    fun configure(isDuckAiMode: Boolean, isBottom: Boolean)
    fun isWidgetBottom(): Boolean
    fun setWidgetPosition(isBottom: Boolean)
    fun setWidgetRootView(view: View)

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
        onShowSuggestions: (ChatSuggestionsAdapter) -> Unit,
        onClearSuggestions: (Boolean) -> Unit,
    )

    fun asView(): View
}

@InjectWith(ViewScope::class)
class NativeInputModeWidget @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : InputModeWidget(context, attrs, defStyle), NativeInputWidget {

    @Inject
    lateinit var viewModelFactory: ViewViewModelFactory

    private val viewModel: NativeInputModeWidgetViewModel by lazy {
        ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[NativeInputModeWidgetViewModel::class.java]
    }

    private var tabCountLiveData: LiveData<Int>? = null
    private var tabCountObserver: Observer<Int>? = null
    private var submitButtons: InputScreenButtons? = null
    private var floatingSubmitContainer: ViewGroup? = null
    private var chatStateJob: Job? = null
    private var chatSuggestionsSettingJob: Job? = null
    private var chatSuggestionsJob: Job? = null
    private var tierJob: Job? = null
    private var nativeInputStateJob: Job? = null
    private var chatSuggestionsUserEnabled: Boolean = true
    private var isStreaming: Boolean = false
    private var nativeInputState: NativeInputState = NativeInputState(
        inputMode = NativeInputState.InputMode.SEARCH_ONLY,
        inputContext = NativeInputState.InputContext.BROWSER,
    )
    private var chatSuggestionsAdapter: ChatSuggestionsAdapter? = null
    private var onShowSuggestions: ((ChatSuggestionsAdapter) -> Unit)? = null
    private var onClearSuggestions: ((Boolean) -> Unit)? = null
    private var voiceSearchAvailable: Boolean = false
    private var voiceChatAvailable: Boolean = false
    private var widgetRoot: View? = null
    override var onStopTapped: (() -> Unit)? = null
    override var onImageClick: (() -> Unit)? = null
    override var onVoiceSearchClick: (() -> Unit)? = null
        set(value) {
            field = value
            submitButtons?.onVoiceSearchClick = value
            onVoiceClick = value
        }
    override var onVoiceChatClick: (() -> Unit)? = null
        set(value) {
            field = value
            submitButtons?.onVoiceChatClick = value
        }
    override var onPaidTierChanged: ((Boolean) -> Unit)? = null
        set(value) {
            field = value
            if (value != null && isAttachedToWindow) observeTier()
        }

    private val imageButton: ImageView by lazy { findViewById(R.id.inputFieldImageButton) }
    private val modelPickerView: ModelPicker by lazy { findViewById<ModelPickerView>(R.id.modelPickerView) }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        imageButton.setOnClickListener { onImageClick?.invoke() }
        modelPickerView.setPickerEnabled(isChatTabSelected())
        modelPickerView.onMenuDismissed = {
            if (hasInputFocus()) {
                (context as? Activity)?.showKeyboard(inputField)
            }
        }
        applyNativeStyling()
        observeChatState()
        observeChatSuggestionsEnabled()
        observeNativeInputState()
        if (onPaidTierChanged != null) observeTier()
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
        hideBackArrow()
        hideInputFieldBackground()
        removeMargins()
        applyTrailingButtonMargin()
        prepareSubmitButtons()
        configureMainButtonsVisibility()
        inputField.doOnTextChanged { text, _, _, _ ->
            if (isChatTabSelected() && !isStreaming) {
                submitButtons?.setSendButtonEnabled(!text.isNullOrBlank())
            }
            updateSendButtonVisibility()
            updateVoiceButtonVisibility()
        }
    }

    override fun setVoiceSearchAvailable(available: Boolean) {
        voiceSearchAvailable = available
        updateVoiceButtonVisibility()
    }

    override fun setVoiceChatAvailable(available: Boolean) {
        voiceChatAvailable = available
        updateVoiceButtonVisibility()
    }

    private fun updateVoiceButtonVisibility() {
        val isBlank = inputField.text.isNullOrBlank()
        setVoiceButtonVisible(voiceSearchAvailable && isBlank)
        submitButtons?.setVoiceSearchVisible(false)
        submitButtons?.setVoiceChatVisible(voiceChatAvailable && isBlank)
    }

    private fun updateSendButtonVisibility() {
        val visible = isChatTabSelected() && (isStreaming || inputField.text.isNotBlank())
        submitButtons?.setSendButtonVisible(visible)
    }

    private fun applyState(state: NativeInputState) {
        nativeInputState = state
        val toggle = findViewById<TabLayout?>(R.id.inputModeSwitch) ?: return
        setToggleMatchParent()
        updateToggleVisibility(toggle, state)
        if (!state.toggleVisible) {
            minimize()
        }
    }

    private fun updateSelectedTab(toggle: TabLayout, state: NativeInputState) {
        val targetIndex = if (state.defaultToggleSelection == NativeInputState.ToggleSelection.DUCK_AI) 1 else 0
        if (toggle.selectedTabPosition != targetIndex) {
            toggle.getTabAt(targetIndex)?.select()
        }
    }

    private fun updateToggleVisibility(toggle: TabLayout, state: NativeInputState) {
        if (!state.toggleVisible) {
            updateSelectedTab(toggle, state)
        }
        toggle.visibility = if (state.toggleVisible) VISIBLE else GONE
    }

    private fun minimize() {
        if (floatingSubmitContainer == null) return
        findViewById<Space?>(R.id.spacer)?.updateLayoutParams<LayoutParams> { height = 0 }
        findViewById<Space?>(R.id.bottomSpacer)?.updateLayoutParams<LayoutParams> { height = 0 }
        findViewById<View?>(R.id.inputModeWidgetLayout)?.updateLayoutParams<MarginLayoutParams> { topMargin = 0 }
        getActionBarSize()?.let { minimumHeight = it }
    }

    private fun removeMargins() {
        findViewById<EditText?>(R.id.inputField)?.updateLayoutParams<MarginLayoutParams> {
            marginStart = 0
        }
        findViewById<FrameLayout?>(R.id.inputScreenButtonsContainer)?.updateLayoutParams<MarginLayoutParams> {
            marginEnd = 0
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

    private fun hideBackArrow() {
        findViewById<View?>(R.id.InputModeWidgetBack)?.visibility = GONE
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

    private fun getActionBarSize(): Int? {
        val typedValue = TypedValue()
        return if (context.theme.resolveAttribute(android.R.attr.actionBarSize, typedValue, true)) {
            TypedValue.complexToDimensionPixelSize(typedValue.data, resources.displayMetrics)
        } else {
            null
        }
    }

    private fun setToggleMatchParent() {
        findViewById<TabLayout?>(R.id.inputModeSwitch)?.let { toggle ->
            toggle.updateLayoutParams<LayoutParams> {
                width = 0
                matchConstraintDefaultWidth = LayoutParams.MATCH_CONSTRAINT_SPREAD
                constrainedWidth = false
            }
            toggle.tabMode = TabLayout.MODE_FIXED
            toggle.tabGravity = TabLayout.GRAVITY_FILL
            toggle.requestLayout()
        }
    }

    private fun configureMainButtonsVisibility() {
        val toggle = findViewById<TabLayout?>(R.id.inputModeSwitch) ?: return
        updateDuckAiSubmitButton()
        toggle.addOnTabSelectedListener(
            object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) {
                    updateDuckAiSubmitButton()
                    modelPickerView.setPickerEnabled(isChatTabSelected())
                }
                override fun onTabUnselected(tab: TabLayout.Tab) {}
                override fun onTabReselected(tab: TabLayout.Tab) {
                    updateDuckAiSubmitButton()
                    modelPickerView.setPickerEnabled(isChatTabSelected())
                }
            },
        )
    }

    override fun EditText.applyChatInputType() {
        imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        setRawInputType(
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_AUTO_CORRECT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES,
        )
        setHorizontallyScrolling(false)
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

    override fun hideKeyboard() {
        (context as? Activity)?.hideKeyboard(inputField)
    }

    override fun selectChatTab() {
        val toggle = findViewById<TabLayout?>(R.id.inputModeSwitch) ?: return
        if (toggle.selectedTabPosition != 1) {
            toggle.getTabAt(1)?.select()
        }
    }

    override fun hideMainButtons() {
        setMainButtonsVisible(false)
    }

    override fun setToggleVisible(visible: Boolean) {
        val toggle = findViewById<TabLayout?>(R.id.inputModeSwitch) ?: return
        val isVisible = visible && nativeInputState.toggleVisible
        suspendLayoutTransitions {
            toggle.visibility = if (isVisible) VISIBLE else GONE
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

    override fun getSelectedModelId(): String? = modelPickerView.getSelectedModelId()

    override fun isModelMenuVisible(): Boolean = modelPickerView.isMenuVisible()

    override fun storePendingPrompt(query: String) {
        // TODO: This should not be the widget's responsibility
        viewModel.storePendingPrompt(query, getSelectedModelId())
    }

    override fun configure(isDuckAiMode: Boolean, isBottom: Boolean) {
        viewModel.configure(isDuckAiMode, isBottom)
        viewModel.state.replayCache.lastOrNull()?.let { nativeInputState = it }
        if (isDuckAiMode) selectChatTab()
        applyOmnibarShape(isBottom)
    }

    override fun isWidgetBottom(): Boolean = nativeInputState.isBottom

    override fun setWidgetPosition(isBottom: Boolean) {
        viewModel.setWidgetPosition(isBottom)
    }

    private fun applyOmnibarShape(isBottom: Boolean) {
        if (isBottom) return
        if (nativeInputState.toggleVisible) return
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
        onShowSuggestions: (ChatSuggestionsAdapter) -> Unit,
        onClearSuggestions: (Boolean) -> Unit,
    ) {
        this.onShowSuggestions = onShowSuggestions
        this.onClearSuggestions = onClearSuggestions

        val adapter = ChatSuggestionsAdapter { suggestion ->
            onChatSuggestionSelected(viewModel.buildChatSuggestionUrl(suggestion))
        }.also { chatSuggestionsAdapter = it }

        fun showSuggestions(query: String) {
            if (!chatSuggestionsUserEnabled) {
                hideChatSuggestions(hideList = true)
                return
            }
            fetchChatSuggestions(lifecycleOwner, query, adapter)
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
        chatSuggestionsAdapter = null
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

    private fun fetchChatSuggestions(
        lifecycleOwner: LifecycleOwner,
        query: String,
        adapter: ChatSuggestionsAdapter,
    ) {
        chatSuggestionsJob?.cancel()
        chatSuggestionsJob = lifecycleOwner.lifecycleScope.launch {
            val suggestions = viewModel.fetchChatSuggestions(query)
            if (suggestions.isNotEmpty()) {
                onShowSuggestions?.invoke(adapter)
            }
            adapter.submitList(suggestions)
            if (suggestions.isEmpty()) {
                onClearSuggestions?.invoke(true)
            }
        }
    }

    private fun hideChatSuggestions(hideList: Boolean) {
        chatSuggestionsJob?.cancel()
        chatSuggestionsAdapter?.submitList(emptyList())
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
            submitButtons?.setSendButtonEnabled(inputField.text.isNotBlank())
        }
        updateSendButtonVisibility()
    }

    private fun updateDuckAiSubmitButton() {
        val toggle = findViewById<TabLayout?>(R.id.inputModeSwitch) ?: return
        val isChatTab = toggle.selectedTabPosition == 1
        setImageButtonVisible(isChatTab)
        if (isChatTab) {
            submitButtons?.setSendButtonIcon(R.drawable.ic_arrow_up_24)
            if (!isStreaming) {
                submitButtons?.setSendButtonEnabled(inputField.text.isNotBlank())
            }
            inputField.minLines = 1
            inputField.maxLines = MAX_LINES
        } else {
            submitButtons?.setSendButtonIcon(com.duckduckgo.mobile.android.R.drawable.ic_find_search_24)
        }
        updateSendButtonVisibility()
    }

    override fun setImageButtonVisible(visible: Boolean) {
        imageButton.isVisible = visible
    }

    override fun setFloatingSubmitContainer(container: ViewGroup) {
        floatingSubmitContainer = container
    }

    private fun configureSubmitButtons() {
        if (submitButtons != null) return
        val floating = floatingSubmitContainer
        val (container, useTopBar) = if (floating != null) {
            floating to true
        } else {
            (findViewById<FrameLayout?>(R.id.inputScreenButtonsContainer) ?: return) to false
        }
        val buttons = InputScreenButtons(context, useTopBar = useTopBar).apply {
            onSendClick = { submitMessage() }
            onStopClick = { this@NativeInputModeWidget.onStopTapped?.invoke() }
            onVoiceSearchClick = this@NativeInputModeWidget.onVoiceSearchClick
            onVoiceChatClick = this@NativeInputModeWidget.onVoiceChatClick
            setSendButtonVisible(false)
            setNewLineButtonVisible(false)
        }
        container.addView(buttons)
        submitButtons = buttons
        updateVoiceButtonVisibility()
    }

    companion object {
        private const val MAX_LINES = 5
    }
}
