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
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.core.net.toUri
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.utils.extensions.hideKeyboard
import com.duckduckgo.common.utils.extensions.showKeyboard
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.duckchat.impl.ChatState
import com.duckduckgo.duckchat.impl.DuckChatInternal
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.inputscreen.ui.suggestions.ChatSuggestion
import com.duckduckgo.duckchat.impl.inputscreen.ui.suggestions.ChatSuggestionsAdapter
import com.duckduckgo.duckchat.impl.inputscreen.ui.suggestions.reader.ChatSuggestionsReader
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
    var onSearchSelected: (() -> Unit)?
    var onChatSelected: (() -> Unit)?
    var onClearTextTapped: (() -> Unit)?
    var onStopTapped: (() -> Unit)?
    var onVoiceClick: (() -> Unit)?

    fun focusInput(activity: Activity?)
    fun hasInputFocus(): Boolean
    fun clearInputFocus()
    fun requestInputFocus()
    fun selectAllText()
    fun hideKeyboard()
    fun selectChatTab()
    fun isChatTabSelected(): Boolean
    fun hideMainButtons()
    fun setVoiceButtonVisible(visible: Boolean)
    fun submitMessage(message: String?)

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
    lateinit var duckChatInternal: DuckChatInternal

    @Inject
    lateinit var chatSuggestionsReader: ChatSuggestionsReader

    private var tabCountLiveData: LiveData<Int>? = null
    private var tabCountObserver: Observer<Int>? = null
    private var submitButtons: InputScreenButtons? = null
    private var chatStateJob: Job? = null
    private var chatSuggestionsSettingJob: Job? = null
    private var chatSuggestionsJob: Job? = null
    private var chatSuggestionsUserEnabled: Boolean = true
    private var chatSuggestionsAdapter: ChatSuggestionsAdapter? = null
    private var onShowSuggestions: ((ChatSuggestionsAdapter) -> Unit)? = null
    private var onClearSuggestions: ((Boolean) -> Unit)? = null
    override var onStopTapped: (() -> Unit)? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        applyNativeStyling()
        observeChatState()
        observeChatSuggestionsEnabled()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        chatStateJob?.cancel()
        chatStateJob = null
        chatSuggestionsSettingJob?.cancel()
        chatSuggestionsSettingJob = null
        tearDownChatSuggestions()
    }

    private fun observeChatState() {
        var isFocussed = false

        chatStateJob?.cancel()
        chatStateJob = duckChatInternal.chatState
            .drop(1)
            .onEach { state ->
                setChatStreaming(state == ChatState.STREAMING)
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

    private val widgetRoot: View?
        get() {
            var v: View? = this
            while (v != null) {
                val p = v.parent
                if (p is androidx.coordinatorlayout.widget.CoordinatorLayout) return v
                v = p as? View
            }
            return null
        }

    private fun applyNativeStyling() {
        setBackgroundColor(Color.TRANSPARENT)
        hideBackArrow()
        hideInputFieldBackground()
        if (duckChatInternal.isEnabled()) {
            setToggleMatchParent()
        } else {
            hideToggle()
        }
        prepareSubmitButtons()
        configureMainButtonsVisibility()
    }

    private fun prepareSubmitButtons() {
        ensureSubmitButtons()
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

    private fun hideToggle() {
        val toggle = findViewById<TabLayout?>(R.id.inputModeSwitch) ?: return
        toggle.getTabAt(0)?.select()
        toggle.visibility = GONE
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
                }
                override fun onTabUnselected(tab: TabLayout.Tab) {}
                override fun onTabReselected(tab: TabLayout.Tab) {
                    updateDuckAiSubmitButton()
                }
            },
        )
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

    override fun bindInputEvents(
        onSearchTextChanged: (String) -> Unit,
        onSearchSubmitted: (String) -> Unit,
        onChatSubmitted: (String) -> Unit,
    ) {
        this.onSearchTextChanged = onSearchTextChanged
        this.onSearchSelected = {
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
            onChatSuggestionSelected(buildChatUrl(suggestion))
        }.also { chatSuggestionsAdapter = it }

        fun showSuggestions(query: String) {
            if (!chatSuggestionsUserEnabled) {
                hideChatSuggestions(hideList = true)
                return
            }
            fetchChatSuggestions(lifecycleOwner, query, adapter)
        }

        val previousOnSearchSelected = this.onSearchSelected
        this.onSearchSelected = {
            hideChatSuggestions(hideList = false)
            previousOnSearchSelected?.invoke()
        }

        val previousOnChatSelected = this.onChatSelected
        this.onChatSelected = {
            previousOnChatSelected?.invoke()
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
        chatSuggestionsSettingJob = duckChatInternal.observeChatSuggestionsUserSettingEnabled()
            .onEach { enabled -> chatSuggestionsUserEnabled = enabled }
            .launchIn(findViewTreeLifecycleOwner()?.lifecycleScope ?: return)
    }

    private fun fetchChatSuggestions(
        lifecycleOwner: LifecycleOwner,
        query: String,
        adapter: ChatSuggestionsAdapter,
    ) {
        chatSuggestionsJob?.cancel()
        chatSuggestionsJob = lifecycleOwner.lifecycleScope.launch {
            val suggestions = runCatching { chatSuggestionsReader.fetchSuggestions(query) }.getOrDefault(emptyList())
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
        chatSuggestionsReader.tearDown()
        onClearSuggestions?.invoke(hideList)
    }

    private fun buildChatUrl(suggestion: ChatSuggestion): String {
        return duckChatInternal.getDuckChatUrl("", false)
            .toUri()
            .buildUpon()
            .appendQueryParameter(CHAT_ID_PARAM, suggestion.chatId)
            .build()
            .toString()
    }

    private fun setChatStreaming(streaming: Boolean) {
        ensureSubmitButtons()
        if (streaming) {
            submitButtons?.setStopButton()
        } else {
            submitButtons?.clearStopButton(com.duckduckgo.mobile.android.R.drawable.ic_arrow_right_24)
        }
    }

    private fun updateDuckAiSubmitButton() {
        val toggle = findViewById<TabLayout?>(R.id.inputModeSwitch) ?: return
        val isChatTab = toggle.selectedTabPosition == 1
        if (isChatTab) {
            submitButtons?.setSendButtonIcon(com.duckduckgo.mobile.android.R.drawable.ic_arrow_right_24)
            submitButtons?.setSendButtonVisible(true)
            if (!canExpand) {
                inputField.minLines = 1
                inputField.maxLines = 1
            } else {
                inputField.minLines = 1
            }
        } else {
            submitButtons?.setSendButtonIcon(com.duckduckgo.mobile.android.R.drawable.ic_find_search_24)
            submitButtons?.setSendButtonVisible(false)
        }
    }

    private fun ensureSubmitButtons() {
        if (submitButtons != null) return
        val container = findViewById<FrameLayout?>(R.id.inputScreenButtonsContainer) ?: return
        val buttons = InputScreenButtons(context, useTopBar = false).apply {
            onSendClick = { submitMessage() }
            onStopClick = { this@NativeInputModeWidget.onStopTapped?.invoke() }
            setSendButtonVisible(false)
            setNewLineButtonVisible(false)
            setVoiceButtonVisible(false)
        }
        container.addView(buttons)
        submitButtons = buttons
    }

    companion object {
        private const val CHAT_ID_PARAM = "chatID"
    }
}
