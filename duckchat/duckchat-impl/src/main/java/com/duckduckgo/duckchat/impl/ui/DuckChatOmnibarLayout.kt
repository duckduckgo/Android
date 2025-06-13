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

import android.animation.ValueAnimator
import android.content.Context
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.animation.addListener
import androidx.core.view.isVisible
import androidx.core.view.marginBottom
import androidx.core.view.marginEnd
import androidx.core.view.marginStart
import androidx.core.view.marginTop
import androidx.core.view.updateLayoutParams
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.mobile.android.R as CommonR
import com.google.android.material.card.MaterialCardView
import com.google.android.material.tabs.TabLayout

@InjectWith(ActivityScope::class)
class DuckChatOmnibarLayout @JvmOverloads constructor(
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

    private val omnibarCard: MaterialCardView by lazy { findViewById(R.id.duckChatControls) }
    private val omnibarContent: View by lazy { findViewById(R.id.duckChatControlsContent) }

    private var focusAnimator: ValueAnimator? = null
    private var isStopButtonVisible = false
    var enableFireButton = false
    var enableNewChatButton = false

    val duckChatFireButton: View
    val duckChatInput: EditText
    val duckChatSend: View
    val duckChatClearText: View
    val duckChatNewChat: View
    val duckChatStop: View
    val duckChatControls: View
    val duckChatBack: View
    val duckChatTabLayout: TabLayout

    private var originalStartMargin: Int = 0

    var onFire: (() -> Unit)? = null
    var onNewChat: (() -> Unit)? = null
    var onStop: (() -> Unit)? = null
    var onBack: (() -> Unit)? = null
    var onSearchSent: ((String) -> Unit)? = null
    var onDuckChatSent: ((String) -> Unit)? = null

    private var selectionStart = 0
    private var selectionEnd = 0

    init {
        LayoutInflater.from(context).inflate(R.layout.view_duck_chat_omnibar, this, true)

        duckChatFireButton = findViewById(R.id.duckChatFireButton)
        duckChatInput = findViewById(R.id.duckChatInput)
        duckChatSend = findViewById(R.id.duckChatSend)
        duckChatClearText = findViewById(R.id.duckChatClearText)
        duckChatNewChat = findViewById(R.id.duckChatNewChat)
        duckChatStop = findViewById(R.id.duckChatStop)
        duckChatControls = findViewById(R.id.duckChatControls)
        duckChatBack = findViewById(R.id.duckChatBack)
        duckChatTabLayout = findViewById(R.id.duckChatTabLayout)

        (duckChatInput.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
            originalStartMargin = params.marginStart
        }

        duckChatFireButton.setOnClickListener { onFire?.invoke() }
        duckChatNewChat.setOnClickListener { onNewChat?.invoke() }
        duckChatSend.setOnClickListener { submitMessage() }
        duckChatClearText.setOnClickListener {
            duckChatInput.setText("")
            duckChatInput.setSelection(0)
            duckChatInput.scrollTo(0, 0)
        }
        duckChatStop.setOnClickListener {
            onStop?.invoke()
            hideStopButton()
        }
        duckChatBack.setOnClickListener { onBack?.invoke() }

        applyInputBehavior(duckChatTabLayout.selectedTabPosition)
        duckChatTabLayout.addOnTabSelectedListener(
            object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) {
                    duckChatSend.isVisible = tab.position != 0
                    applyInputBehavior(tab.position)
                }
                override fun onTabUnselected(tab: TabLayout.Tab?) {}
                override fun onTabReselected(tab: TabLayout.Tab?) {}
            },
        )

        duckChatInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                duckChatFireButton.isVisible = false
                duckChatNewChat.isVisible = false
            }
            animateOmnibarFocusedState(hasFocus || isStopButtonVisible)
            applyLeftInputMargin(originalStartMargin)
        }

        duckChatInput.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    duckChatSend.isVisible = !s.isNullOrEmpty() && duckChatTabLayout.selectedTabPosition == 1
                    duckChatClearText.isVisible = !s.isNullOrEmpty()
                    duckChatFireButton.isVisible = false
                    duckChatNewChat.isVisible = false
                }
                override fun afterTextChanged(s: Editable?) = Unit
            },
        )

        duckChatInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                submitMessage()
                true
            } else {
                false
            }
        }

        duckChatFireButton.isVisible = false
        duckChatNewChat.isVisible = false
    }

    private fun applyInputBehavior(tabPosition: Int) {
        selectionStart = duckChatInput.selectionStart
        selectionEnd = duckChatInput.selectionEnd

        val isSearchTab = tabPosition == 0

        duckChatInput.apply {
            maxLines = MAX_LINES
            setHorizontallyScrolling(false)
            setRawInputType(InputType.TYPE_CLASS_TEXT)

            imeOptions = if (isSearchTab) {
                EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_ACTION_GO
            } else {
                EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_ACTION_NONE
            }

            text?.length.let { length ->
                setSelection(
                    selectionStart.coerceIn(0, length),
                    selectionEnd.coerceIn(0, length),
                )
            }
        }
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).restartInput(duckChatInput)
        applyLeftInputMargin(originalStartMargin)
    }

    private fun applyLeftInputMargin(margin: Int) {
        (duckChatInput.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
            params.marginStart = margin
            duckChatInput.layoutParams = params
        }
    }

    private fun submitMessage() {
        val message = duckChatInput.text.toString().trim()
        if (message.isNotEmpty()) {
            if (duckChatTabLayout.selectedTabPosition == 0) {
                onSearchSent?.invoke(message)
            } else {
                onDuckChatSent?.invoke(message)
            }
            duckChatInput.clearFocus()

            if (duckChatTabLayout.selectedTabPosition == 1) {
                duckChatFireButton.isVisible = enableFireButton
                duckChatNewChat.isVisible = enableNewChatButton
                if (enableFireButton) {
                    applyLeftInputMargin(0)
                }
            }
        }
    }

    fun selectTab(index: Int) {
        duckChatTabLayout.post {
            duckChatTabLayout.getTabAt(index)?.select()
        }
    }

    fun showStopButton() {
        duckChatTabLayout.isVisible = false
        duckChatBack.isVisible = false
        duckChatStop.isVisible = true
        duckChatControls.visibility = View.INVISIBLE
        isStopButtonVisible = true
    }

    fun hideStopButton() {
        duckChatTabLayout.isVisible = true
        duckChatBack.isVisible = true
        duckChatStop.isVisible = false
        duckChatControls.visibility = View.VISIBLE
        isStopButtonVisible = false
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

    companion object {
        private const val DEFAULT_ANIMATION_DURATION = 300L
        private const val MAX_LINES = 8
    }
}
