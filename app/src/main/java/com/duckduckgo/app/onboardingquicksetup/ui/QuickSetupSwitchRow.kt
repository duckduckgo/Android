/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.app.onboardingquicksetup.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.CompoundButton
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.res.use
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ViewQuickSetupSwitchRowBinding
import com.duckduckgo.common.ui.view.quietlySetIsChecked

class QuickSetupSwitchRow @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val binding: ViewQuickSetupSwitchRowBinding =
        ViewQuickSetupSwitchRowBinding.inflate(LayoutInflater.from(context), this)

    init {
        context.theme.obtainStyledAttributes(attrs, R.styleable.QuickSetupSwitchRow, 0, 0).use { attrs ->
            attrs.getResourceId(R.styleable.QuickSetupSwitchRow_quickSetupRowIcon, 0)
                .takeIf { it != 0 }
                ?.let(binding.quickSetupSwitchRowIcon::setImageResource)
            attrs.getText(R.styleable.QuickSetupSwitchRow_quickSetupRowPrimaryText)
                ?.let { binding.quickSetupSwitchRowPrimaryText.text = it }
        }
    }

    private var checkedChangeListener: CompoundButton.OnCheckedChangeListener? = null

    var isChecked: Boolean
        get() = binding.quickSetupSwitchRowSwitch.isChecked
        set(value) {
            binding.quickSetupSwitchRowSwitch.isChecked = value
        }

    fun setIcon(@DrawableRes res: Int) {
        binding.quickSetupSwitchRowIcon.setImageResource(res)
    }

    fun setPrimaryText(@StringRes res: Int) {
        binding.quickSetupSwitchRowPrimaryText.setText(res)
    }

    fun setOnCheckedChangeListener(listener: (Boolean) -> Unit) {
        val wrapped = CompoundButton.OnCheckedChangeListener { _, isChecked -> listener(isChecked) }
        checkedChangeListener = wrapped
        binding.quickSetupSwitchRowSwitch.setOnCheckedChangeListener(wrapped)
    }

    fun setCheckedSilently(checked: Boolean) {
        binding.quickSetupSwitchRowSwitch.quietlySetIsChecked(checked, checkedChangeListener)
    }
}
