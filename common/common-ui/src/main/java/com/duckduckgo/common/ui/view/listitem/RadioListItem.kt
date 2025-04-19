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

package com.duckduckgo.common.ui.view.listitem

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import com.duckduckgo.common.ui.view.button.RadioButton
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.listitem.DaxListItem.ImageBackground
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.view.text.DaxTextView
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.mobile.android.R
import com.duckduckgo.mobile.android.databinding.ViewRadioListItemBinding

class RadioListItem @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.twoLineListItemStyle,
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val binding: ViewRadioListItemBinding by viewBinding()

    val radioButton: RadioButton
        get() = binding.radioButton
    val primaryText: DaxTextView
        get() = binding.primaryText
    val secondaryText: DaxTextView
        get() = binding.secondaryText
    val leadingEmojiIconContainer: View
        get() = binding.leadingIconBackground
    val trailingIconContainer: View
        get() = binding.trailingIconContainer

    init {
        context.obtainStyledAttributes(
            attrs,
            R.styleable.RadioListItem,
            0,
            R.style.Widget_DuckDuckGo_TwoLineListItem,
        ).apply {
            if (hasValue(R.styleable.RadioListItem_android_minHeight)) {
                binding.itemContainer.minHeight =
                    getDimensionPixelSize(R.styleable.RadioListItem_android_minHeight, resources.getDimensionPixelSize(R.dimen.oneLineItemHeight))
            } else {
                binding.itemContainer.minHeight = resources.getDimensionPixelSize(R.dimen.oneLineItemHeight)
            }

            binding.radioButton.isChecked = getBoolean(R.styleable.RadioListItem_android_checked, false)

            binding.primaryText.text = getString(R.styleable.RadioListItem_primaryText)

            if (hasValue(R.styleable.RadioListItem_secondaryText)) {
                binding.secondaryText.text = getString(R.styleable.RadioListItem_secondaryText)
            } else {
                binding.secondaryText.gone()
            }

            if (hasValue(R.styleable.RadioListItem_primaryTextColorOverlay)) {
                binding.primaryText.setTextColor((getColorStateList(R.styleable.RadioListItem_primaryTextColorOverlay)))
            }

            if (hasValue(R.styleable.RadioListItem_secondaryTextColorOverlay)) {
                binding.secondaryText.setTextColor(getColorStateList(R.styleable.RadioListItem_secondaryTextColorOverlay))
            }

            if (hasValue(R.styleable.RadioListItem_leadingEmojiIcon)) {
                binding.leadingIcon.text = getString(R.styleable.RadioListItem_leadingEmojiIcon)
            } else {
                binding.leadingIcon.gone()
                binding.leadingIconBackground.gone()
            }

            if (hasValue(R.styleable.RadioListItem_leadingIconBackground)) {
                val type = ImageBackground.from(getInt(R.styleable.RadioListItem_leadingIconBackground, 0))
                binding.leadingIconBackground.setBackgroundResource(ImageBackground.background(type))
                val padding = resources.getDimensionPixelSize(com.duckduckgo.mobile.android.R.dimen.twoLineItemVerticalPadding)
                binding.itemContainer.setPadding(0, padding, 0, padding)
                binding.leadingIconBackground.show()
            }

            if (getBoolean(R.styleable.RadioListItem_showTrailingIcon, false)) {
                binding.trailingIconContainer.show()
            } else {
                binding.trailingIconContainer.gone()
            }
            recycle()
        }
    }

    fun setClickListener(onClick: () -> Unit) {
        binding.radioButton.setOnClickListener { onClick() }
        binding.itemContainer.setOnClickListener { onClick() }
    }

    /** Sets the primary text title */
    fun setPrimaryText(title: String?) {
        primaryText.text = title
        primaryText.show()
    }

    /** Sets the secondary text title */
    fun setSecondaryText(title: String?) {
        secondaryText.text = title
        secondaryText.show()
    }

    /** Sets the leading icon image drawable */
    fun setLeadingEmojiIcon(emoji: String) {
        binding.leadingIcon.text = emoji
        binding.leadingIcon.show()
        leadingEmojiIconContainer.show()
    }

    /** Sets the item overflow menu click listener */
    fun setTrailingIconClickListener(onClick: (View) -> Unit) {
        trailingIconContainer.setOnClickListener { onClick(trailingIconContainer) }
    }

    fun setChecked(checked: Boolean) {
        radioButton.isChecked = checked
    }
}
