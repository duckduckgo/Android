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
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.CompoundButton.OnCheckedChangeListener
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.children
import com.duckduckgo.mobile.android.R
import com.duckduckgo.mobile.android.databinding.ViewTwoLineItemBinding
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
            binding.secondaryText.text = getString(R.styleable.TwoLineListItem_secondaryText)

            if (hasValue(R.styleable.TwoLineListItem_primaryTextColorOverlay)) {
                binding.primaryText.setTextColor(getColorStateList(R.styleable.TwoLineListItem_primaryTextColorOverlay))
            }

            if (hasValue(R.styleable.TwoLineListItem_secondaryTextColorOverlay)) {
                binding.secondaryText.setTextColor(getColorStateList(R.styleable.TwoLineListItem_secondaryTextColorOverlay))
            }

            if (hasValue(R.styleable.TwoLineListItem_leadingIcon)) {
                setLeadingIconDrawable(getDrawable(R.styleable.TwoLineListItem_leadingIcon)!!)
            } else {
                binding.leadingIconBackground.gone()
            }

            if (hasValue(R.styleable.TwoLineListItem_leadingIconBackground)) {
                setLeadingIconBackgroundType(getInt(R.styleable.TwoLineListItem_leadingIconBackground, 0))
            }

            setPillVisible(getBoolean(R.styleable.TwoLineListItem_showBetaPill, false))

            val showTrailingIcon = hasValue(R.styleable.TwoLineListItem_trailingIcon)
            val showSwitch = getBoolean(R.styleable.TwoLineListItem_showSwitch, false)
            if (showSwitch) {
                showSwitch()
            } else if (showTrailingIcon) {
                binding.trailingIcon.setImageDrawable(getDrawable(R.styleable.TwoLineListItem_trailingIcon))
                showTrailingIcon()
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
        binding.leadingIconBackground.show()
    }

    /** Sets the item image resource */
    fun setLeadingIconDrawable(drawable: Drawable) {
        binding.leadingIcon.setImageDrawable(drawable)
        binding.leadingIconBackground.show()
    }

    /** Sets the item click listener */
    fun setClickListener(onClick: () -> Unit) {
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

    /** Sets the background image type */
    fun setLeadingIconBackgroundType(value: Int) {
        if (value == 1) {
            binding.leadingIconBackground.setBackgroundResource(R.drawable.list_item_image_circular_background)
        }
        if (value == 2) {
            binding.leadingIconBackground.setBackgroundResource(R.drawable.list_item_image_round_background)
        }
        binding.leadingIconBackground.show()
    }

    /** Sets the item image resource */
    fun setTrailingIcon(idRes: Int) {
        binding.trailingIcon.setImageResource(idRes)
        showTrailingIcon()
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
            binding.betaPill.show()
        } else {
            binding.betaPill.gone()
        }
    }

    /** Sets the Switch Visible */
    fun showSwitch() {
        binding.trailingContainer.show()
        binding.trailingSwitch.show()
        binding.trailingIcon.gone()
    }

    /** Sets the Trailing Icon Visible */
    fun showTrailingIcon() {
        binding.trailingContainer.show()
        binding.trailingIcon.show()
        binding.trailingSwitch.gone()
    }

    /** Sets the checked change listener for the switch */
    fun setOnCheckedChangeListener(onCheckedChangeListener: OnCheckedChangeListener) {
        binding.trailingSwitch.setOnCheckedChangeListener(onCheckedChangeListener)
    }

    /** Sets the switch value */
    fun setIsChecked(isChecked: Boolean) {
        binding.trailingSwitch.isChecked = isChecked
    }

    /** Allows to set a new value to the switch, without triggering the onChangeListener */
    fun quietlySetIsChecked(
        newCheckedState: Boolean,
        changeListener: CompoundButton.OnCheckedChangeListener?
    ) {
        binding.trailingSwitch.quietlySetIsChecked(newCheckedState, changeListener)
    }

    override fun setEnabled(enabled: Boolean) {
        recursiveEnable(enabled)
        super.setEnabled(enabled)
    }

    fun View.recursiveEnable(enabled: Boolean) {
        (this as? ViewGroup)?.children?.forEach {
            it.isEnabled = enabled
            it.recursiveEnable(enabled)
        }
    }
}
