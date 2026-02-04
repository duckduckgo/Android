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

package com.duckduckgo.duckchat.impl.inputscreen.ui.view

import android.content.Context
import android.os.Build
import android.text.Editable
import android.text.InputType
import android.text.Spanned
import android.text.style.CharacterStyle
import android.text.style.ImageSpan
import android.text.style.ParagraphStyle
import android.text.style.URLSpan
import android.transition.ChangeBounds
import android.transition.Fade
import android.transition.TransitionManager
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isNotEmpty
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.widget.doAfterTextChanged
import androidx.core.widget.doOnTextChanged
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.browser.ui.tabs.TabSwitcherButton
import com.duckduckgo.common.ui.view.addBottomShadow
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.view.toPx
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName
import com.duckduckgo.duckchat.impl.pixel.inputScreenPixelsModeParam
import com.google.android.material.card.MaterialCardView
import com.google.android.material.tabs.TabLayout
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject
import kotlin.math.roundToInt

@InjectWith(ViewScope::class)
class InputModeWidget @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : ConstraintLayout(context, attrs, defStyle) {
    @Inject
    lateinit var pixel: Pixel

    val inputField: EditText
    private val inputFieldClearText: View
    private val inputModeWidgetBack: View
    private val inputModeSwitch: TabLayout
    private val inputModeWidgetCard: MaterialCardView
    private val inputScreenButtonsContainer: FrameLayout
    private val inputModeMainButtonsContainer: View
    private val inputModeWidgetLayout: View
    val tabSwitcherButton: TabSwitcherButton
    private val menuButton: View
    private val menuIconImageView: ImageView
    private val fireButton: View
    private val voiceInputButton: View
    private var bottomButtonsMode: Boolean = false

    private val inputModeCardExtendedEndMargin: Int by lazy {
        resources.getDimensionPixelSize(R.dimen.inputScreenOmnibarCardExtendedMarginHorizontal)
    }

    private val inputModeCardEndMargin: Int by lazy {
        resources.getDimensionPixelSize(R.dimen.inputScreenOmnibarCardMarginHorizontal)
    }

    var onBack: (() -> Unit)? = null
    var onSearchSent: ((String) -> Unit)? = null
    var onChatSent: ((String) -> Unit)? = null
    var onSearchSelected: (() -> Unit)? = null
    var onChatSelected: (() -> Unit)? = null
    var onSubmitMessageAvailable: ((Boolean) -> Unit)? = null
        set(value) {
            field = value
            value?.invoke(inputField.text.getTextToSubmit() != null)
        }
    var onVoiceInputAllowed: ((Boolean) -> Unit)? = null
    var onSearchTextChanged: ((String) -> Unit)? = null
    var onChatTextChanged: ((String) -> Unit)? = null
    var onInputFieldClicked: (() -> Unit)? = null
    var onVoiceClick: (() -> Unit)? = null

    var onTabTapped: ((index: Int) -> Unit)? = null
    var onFireButtonTapped: (() -> Unit)? = null
    var onTabSwitcherTapped: (() -> Unit)? = null
    var onMenuTapped: (() -> Unit)? = null
    var onClearTextTapped: (() -> Unit)? = null

    var text: String
        get() = inputField.text.toString()
        set(value) {
            inputField.setText(value)
            inputField.setSelection(value.length)
        }

    var canExpand: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                beginChangeBoundsTransition()
                val isChatMode = inputModeSwitch.selectedTabPosition == 1
                val chatMin = if (bottomButtonsMode) 1 else CHAT_MIN_LINES
                inputField.maxLines = when {
                    value -> MAX_LINES
                    isChatMode -> chatMin
                    else -> 1
                }
                inputField.setHorizontallyScrolling(!value)
                inputField.post {
                    inputField.requestLayout()
                }
            }
        }

    private var originalText: String? = null
    private var hasTextChangedFromOriginal = false

    init {
        LayoutInflater.from(context).inflate(R.layout.view_input_mode_switch_widget, this, true)

        inputField = findViewById(R.id.inputField)
        inputFieldClearText = findViewById(R.id.inputFieldClearText)
        inputModeWidgetBack = findViewById(R.id.InputModeWidgetBack)
        inputModeSwitch = findViewById(R.id.inputModeSwitch)
        inputModeWidgetCard = findViewById(R.id.inputModeWidgetCard)
        menuButton = findViewById(R.id.inputFieldBrowserMenu)
        menuIconImageView = findViewById(R.id.browserMenuImageView)
        fireButton = findViewById(R.id.inputFieldFireButton)
        tabSwitcherButton = findViewById(R.id.inputFieldTabsMenu)
        voiceInputButton = findViewById(R.id.inputFieldVoiceInputButton)
        inputScreenButtonsContainer = findViewById(R.id.inputScreenButtonsContainer)
        inputModeMainButtonsContainer = findViewById(R.id.inputModeMainButtonsContainer)
        inputModeWidgetLayout = findViewById(R.id.inputModeWidgetLayout)

        configureClickListeners()
        configureInputBehavior()
        configureTabBehavior()
        applyModeSpecificInputBehaviour(isSearchTab = true)
        configureShadow()
    }

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()
    }

    private fun provideInitialText(text: String) {
        originalText = text
        this.text = text
    }

    fun provideInitialInputState(
        text: String,
        canShowMainButtons: Boolean,
    ) {
        if (text.isNotEmpty()) {
            provideInitialText(text)
        }

        if (canShowMainButtons && text.isNotEmpty()) {
            inputModeMainButtonsContainer.show()
        } else {
            inputModeMainButtonsContainer.gone()
        }
    }

    fun initOnSearch() {
        onSearchSelected?.invoke()
    }

    fun initOnChat() {
        onChatSelected?.invoke()
    }

    fun clearInputFocus() {
        inputField.clearFocus()
    }

    fun getSelectedTabPosition(): Int {
        return inputModeSwitch.selectedTabPosition
    }

    private fun configureClickListeners() {
        inputFieldClearText.setOnClickListener {
            inputField.text.clear()
            inputField.setSelection(0)
            inputField.scrollTo(0, 0)

            onClearTextTapped?.invoke()
        }
        inputModeWidgetBack.setOnClickListener {
            onBack?.invoke()

            val params = inputScreenPixelsModeParam(isSearchMode = inputModeSwitch.selectedTabPosition == 0)
            pixel.fire(DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_BACK_BUTTON_PRESSED, parameters = params)
        }
        inputField.setOnClickListener {
            onInputFieldClicked?.invoke()
        }
        menuButton.setOnClickListener {
            onMenuTapped?.invoke()
        }
        tabSwitcherButton.setOnClickListener {
            onTabSwitcherTapped?.invoke()
        }
        fireButton.setOnClickListener {
            onFireButtonTapped?.invoke()
        }
        voiceInputButton.setOnClickListener {
            onVoiceClick?.invoke()
        }
        addTabClickListeners()
    }

    private fun addTabClickListeners() {
        val tabStrip = inputModeSwitch.getChildAt(0) as? ViewGroup ?: return

        repeat(inputModeSwitch.tabCount) { index ->
            inputModeSwitch.getTabAt(index)?.let { tab ->
                tabStrip.getChildAt(index)?.setOnClickListener {
                    onTabTapped?.invoke(index)
                    if (inputModeSwitch.selectedTabPosition != index) {
                        tab.select()
                    }
                }
            }
        }
    }

    private fun configureInputBehavior() =
        with(inputField) {
            setHorizontallyScrolling(true)

            setOnEditorActionListener { _, actionId, keyEvent ->
                val isHardwareEnter =
                    (keyEvent?.keyCode == KeyEvent.KEYCODE_ENTER || keyEvent?.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) &&
                        keyEvent.action == KeyEvent.ACTION_DOWN

                if (actionId == EditorInfo.IME_ACTION_GO || isHardwareEnter) {
                    submitMessage()

                    val params = inputScreenPixelsModeParam(isSearchMode = inputModeSwitch.selectedTabPosition == 0)
                    pixel.fire(DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_KEYBOARD_GO_PRESSED, parameters = params)
                    true
                } else {
                    false
                }
            }

            doOnTextChanged { text, _, _, _ ->
                if (!hasTextChangedFromOriginal) {
                    hasTextChangedFromOriginal = text != originalText
                }

                val textToSubmit = inputField.text.getTextToSubmit()
                onSubmitMessageAvailable?.invoke(textToSubmit != null)
                onVoiceInputAllowed?.invoke(!hasTextChangedFromOriginal || inputField.text.isBlank())

                when (inputModeSwitch.selectedTabPosition) {
                    0 -> onSearchTextChanged?.invoke(textToSubmit?.toString().orEmpty())
                    1 -> onChatTextChanged?.invoke(textToSubmit?.toString().orEmpty())
                }

                val isNullOrEmpty = text.isNullOrEmpty()
                inputFieldClearText.isVisible = !isNullOrEmpty
            }

            doAfterTextChanged { text ->
                text?.let {
                    removeFormatting(text)
                }
            }
        }

    private fun removeFormatting(text: Editable) {
        val spans =
            buildList<Any> {
                addAll(text.getSpans(0, text.length, CharacterStyle::class.java))
                addAll(text.getSpans(0, text.length, ParagraphStyle::class.java))
                addAll(text.getSpans(0, text.length, URLSpan::class.java))
                addAll(text.getSpans(0, text.length, ImageSpan::class.java))
            }.filter { span ->
                (text.getSpanFlags(span) and Spanned.SPAN_COMPOSING) == 0
            }

        if (spans.isNotEmpty()) {
            spans.forEach(text::removeSpan)
            // Remove trailing newlines
            text.delete(text.indexOfLast { it != '\n' } + 1, text.length)
        }
    }

    private fun configureTabBehavior() {
        inputModeSwitch.addOnTabSelectedListener(
            object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) {
                    val isSearchTab = tab.position == 0
                    applyModeSpecificInputBehaviour(isSearchTab = isSearchTab)
                    when (tab.position) {
                        0 -> onSearchSelected?.invoke()
                        1 -> onChatSelected?.invoke()
                    }
                }

                override fun onTabUnselected(tab: TabLayout.Tab?) {}

                override fun onTabReselected(tab: TabLayout.Tab?) {}
            },
        )
    }

    private fun applyModeSpecificInputBehaviour(isSearchTab: Boolean) {
        inputField.apply {
            if (isSearchTab) {
                hint = context.getString(R.string.input_screen_search_hint)
                imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING or EditorInfo.IME_ACTION_GO
                setRawInputType(
                    InputType.TYPE_CLASS_TEXT or
                        InputType.TYPE_TEXT_VARIATION_URI or
                        InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
                )
                minLines = 1
                maxLines = if (canExpand) MAX_LINES else 1
            } else {
                hint = context.getString(R.string.input_screen_chat_hint)
                imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING or EditorInfo.IME_ACTION_GO
                setRawInputType(
                    InputType.TYPE_CLASS_TEXT or
                        InputType.TYPE_TEXT_FLAG_AUTO_CORRECT or
                        InputType.TYPE_TEXT_FLAG_CAP_SENTENCES,
                )
                val chatMin = if (bottomButtonsMode) 1 else CHAT_MIN_LINES
                minLines = chatMin
                maxLines = if (canExpand) MAX_LINES else chatMin
            }
            setHorizontallyScrolling(!canExpand)
            post {
                requestLayout()
            }
        }
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).restartInput(inputField)
    }

    private fun beginChangeBoundsTransition() {
        (parent as? ViewGroup ?: this).let { root ->
            TransitionManager.beginDelayedTransition(
                root,
                ChangeBounds().apply {
                    duration = EXPAND_COLLAPSE_TRANSITION_DURATION
                    excludeTarget(R.id.inputScreenButtonsContainer, true)
                    excludeTarget(inputScreenButtonsContainer, true)
                },
            )
        }
    }

    private fun beginInputScreenButtonsVisibilityTransition() {
        (parent as? ViewGroup ?: this).let { root ->
            TransitionManager.beginDelayedTransition(
                root,
                ChangeBounds().apply {
                    duration = EXPAND_COLLAPSE_TRANSITION_DURATION
                },
            )
        }
    }

    fun submitMessage(message: String? = null) {
        val text = message?.also { text = it } ?: inputField.text
        val textToSubmit = text.getTextToSubmit()?.toString()
        if (textToSubmit != null) {
            if (inputModeSwitch.selectedTabPosition == 0) {
                onSearchSent?.invoke(textToSubmit)
            } else {
                onChatSent?.invoke(textToSubmit)
            }
            inputField.clearFocus()
        }
    }

    fun selectTab(index: Int) {
        inputModeSwitch.post {
            inputModeSwitch.getTabAt(index)?.select()
        }
    }

    fun isChatTabSelected(): Boolean = inputModeSwitch.selectedTabPosition == 1

    fun setScrollPosition(
        position: Int,
        positionOffset: Float,
    ) {
        inputModeSwitch.setScrollPosition(position, positionOffset, false)
    }

    private fun fade(
        view: View,
        visible: Boolean,
        duration: Long = FADE_DURATION,
    ) {
        if (view.isVisible == visible) return
        (view.parent as? ViewGroup)?.let { root ->
            TransitionManager.beginDelayedTransition(
                root,
                Fade().apply { this.duration = duration },
            )
        }
        view.isVisible = visible
    }

    fun printNewLine() {
        val currentText = inputField.text.toString()
        val selectionStart = inputField.selectionStart
        val selectionEnd = inputField.selectionEnd
        val newText = currentText.substring(0, selectionStart) + "\n" + currentText.substring(selectionEnd)
        text = newText
        inputField.setSelection(selectionStart + 1)
    }

    fun selectAllText() {
        inputField.selectAll()
    }

    fun setInputScreenBottomButtons(inputScreenButtons: InputScreenButtons) {
        bottomButtonsMode = true
        inputScreenButtonsContainer.addView(inputScreenButtons)
        inputFieldClearText.updateLayoutParams<MarginLayoutParams> {
            // align the clear text button with the center of the submit button
            marginEnd = 4f.toPx(context).roundToInt()
        }
        inputScreenButtonsContainer.visibility = VISIBLE
    }

    fun setInputScreenButtonsVisible(buttonsVisible: Boolean) {
        if (inputScreenButtonsContainer.isNotEmpty()) {
            if (bottomButtonsMode) {
                inputScreenButtonsContainer.visibility = VISIBLE
                return
            }
            val targetVisibility = if (buttonsVisible) VISIBLE else INVISIBLE
            if (inputScreenButtonsContainer.visibility != targetVisibility) {
                beginInputScreenButtonsVisibilityTransition()
                inputScreenButtonsContainer.visibility = targetVisibility
            }
        }
    }

    private fun CharSequence.getTextToSubmit(): CharSequence? {
        val text = this.trim()
        return text.ifBlank { null }
    }

    private fun configureShadow() {
        if (Build.VERSION.SDK_INT >= 28) {
            inputModeWidgetCard.addBottomShadow()
        }
    }

    fun setVoiceButtonVisible(visible: Boolean) {
        voiceInputButton.isVisible = visible
    }

    fun setMenuIcon(@DrawableRes resId: Int) {
        ContextCompat.getDrawable(context, resId)?.let {
            menuIconImageView.setImageDrawable(it)
        }
    }

    fun setMainButtonsVisible(
        mainButtonsVisible: Boolean,
    ) {
        fade(inputModeMainButtonsContainer, mainButtonsVisible)

        inputModeWidgetLayout.updateLayoutParams<MarginLayoutParams> {
            marginEnd = if (mainButtonsVisible) {
                inputModeCardEndMargin
            } else {
                inputModeCardExtendedEndMargin
            }
            marginStart = inputModeCardExtendedEndMargin
        }
    }

    companion object {
        private const val FADE_DURATION = 150L
        private const val EXPAND_COLLAPSE_TRANSITION_DURATION = 150L
        private const val MAX_LINES = 5
        private const val CHAT_MIN_LINES = 2
    }
}
