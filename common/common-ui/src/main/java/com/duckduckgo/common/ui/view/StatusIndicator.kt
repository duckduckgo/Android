/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.common.ui.view

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.mobile.android.databinding.ViewStatusIndicatorBinding

class StatusIndicator @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private val binding: ViewStatusIndicatorBinding by viewBinding()

    fun setStatus(isOn: Boolean) {
        if (isOn) {
            binding.icon.isEnabled = true
            // TODO copy changes
            binding.label.text = "On"
        } else {
            binding.icon.isEnabled = false
            // TODO copy changes
            binding.label.text = "Off"
        }
    }
}
