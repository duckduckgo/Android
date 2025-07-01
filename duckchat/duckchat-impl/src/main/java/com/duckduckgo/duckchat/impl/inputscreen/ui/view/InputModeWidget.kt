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

import android.animation.ValueAnimator
import android.content.Context
import android.text.InputType
import android.transition.ChangeBounds
import android.transition.Fade
import android.transition.TransitionManager
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.annotation.IdRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.animation.addListener
import androidx.core.view.isVisible
import androidx.core.view.marginBottom
import androidx.core.view.marginEnd
import androidx.core.view.marginStart
import androidx.core.view.marginTop
import androidx.core.view.updateLayoutParams
import androidx.core.widget.doOnTextChanged
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.mobile.android.R as CommonR
import com.google.android.material.card.MaterialCardView
import com.google.android.material.tabs.TabLayout

@InjectWith(ActivityScope::class)
class InputModeWidget @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : ConstraintLayout(context, attrs, defStyle) {

    private val omnibarCardMarginHorizontal by lazy { resources.getDimensionPixelSize(CommonR.dimen.experimentalOmnibarCardMarginHorizontal) }
    private val omnibarCardMarginTop by lazy { resources.getDimensionPixelSize(CommonR.dimen.experimentalOmnibarCardMarginTop) }
    private val omnibarCardMarginBottom by lazy { resources.getDimensionPixelSize(CommonR.dimen.experimentalOmnibarCardMarginBottom) }
    private val omnibarCardFocusedMarginHorizontal by lazy {
        resources.getDimensionPixelSize(
            CommonR.dimen.experimentalOmnibarCardFocusedMarginHorizontal,
        )
    }
    private val omnibarCardFocusedMarginTop by lazy { resources.getDimensionPixelSize(CommonR.dimen.experimentalOmnibarCardFocusedMarginTop) }
    private val omnibarCardFocusedMarginBottom by lazy { resources.getDimensionPixelSize(CommonR.dimen.experimentalOmnibarCardFocusedMarginBottom) }

    private val omnibarOutlineWidth by lazy { resources.getDimensionPixelSize(CommonR.dimen.experimentalOmnibarOutlineWidth) }
    private val omnibarOutlineFocusedWidth by lazy { resources.getDimensionPixelSize(CommonR.dimen.experimentalOmnibarOutlineFocusedWidth) }

    private val omnibarCard: MaterialCardView by lazy { findViewById(R.id.inputModeWidgetCard) }
    private val omnibarContent: View by lazy { findViewById(R.id.inputModeWidgetCardContent) }

    val inputField: EditText
    val inputFieldClearText: View
    val inputModeWidgetBack: View
    val inputModeSwitch: TabLayout

    var onBack: (() -> Unit)? = null
    var onSearchSent: ((String) -> Unit)? = null
    var onChatSent: ((String) -> Unit)? = null
    var onSearchSelected: (() -> Unit)? = null
    var onChatSelected: (() -> Unit)? = null
    var onSendMessageAvailable: ((Boolean) -> Unit)? = null
        set(value) {
            field = value
            value?.invoke(inputField.text.getTextToSubmit() != null)
        }
    var onVoiceInputAllowed: ((Boolean) -> Unit)? = null

    var text: String
        get() = inputField.text.toString()
        set(value) {
            inputField.setText(value)
            inputField.setSelection(value.length)
        }

    @IdRes
    private var contentId: Int = View.NO_ID
    private var focusAnimator: ValueAnimator? = null
    private var originalText: String? = null
    private var hasTextChangedFromOriginal = false

    init {
        LayoutInflater.from(context).inflate(R.layout.view_input_mode_switch_layout, this, true)

        inputField = findViewById(R.id.inputField)
        inputFieldClearText = findViewById(R.id.inputFieldClearText)
        inputModeWidgetBack = findViewById(R.id.InputModeWidgetBack)
        inputModeSwitch = findViewById(R.id.inputModeSwitch)

        configureClickListeners()
        configureInputBehavior()
        configureTabBehavior()
        applyModeSpecificInputBehaviour(isSearchTab = true)
    }

    fun provideInitialText(text: String) {
        originalText = text
        inputField.setText(text)
    }

    private fun configureClickListeners() {
        inputFieldClearText.setOnClickListener {
            inputField.text.clear()
            inputField.setSelection(0)
            inputField.scrollTo(0, 0)
            beginChangeBoundsTransition()
        }
        inputModeWidgetBack.setOnClickListener { onBack?.invoke() }
    }

    private fun configureInputBehavior() = with(inputField) {
        maxLines = MAX_LINES
        setHorizontallyScrolling(false)
        setRawInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)

        setOnFocusChangeListener { _, hasFocus ->
            animateOmnibarFocusedState(hasFocus)
        }

        setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                submitMessage()
                true
            } else {
                false
            }
        }

        doOnTextChanged { text, _, _, _ ->
            if (!hasTextChangedFromOriginal) {
                hasTextChangedFromOriginal = text != originalText
            }
            onVoiceInputAllowed?.invoke(!hasTextChangedFromOriginal || inputField.text.isBlank())

            onSendMessageAvailable?.invoke(inputField.text.getTextToSubmit() != null)

            val isNullOrEmpty = text.isNullOrEmpty()
            fade(inputFieldClearText, !isNullOrEmpty)

            if (isNullOrEmpty && inputField.minLines > 1) {
                inputField.post {
                    inputField.minLines = if (inputModeSwitch.selectedTabPosition == 0) SEARCH_MIN_LINES else DUCK_CHAT_MIN_LINES
                }
            }
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
                minLines = SEARCH_MIN_LINES
                hint = context.getString(R.string.duck_chat_search_or_type_url)
                imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING or EditorInfo.IME_ACTION_GO
            } else {
                minLines = DUCK_CHAT_MIN_LINES
                hint = context.getString(R.string.duck_chat_ask_anything)
                imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING or EditorInfo.IME_ACTION_GO
            }
        }
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).restartInput(inputField)
    }

    private fun beginChangeBoundsTransition() {
        (parent as? ViewGroup ?: this).let { root ->
            val pager = root.findViewById<View>(contentId).apply {
                isTransitionGroup = true
            }
            TransitionManager.beginDelayedTransition(
                root,
                ChangeBounds().apply {
                    excludeChildren(pager, true)
                },
            )
        }
    }

    fun submitMessage(message: String? = null) {
        val text = message?.also(inputField::setText) ?: inputField.text
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

    fun setContentId(@IdRes id: Int) {
        contentId = id
    }

    fun animateOmnibarFocusedState(focused: Boolean) {
        focusAnimator?.cancel()

        val startTop = omnibarCard.marginTop
        val startBottom = omnibarCard.marginBottom
        val startStart = omnibarCard.marginStart
        val startEnd = omnibarCard.marginEnd
        val startStroke = omnibarCard.strokeWidth

        val endTop = if (focused) omnibarCardFocusedMarginTop else omnibarCardMarginTop
        val endBottom = if (focused) omnibarCardFocusedMarginBottom else omnibarCardMarginBottom
        val endStart = if (focused) omnibarCardFocusedMarginHorizontal else omnibarCardMarginHorizontal
        val endEnd = if (focused) omnibarCardFocusedMarginHorizontal else omnibarCardMarginHorizontal
        val endStroke = if (focused) omnibarOutlineFocusedWidth else omnibarOutlineWidth

        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = DEFAULT_ANIMATION_DURATION
            interpolator = DecelerateInterpolator()
            addUpdateListener { valueAnimator ->
                val fraction = valueAnimator.animatedFraction
                (omnibarCard.layoutParams as MarginLayoutParams).apply {
                    leftMargin = (startStart + (endStart - startStart) * fraction).toInt()
                    topMargin = (startTop + (endTop - startTop) * fraction).toInt()
                    rightMargin = (startEnd + (endEnd - startEnd) * fraction).toInt()
                    bottomMargin = (startBottom + (endBottom - startBottom) * fraction).toInt()
                }.also { omnibarCard.layoutParams = it }
                omnibarCard.strokeWidth = (startStroke + (endStroke - startStroke) * fraction).toInt()
            }
            addListener(
                onStart = { lockContentDimensions() },
                onEnd = { if (!focused) unlockContentDimensions() },
                onCancel = { removeAllListeners() },
            )
            start()
            focusAnimator = this
        }
    }

    private fun lockContentDimensions() {
        omnibarContent.updateLayoutParams {
            width = omnibarContent.measuredWidth
            height = ViewGroup.LayoutParams.WRAP_CONTENT
        }
    }

    private fun unlockContentDimensions() {
        omnibarContent.updateLayoutParams {
            width = ViewGroup.LayoutParams.MATCH_PARENT
            height = ViewGroup.LayoutParams.WRAP_CONTENT
        }
    }

    fun printNewLine() {
        val currentText = inputField.text.toString()
        val selectionStart = inputField.selectionStart
        val selectionEnd = inputField.selectionEnd
        val newText = currentText.substring(0, selectionStart) + "\n" + currentText.substring(selectionEnd)
        inputField.setText(newText)
        inputField.setSelection(selectionStart + 1)
    }

    private fun CharSequence.getTextToSubmit(): CharSequence? {
        val text = this.trim()
        return text.ifBlank { null }
    }

    companion object {
        private const val DEFAULT_ANIMATION_DURATION = 300L
        private const val FADE_DURATION = 150L
        private const val MAX_LINES = 8
        private const val SEARCH_MIN_LINES = 2
        private const val DUCK_CHAT_MIN_LINES = 2
    }
}
