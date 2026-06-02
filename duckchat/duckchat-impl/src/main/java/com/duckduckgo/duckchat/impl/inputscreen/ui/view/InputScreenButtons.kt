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
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.LayoutRes
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.toPx
import com.duckduckgo.duckchat.impl.R
import kotlin.math.roundToInt
import com.duckduckgo.mobile.android.R as CommonR

class InputScreenButtons @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    useTopBar: Boolean = true,
    @LayoutRes layoutResId: Int = R.layout.view_input_screen_buttons,
) : LinearLayout(context, attrs, defStyleAttr) {

    private val actionSend: ImageView by lazy { findViewById(R.id.actionSend) }
    private val actionNewLine: ImageView by lazy { findViewById(R.id.actionNewLine) }
    private val actionVoiceSearch: ImageView? by lazy { findViewById(R.id.actionVoiceSearch) }
    private val actionVoiceChat: ImageView? by lazy { findViewById(R.id.actionVoiceChat) }

    var onSendClick: (() -> Unit)? = null
        set(value) {
            field = value
            actionSend.setOnClickListener { sendIfEnabled() }
        }

    var onStopClick: (() -> Unit)? = null

    var onNewLineClick: (() -> Unit)? = null
        set(value) {
            field = value
            actionNewLine.setOnClickListener { value?.invoke() }
        }

    var onVoiceSearchClick: (() -> Unit)? = null
        set(value) {
            field = value
            actionVoiceSearch?.setOnClickListener { value?.invoke() }
        }

    var onVoiceChatClick: (() -> Unit)? = null
        set(value) {
            field = value
            actionVoiceChat?.setOnClickListener { value?.invoke() }
        }

    init {
        LayoutInflater.from(context).inflate(layoutResId, this, true)
        if (useTopBar) {
            // when used in top bar we want to transform the buttons to floating
            transformButtonsToFloating()
        } else {
            // when in bottom bar mode, the voice search icon is shown in the input field
            actionVoiceSearch?.gone()
        }
    }

    fun setSendButtonIcon(iconResId: Int) {
        actionSend.setImageResource(iconResId)
    }

    fun showStopButton() {
        actionSend.isEnabled = true
        actionSend.setImageResource(R.drawable.ic_stop_16)
        actionSend.setOnClickListener { onStopClick?.invoke() }
    }

    fun showSendButton() {
        actionSend.setImageResource(CommonR.drawable.ic_arrow_right_24)
        actionSend.setOnClickListener { sendIfEnabled() }
    }

    private fun sendIfEnabled() {
        if (actionSend.isEnabled) onSendClick?.invoke()
    }

    private fun resolveThemeColorStateList(attr: Int): android.content.res.ColorStateList? {
        val attributes = context.obtainStyledAttributes(intArrayOf(attr))
        val colorStateList = attributes.getColorStateList(0)
        attributes.recycle()
        return colorStateList
    }

    fun setSendButtonEnabled(enabled: Boolean) {
        actionSend.isEnabled = enabled
    }

    fun setSendButtonVisible(visible: Boolean) {
        actionSend.isVisible = visible
    }

    fun setNewLineButtonVisible(visible: Boolean) {
        actionNewLine.isVisible = visible
    }

    fun setVoiceSearchVisible(visible: Boolean) {
        actionVoiceSearch?.isVisible = visible
    }

    fun setVoiceChatVisible(visible: Boolean) {
        actionVoiceChat?.isVisible = visible
    }

    private fun transformButtonsToFloating() {
        // enlarge buttons if they are floating
        val buttonSizePx = 40f.toPx(context).roundToInt()
        actionSend.updateLayoutParams {
            width = buttonSizePx
            height = buttonSizePx
        }
        actionNewLine.updateLayoutParams {
            width = buttonSizePx
            height = buttonSizePx
        }
        actionVoiceSearch?.updateLayoutParams {
            width = buttonSizePx
            height = buttonSizePx
        }
        actionVoiceChat?.updateLayoutParams {
            width = buttonSizePx
            height = buttonSizePx
        }

        // breathing room for the floating row inside its container
        val verticalPad = 16f.toPx(context).roundToInt()
        val horizontalPad = 10f.toPx(context).roundToInt()
        setPadding(horizontalPad, verticalPad, horizontalPad, verticalPad)

        // actionSend always carries its own circular background + ripple (set in XML)
        // because it needs the styling in both top-bar and bottom modes. The other
        // three buttons only need it when floating, so we apply it here.
        val backgroundRes = R.drawable.background_input_screen_button
        actionNewLine.setBackgroundResource(backgroundRes)
        actionVoiceChat?.setBackgroundResource(backgroundRes)
        actionVoiceSearch?.setBackgroundResource(backgroundRes)
        val circularRippleDrawable = ContextCompat.getDrawable(context, CommonR.drawable.selectable_circular_ripple)
        actionNewLine.foreground = circularRippleDrawable
        actionVoiceSearch?.foreground = circularRippleDrawable
        actionVoiceChat?.foreground = circularRippleDrawable
    }
}
