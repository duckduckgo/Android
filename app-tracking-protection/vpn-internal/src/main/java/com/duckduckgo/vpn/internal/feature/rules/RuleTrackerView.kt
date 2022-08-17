/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.vpn.internal.feature.rules

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import com.duckduckgo.vpn.internal.databinding.ExceptionRuleDomainViewBinding

class RuleTrackerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding: ExceptionRuleDomainViewBinding by viewBinding()

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        binding.checkBox.setOnCheckedChangeListener { _, isEnable ->
            ruleTrackerListener?.onTrackerClicked(this, isEnable)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        binding.root.setOnClickListener(null)
    }

    var ruleTrackerListener: RuleTrackerListener? = null

    var domain: String
        get() = binding.trackerDomain.toString()
        set(value) {
            binding.trackerDomain.text = value
        }

    var isChecked: Boolean
        get() = binding.checkBox.isChecked
        set(value) {
            binding.checkBox.isChecked = value
        }

    interface RuleTrackerListener {
        fun onTrackerClicked(
            view: View,
            enabled: Boolean
        )
    }
}
