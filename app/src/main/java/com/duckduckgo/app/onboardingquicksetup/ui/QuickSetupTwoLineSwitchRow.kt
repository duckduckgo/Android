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
import androidx.annotation.DrawableRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.res.use
import androidx.core.view.isVisible
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ViewQuickSetupTwoLineSwitchRowBinding
import com.duckduckgo.mobile.android.R as CommonR

class QuickSetupTwoLineSwitchRow @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val binding: ViewQuickSetupTwoLineSwitchRowBinding =
        ViewQuickSetupTwoLineSwitchRowBinding.inflate(LayoutInflater.from(context), this)

    init {
        // DaxTextView applies its own typography appearance in its constructor, overwriting anything a style= would set,
        // so the onboarding appearances have to land after inflation.
        binding.quickSetupTwoLineSwitchRowPrimaryText.setTextAppearance(CommonR.style.Typography_DuckDuckGo_Onboarding_PreferencePrimary)
        binding.quickSetupTwoLineSwitchRowSecondaryText.setTextAppearance(CommonR.style.Typography_DuckDuckGo_Onboarding_PreferenceSecondary)
        minimumHeight = resources.getDimensionPixelSize(CommonR.dimen.twoLineItemHeight)
        val verticalPadding = resources.getDimensionPixelSize(R.dimen.quickSetupTwoLineRowVerticalPadding)
        setPadding(0, verticalPadding, 0, verticalPadding)
        context.theme.obtainStyledAttributes(attrs, R.styleable.QuickSetupTwoLineSwitchRow, 0, 0).use { attrs ->
            attrs.getResourceId(R.styleable.QuickSetupTwoLineSwitchRow_quickSetupRowIcon, 0)
                .takeIf { it != 0 }
                ?.let(binding.quickSetupTwoLineSwitchRowIcon::setImageResource)
            attrs.getText(R.styleable.QuickSetupTwoLineSwitchRow_quickSetupRowPrimaryText)
                ?.let { binding.quickSetupTwoLineSwitchRowPrimaryText.text = it }
            attrs.getText(R.styleable.QuickSetupTwoLineSwitchRow_quickSetupRowSecondaryText)
                ?.let { binding.quickSetupTwoLineSwitchRowSecondaryText.text = it }
        }
    }

    var isChecked: Boolean
        get() = binding.quickSetupTwoLineSwitchRowSwitch.isChecked
        set(value) {
            binding.quickSetupTwoLineSwitchRowSwitch.isChecked = value
        }

    /** A null [res] hides the icon, leaving the text aligned with the row's leading edge. */
    fun setIcon(@DrawableRes res: Int?) {
        res?.let(binding.quickSetupTwoLineSwitchRowIcon::setImageResource)
        binding.quickSetupTwoLineSwitchRowIcon.isVisible = res != null
    }

    fun setPrimaryText(text: CharSequence) {
        binding.quickSetupTwoLineSwitchRowPrimaryText.text = text
    }

    /** A null [text] collapses the second line, leaving the primary text centred against the icon and switch. */
    fun setSecondaryText(text: CharSequence?) {
        binding.quickSetupTwoLineSwitchRowSecondaryText.text = text
        binding.quickSetupTwoLineSwitchRowSecondaryText.isVisible = text != null
    }

    fun setOnCheckedChangeListener(listener: (Boolean) -> Unit) {
        binding.quickSetupTwoLineSwitchRowSwitch.setOnCheckedChangeListener { _, isChecked -> listener(isChecked) }
    }
}
