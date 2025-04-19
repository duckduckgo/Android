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
import android.graphics.drawable.Drawable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.duckduckgo.common.ui.view.PopupMenuItemView.PopupMenuItemType.DESTRUCTIVE
import com.duckduckgo.common.ui.view.PopupMenuItemView.PopupMenuItemType.PRIMARY
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.mobile.android.R
import com.duckduckgo.mobile.android.databinding.ViewPopupMenuItemBinding

class PopupMenuItemView
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr, defStyleRes) {

    private val binding: ViewPopupMenuItemBinding by viewBinding()

    init {
        initAttr(attrs)
    }

    private fun initAttr(attrs: AttributeSet?) {
        context.obtainStyledAttributes(
            attrs,
            R.styleable.PopupMenuItemView,
            0,
            0,
        ).apply {
            if (hasValue(R.styleable.PopupMenuItemView_primaryTextType)) {
                val primaryTextType = when (getInt(R.styleable.PopupMenuItemView_primaryTextType, 0)) {
                    1 -> DESTRUCTIVE
                    else -> PRIMARY
                }
                setPrimaryTextType(primaryTextType)
            }

            if (hasValue(R.styleable.PopupMenuItemView_trailingIcon)) {
                setTrailingIconDrawable(getDrawable(R.styleable.PopupMenuItemView_trailingIcon)!!)
            } else {
                setTrailingIconVisibility(false)
            }

            binding.label.text = getString(R.styleable.PopupMenuItemView_primaryText) ?: ""
            updateContentDescription()
            recycle()
        }
    }

    fun setPrimaryText(label: String) {
        binding.label.text = label
        updateContentDescription()
    }

    fun setPrimaryText(label: CharSequence) {
        binding.label.text = label
    }

    fun setPrimaryText(label: () -> String) {
        binding.label.text = label()
        updateContentDescription()
    }

    fun setPrimaryTextType(type: PopupMenuItemType) {
        when (type) {
            PRIMARY -> binding.label.setTextColor(ContextCompat.getColorStateList(context, R.color.primary_text_color_selector))
            DESTRUCTIVE -> binding.label.setTextColor(ContextCompat.getColorStateList(context, R.color.destructive_text_color_selector))
        }
    }

    private fun updateContentDescription() {
        binding.root.contentDescription = binding.label.text
    }

    fun setDisabled() {
        val textColorAttr = R.attr.daxColorTextDisabled
        val spannable = SpannableString(binding.label.text)
        spannable.setSpan(ForegroundColorSpan(binding.root.context.getColorFromAttr(textColorAttr)), 0, spannable.length, 0)
        setPrimaryText(spannable)
        isEnabled = false
    }

    fun setTrailingIconDrawable(drawable: Drawable) {
        binding.trailingIcon.setImageDrawable(drawable)
        setTrailingIconVisibility(true)
    }

    fun setTrailingIconResource(@DrawableRes resource: Int) {
        binding.trailingIcon.setImageResource(resource)
        setTrailingIconVisibility(true)
    }

    /** Sets the leading icon image visibility */
    fun setTrailingIconVisibility(visible: Boolean) {
        if (visible) {
            binding.trailingIcon.show()
        } else {
            binding.trailingIcon.gone()
        }
    }

    enum class PopupMenuItemType {
        PRIMARY,
        DESTRUCTIVE,
    }
}
