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
import com.duckduckgo.mobile.android.R
import com.duckduckgo.mobile.android.databinding.ViewStatusIndicatorBinding

class StatusIndicatorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private val binding: ViewStatusIndicatorBinding by viewBinding()

    init {
        context.obtainStyledAttributes(
            attrs,
            R.styleable.StatusIndicator,
            0,
            0,
        ).apply {

            val status = Status.from(getInt(R.styleable.StatusIndicator_indicatorStatus, 0))
            setStatus(status)

            recycle()
        }
    }

    fun setStatus(status: Status) {
        when (status) {
            Status.ALWAYS_ON -> {
                binding.icon.isEnabled = true
                binding.label.text = context.getString(R.string.alwaysOn)
                show()
            }
            Status.ON -> {
                binding.icon.isEnabled = true
                binding.label.text = context.getString(R.string.on)
                show()
            }
            Status.OFF -> {
                binding.icon.isEnabled = false
                binding.label.text = context.getString(R.string.off)
                show()
            }
            Status.NONE -> {
                gone()
            }
        }
    }

    fun setStatus(isOn: Boolean) {
        setStatus(if (isOn) Status.ON else Status.OFF)
    }

    enum class Status {

        ALWAYS_ON,
        ON,
        OFF,
        NONE,
        ;

        companion object {

            fun from(value: Int): Status = when (value) {
                0 -> ALWAYS_ON
                1 -> ON
                2 -> OFF
                3 -> NONE
                else -> OFF
            }
        }
    }
}
