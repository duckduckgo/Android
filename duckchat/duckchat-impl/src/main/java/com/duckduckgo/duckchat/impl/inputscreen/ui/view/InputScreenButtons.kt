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
import androidx.core.view.setPadding
import androidx.core.view.updateLayoutParams
import com.duckduckgo.common.ui.view.toPx
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.databinding.ViewInputScreenButtonsBinding
import kotlin.math.roundToInt
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
        // enlarge buttons if they are floating
        val buttonSizePx = 40f.toPx(context).roundToInt()
        binding.actionSend.updateLayoutParams {
            width = buttonSizePx
            height = buttonSizePx
        }
        binding.actionNewLine.updateLayoutParams {
            width = buttonSizePx
            height = buttonSizePx
        }
        binding.actionVoice.updateLayoutParams {
            width = buttonSizePx
            height = buttonSizePx
        }

        // add shadows via elevation and add padding to the container to avoid clipping
        val elevation = 6f.toPx(context)
        binding.actionNewLine.elevation = elevation
        binding.actionVoice.elevation = elevation
        binding.actionSend.elevation = elevation
        val padding = elevation.times(2).roundToInt()
        binding.root.setPadding(padding)

        // add circular background and click ripple to all remaining buttons
        val backgroundRes = R.drawable.background_input_screen_button
        binding.actionNewLine.setBackgroundResource(backgroundRes)
        binding.actionVoice.setBackgroundResource(backgroundRes)
        val circularRippleDrawable = ContextCompat.getDrawable(context, CommonR.drawable.selectable_circular_ripple)
        binding.actionNewLine.foreground = circularRippleDrawable
        binding.actionVoice.foreground = circularRippleDrawable
    }
}
