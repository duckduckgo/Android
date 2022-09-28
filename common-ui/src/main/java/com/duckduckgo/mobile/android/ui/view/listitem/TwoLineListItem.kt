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

@file:Suppress("MemberVisibilityCanBePrivate")

package com.duckduckgo.mobile.android.ui.view.listitem

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.CompoundButton
import android.widget.CompoundButton.OnCheckedChangeListener
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import com.duckduckgo.mobile.android.R
import com.duckduckgo.mobile.android.databinding.ViewTwoLineItemBinding
import com.duckduckgo.mobile.android.ui.view.getColorFromAttr
import com.duckduckgo.mobile.android.ui.view.gone
import com.duckduckgo.mobile.android.ui.view.quietlySetIsChecked
import com.duckduckgo.mobile.android.ui.view.show
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding

class TwoLineListItem @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.twoLineListItemStyle
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val binding: ViewTwoLineItemBinding by viewBinding()

    init {
        context.obtainStyledAttributes(
            attrs,
            R.styleable.TwoLineListItem,
            0,
            R.style.Widget_DuckDuckGo_TwoLineListItem
        ).apply {

            binding.primaryText.text = getString(R.styleable.TwoLineListItem_primaryText)
            if (hasValue(R.styleable.TwoLineListItem_primaryTextColor)) {
                binding.primaryText.setTextColor(
                    getColor(
                        R.styleable.TwoLineListItem_primaryTextColor,
                        context.getColorFromAttr(R.attr.normalTextColor)
                    )
                )
            }

            binding.secondaryText.text = getString(R.styleable.TwoLineListItem_secondaryText)
            if (hasValue(R.styleable.TwoLineListItem_secondaryTextColor)) {
                binding.secondaryText.setTextColor(
                    getColor(
                        R.styleable.TwoLineListItem_secondaryTextColor,
                        context.getColorFromAttr(R.attr.normalTextColor)
                    )
                )
            }

            if (hasValue(R.styleable.TwoLineListItem_leadingIcon)) {
                binding.leadingIcon.setImageDrawable(getDrawable(R.styleable.TwoLineListItem_leadingIcon))
            } else {
                binding.leadingIconBackground.gone()
            }

            if (hasValue(R.styleable.TwoLineListItem_leadingIconBackground)) {
                val value = getInt(R.styleable.TwoLineListItem_leadingIconBackground, 0)
                if (value == 1) {
                    binding.leadingIconBackground.setBackgroundResource(R.drawable.list_item_image_circular_background)
                }
                if (value == 2) {
                    binding.leadingIconBackground.setBackgroundResource(R.drawable.list_item_image_round_background)
                }
                binding.leadingIconBackground.show()
            }

            val showBetaPill = getBoolean(R.styleable.TwoLineListItem_showBetaPill, false)
            if (showBetaPill) {
                binding.primaryText.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_beta_pill, 0)
                binding.primaryText.compoundDrawablePadding = 24
            }

            val showTrailingIcon = hasValue(R.styleable.TwoLineListItem_trailingIcon)
            val showSwitch = getBoolean(R.styleable.TwoLineListItem_showSwitch, false)
            if (showSwitch) {
                binding.trailingSwitch.show()
                binding.trailingIcon.gone()
            } else if (showTrailingIcon) {
                binding.trailingSwitch.gone()
                binding.trailingIcon.setImageDrawable(getDrawable(R.styleable.TwoLineListItem_trailingIcon))
                binding.trailingIcon.show()
            } else {
                binding.trailingContainer.gone()
            }

            recycle()
        }
    }

    /** Sets the item title */
    fun setPrimaryText(title: String) {
        binding.primaryText.text = title
    }

    /** Sets the item subtitle */
    fun setSecondaryText(subtitle: String) {
        binding.secondaryText.text = subtitle
    }

    fun leadingIcon() = binding.leadingIcon

    /** Sets the item image resource */
    fun setLeadingIcon(idRes: Int) {
        binding.leadingIcon.setImageResource(idRes)
        binding.leadingIcon.show()
    }

    /** Sets the item click listener */
    fun setOnClickListener(onClick: () -> Unit) {
        binding.itemContainer.setOnClickListener { onClick() }
    }

    /** Sets the item overflow menu click listener */
    fun setLeadingIconClickListener(onClick: (View) -> Unit) {
        binding.leadingIcon.setOnClickListener { onClick(binding.leadingIcon) }
    }

    /** Sets the leading image content description */
    fun setLeadingIconContentDescription(description: String) {
        binding.leadingIcon.contentDescription = description
    }

    /** Sets the item image resource */
    fun setTrailingIcon(idRes: Int) {
        binding.trailingIcon.setImageResource(idRes)
        binding.trailingIcon.show()
    }

    /** Sets the item overflow menu click listener */
    fun setTrailingIconClickListener(onClick: (View) -> Unit) {
        binding.trailingIcon.setOnClickListener { onClick(binding.trailingIcon) }
    }

    /** Sets the trailing image content description */
    fun setTrailingContentDescription(description: String) {
        binding.trailingIcon.contentDescription = description
    }

    /** Sets the trailing image content description */
    fun setPillVisible(isVisible: Boolean) {
        if (isVisible) {
            binding.primaryText.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_beta_pill, 0)
            binding.primaryText.compoundDrawablePadding = 24
        } else {
            binding.primaryText.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
            binding.primaryText.compoundDrawablePadding = 0
        }
    }

    /** Sets the checked change listener for the switch */
    fun setOnCheckedChangeListener(onCheckedChangeListener: OnCheckedChangeListener) {
        binding.trailingSwitch.setOnCheckedChangeListener(onCheckedChangeListener)
    }

    /** Sets the switch value */
    fun setIsChecked(isChecked: Boolean) {
        binding.trailingSwitch.isChecked = isChecked
    }

    /** Sets the switch as enabled or not */
    fun setIsEnabled(isEnabled: Boolean) {
        binding.trailingSwitch.isEnabled = isEnabled
    }

    /** Allows to set a new value to the switch, without triggering the onChangeListener */
    fun quietlySetIsChecked(
        newCheckedState: Boolean,
        changeListener: CompoundButton.OnCheckedChangeListener?
    ) {
        binding.trailingSwitch.quietlySetIsChecked(newCheckedState, changeListener)
    }
}
