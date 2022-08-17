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
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import com.duckduckgo.vpn.internal.databinding.ExceptionRuleAppViewBinding

class RuleAppView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding: ExceptionRuleAppViewBinding by viewBinding()

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        binding.root.setOnClickListener(null)
    }

    var appName: String
        get() = binding.ruleAppName.toString()
        set(value) {
            binding.ruleAppName.text = value
        }

    var appIcon: Drawable?
        get() = binding.ruleAppIcon.drawable
        set(value) {
            binding.ruleAppIcon.setImageDrawable(value)
        }

    fun removeAllTrackerViews() {
        binding.appDomainRules.removeAllViews()
    }

    fun addTrackerView(view: View) {
        binding.appDomainRules.addView(view)
    }
}
