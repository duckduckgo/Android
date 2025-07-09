/*
 * Copyright (c) 2022 DuckDuckGo
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
import androidx.annotation.DrawableRes
import com.duckduckgo.common.ui.view.text.DaxTextView
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.mobile.android.R
import com.duckduckgo.mobile.android.databinding.ViewMenuItemBinding

class MenuItemView
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = R.style.Widget_DuckDuckGo_MenuItemView,
) : LinearLayout(context, attrs, defStyleAttr, defStyleRes) {

    private val binding: ViewMenuItemBinding by viewBinding()

    init {
        initAttr(attrs)
    }

    private fun initAttr(attrs: AttributeSet?) {
        val attributes = context.obtainStyledAttributes(
            attrs,
            R.styleable.MenuItemView,
            0,
            R.style.Widget_DuckDuckGo_MenuItemView,
        )

        val typography = if (attributes.hasValue(R.styleable.MenuItemView_typography)) {
            DaxTextView.Typography.from(attributes.getInt(R.styleable.MenuItemView_typography, 0))
        } else {
            DaxTextView.Typography.Body1
        }

        binding.label.setTypography(typography)

        val hasType = attributes.hasValue(R.styleable.MenuItemView_textType)
        if (hasType) {
            val textType = DaxTextView.TextType.from(attributes.getInt(R.styleable.MenuItemView_textType, 0))
            binding.label.setTextColorStateList(textType)
        }

        binding.label.text = attributes.getString(R.styleable.MenuItemView_primaryText) ?: ""
        binding.icon.setImageResource(
            attributes.getResourceId(
                R.styleable.MenuItemView_iconDrawable,
                R.drawable.ic_globe_16,
            ),
        )
        updateContentDescription()
        attributes.recycle()
    }

    fun label(label: String) {
        binding.label.text = label
        updateContentDescription()
    }

    fun label(label: () -> String) {
        binding.label.text = label()
        updateContentDescription()
    }

    fun setIcon(@DrawableRes iconResId: Int) {
        binding.icon.setImageResource(iconResId)
    }

    private fun updateContentDescription() {
        binding.root.contentDescription = binding.label.text
    }
}
