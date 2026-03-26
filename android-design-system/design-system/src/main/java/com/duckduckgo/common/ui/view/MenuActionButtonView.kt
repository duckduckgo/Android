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

package com.duckduckgo.common.ui.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.DrawableRes
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.duckduckgo.common.ui.view.text.DaxTextView
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.mobile.android.R
import com.duckduckgo.mobile.android.databinding.ViewMenuActionButtonBinding

class MenuActionButtonView
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = R.style.Widget_DuckDuckGo_MenuActionButtonView,
) : FrameLayout(context, attrs, defStyleAttr, defStyleRes) {
    private val binding: ViewMenuActionButtonBinding by viewBinding()

    init {
        initAttr(attrs)
    }

    private fun initAttr(attrs: AttributeSet?) {
        val attributes = context.obtainStyledAttributes(
            attrs,
            R.styleable.MenuActionButtonView,
            0,
            R.style.Widget_DuckDuckGo_MenuActionButtonView,
        )

        val labelText = attributes.getString(R.styleable.MenuActionButtonView_menuActionLabel)
        binding.label.text = labelText

        val typography = if (attributes.hasValue(R.styleable.MenuActionButtonView_typography)) {
            DaxTextView.Typography.from(attributes.getInt(R.styleable.MenuActionButtonView_typography, 0))
        } else {
            DaxTextView.Typography.Caption
        }
        binding.label.setTypography(typography)

        val iconResId = attributes.getResourceId(R.styleable.MenuActionButtonView_menuActionSrc, 0)
        if (iconResId != 0) {
            binding.icon.setImageResource(iconResId)
        }

        attributes.recycle()

        ViewCompat.setAccessibilityDelegate(
            this,
            object : AccessibilityDelegateCompat() {
                override fun onInitializeAccessibilityNodeInfo(
                    host: View,
                    info: AccessibilityNodeInfoCompat,
                ) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.addAction(
                        AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                            AccessibilityNodeInfoCompat.ACTION_CLICK,
                            labelText,
                        ),
                    )
                }
            },
        )
    }

    fun label(label: String) {
        binding.label.text = label
    }

    fun setIcon(@DrawableRes iconResId: Int) {
        binding.icon.setImageResource(iconResId)
    }

    override fun setEnabled(enabled: Boolean) {
        binding.icon.setEnabledOpacity(enabled)
        binding.label.setEnabledOpacity(enabled)
        isClickable = enabled
        super.setEnabled(enabled)
    }
}
