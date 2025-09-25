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
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.duckduckgo.common.ui.view.toDp
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.databinding.ViewInputScreenButtonsBinding
import com.duckduckgo.mobile.android.R as CommonR

class InputScreenButtons @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {
    private val binding = ViewInputScreenButtonsBinding.inflate(LayoutInflater.from(context), this, true)

    var onSendClick: (() -> Unit)? = null
        set(value) {
            field = value
            binding.actionSend.setOnClickListener { value?.invoke() }
        }

    var onNewLineClick: (() -> Unit)? = null
        set(value) {
            field = value
            binding.actionNewLine.setOnClickListener { value?.invoke() }
        }

    var onVoiceClick: (() -> Unit)? = null
        set(value) {
            field = value
            binding.actionVoice.setOnClickListener { value?.invoke() }
        }

    fun setSendButtonIcon(iconResId: Int) {
        binding.actionSend.setImageResource(iconResId)
    }

    fun setSendButtonVisible(visible: Boolean) {
        binding.actionSend.isVisible = visible
    }

    fun setNewLineButtonVisible(visible: Boolean) {
        binding.actionNewLine.isVisible = visible
    }

    fun setVoiceButtonVisible(visible: Boolean) {
        binding.actionVoice.isVisible = visible
    }

    fun transformButtonsToFloating() {
        val backgroundRes = R.drawable.background_input_screen_button
        binding.actionNewLine.setBackgroundResource(backgroundRes)
        binding.actionVoice.setBackgroundResource(backgroundRes)

        val circularRippleDrawable = ContextCompat.getDrawable(context, CommonR.drawable.selectable_circular_ripple)
        binding.actionNewLine.foreground = circularRippleDrawable
        binding.actionVoice.foreground = circularRippleDrawable

        val elevation = 16.toDp(context).toFloat()
        binding.actionNewLine.elevation = elevation
        binding.actionVoice.elevation = elevation
        binding.actionSend.elevation = elevation
    }
}
