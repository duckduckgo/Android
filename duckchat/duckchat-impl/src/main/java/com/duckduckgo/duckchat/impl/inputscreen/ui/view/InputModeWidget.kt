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
import android.text.InputType
import android.transition.ChangeBounds
import android.transition.Fade
import android.transition.TransitionManager
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.annotation.IdRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.view.addBottomShadow
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

    val inputField: EditText
    private val inputFieldClearText: View
    private val inputModeWidgetBack: View
    private val inputModeSwitch: TabLayout
    private val inputModeWidgetCard: MaterialCardView

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
    private var originalText: String? = null
    private var hasTextChangedFromOriginal = false

    init {
        LayoutInflater.from(context).inflate(R.layout.view_input_mode_switch_widget, this, true)

        inputField = findViewById(R.id.inputField)
        inputFieldClearText = findViewById(R.id.inputFieldClearText)
        inputModeWidgetBack = findViewById(R.id.InputModeWidgetBack)
        inputModeSwitch = findViewById(R.id.inputModeSwitch)
        inputModeWidgetCard = findViewById(R.id.inputModeWidgetCard)

        configureClickListeners()
        configureInputBehavior()
        configureTabBehavior()
        applyModeSpecificInputBehaviour(isSearchTab = true)
        configureShadow()
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

    private fun configureShadow() {
        if (Build.VERSION.SDK_INT >= 28) {
            inputModeWidgetCard.addBottomShadow(
                shadowSizeDp = 12f,
                offsetYDp = 3f,
                insetDp = 3f,
                shadowColor = context.getColor(CommonR.color.background_omnibar_shadow),
            )
        }
    }

    companion object {
        private const val FADE_DURATION = 150L
        private const val MAX_LINES = 8
        private const val SEARCH_MIN_LINES = 1
        private const val DUCK_CHAT_MIN_LINES = 1
    }
}
