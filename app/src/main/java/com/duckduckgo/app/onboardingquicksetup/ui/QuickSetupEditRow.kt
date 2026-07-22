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
import android.util.TypedValue
import android.view.LayoutInflater
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.res.use
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ViewQuickSetupEditRowBinding

class QuickSetupEditRow @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val binding: ViewQuickSetupEditRowBinding =
        ViewQuickSetupEditRowBinding.inflate(LayoutInflater.from(context), this)

    init {
        isClickable = true
        val selectableBackground = TypedValue().also {
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, it, true)
        }
        setBackgroundResource(selectableBackground.resourceId)
        val verticalPadding = resources.getDimensionPixelSize(com.duckduckgo.mobile.android.R.dimen.keyline_2)
        setPadding(paddingLeft, verticalPadding, paddingRight, verticalPadding)
        context.theme.obtainStyledAttributes(attrs, R.styleable.QuickSetupEditRow, 0, 0).use { attrs ->
            attrs.getResourceId(R.styleable.QuickSetupEditRow_quickSetupRowIcon, 0)
                .takeIf { it != 0 }
                ?.let(binding.quickSetupEditRowIcon::setImageResource)
            attrs.getText(R.styleable.QuickSetupEditRow_quickSetupRowPrimaryText)
                ?.let { binding.quickSetupEditRowPrimaryText.text = it }
            attrs.getText(R.styleable.QuickSetupEditRow_quickSetupRowSecondaryText)
                ?.let { binding.quickSetupEditRowSecondaryText.text = it }
        }
    }

    fun setIcon(@DrawableRes res: Int) {
        binding.quickSetupEditRowIcon.setImageResource(res)
    }

    fun setPrimaryText(@StringRes res: Int) {
        binding.quickSetupEditRowPrimaryText.setText(res)
    }

    fun setSecondaryText(@StringRes res: Int) {
        binding.quickSetupEditRowSecondaryText.setText(res)
    }
}
