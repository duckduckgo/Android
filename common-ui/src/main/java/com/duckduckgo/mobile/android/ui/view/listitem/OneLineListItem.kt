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

package com.duckduckgo.mobile.android.ui.view.listitem

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.widget.CompoundButton
import android.widget.CompoundButton.OnCheckedChangeListener
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat
import com.duckduckgo.mobile.android.R
import com.duckduckgo.mobile.android.databinding.ViewOneLineListItemBinding
import com.duckduckgo.mobile.android.ui.view.gone
import com.duckduckgo.mobile.android.ui.view.quietlySetIsChecked
import com.duckduckgo.mobile.android.ui.view.recursiveEnable
import com.duckduckgo.mobile.android.ui.view.show
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding

class OneLineListItem @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.oneLineListItemStyle
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val binding: ViewOneLineListItemBinding by viewBinding()

    init {
        context.obtainStyledAttributes(
            attrs,
            R.styleable.OneLineListItem,
            0,
            R.style.Widget_DuckDuckGo_OneLineListItem
        ).apply {

            binding.primaryText.text = getString(R.styleable.OneLineListItem_primaryText)

            if (hasValue(R.styleable.OneLineListItem_primaryTextColorOverlay)) {
                binding.primaryText.setTextColor(getColorStateList(R.styleable.OneLineListItem_primaryTextColorOverlay))
            }

            if (hasValue(R.styleable.OneLineListItem_leadingIcon)) {
                setLeadingIconDrawable(getDrawable(R.styleable.OneLineListItem_leadingIcon)!!)
            } else {
                binding.leadingIconBackground.gone()
            }

            if (hasValue(R.styleable.OneLineListItem_leadingIconBackground)) {
                setLeadingIconBackgroundType(getInt(R.styleable.OneLineListItem_leadingIconBackground, 0))
            }

            if (hasValue(R.styleable.OneLineListItem_trailingIcon)) {
                binding.trailingIcon.setImageDrawable(getDrawable(R.styleable.OneLineListItem_trailingIcon))
            } else {
                binding.trailingIcon.gone()
            }

            val showTrailingIcon = hasValue(R.styleable.OneLineListItem_trailingIcon)
            val showSwitch = getBoolean(R.styleable.OneLineListItem_showSwitch, false)
            if (showSwitch) {
                showSwitch()
            } else if (showTrailingIcon) {
                binding.trailingIcon.setImageDrawable(getDrawable(R.styleable.OneLineListItem_trailingIcon))
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

    /** Sets the leading icon image resource */
    fun setLeadingIcon(idRes: Int) {
        val drawable = VectorDrawableCompat.create(resources, idRes, null)
        binding.leadingIcon.setImageDrawable(drawable)
        binding.leadingIcon.show()
    }

    /** Sets the item image resource */
    fun setLeadingIconDrawable(drawable: Drawable) {
        binding.leadingIcon.setImageDrawable(drawable)
        binding.leadingIconBackground.show()
    }

    fun leadingIcon() = binding.leadingIcon

    /** Sets the trailing icon image resource */
    fun setTrailingIcon(idRes: Int) {
        val drawable = VectorDrawableCompat.create(resources, idRes, null)
        binding.trailingIcon.setImageDrawable(drawable)
        binding.trailingIcon.show()
    }

    /** Sets the item image content description */
    fun setLeadingIconContentDescription(description: String) {
        binding.leadingIcon.contentDescription = description
    }

    /** Sets the item click listener */
    fun setClickListener(onClick: () -> Unit) {
        binding.itemContainer.setOnClickListener { onClick() }
    }

    /** Sets the item overflow menu click listener */
    fun setLeadingIconClickListener(onClick: (View) -> Unit) {
        binding.leadingIcon.setOnClickListener { onClick(binding.leadingIcon) }
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

    /** Sets the item overflow menu click listener */
    fun setTrailingIconClickListener(onClick: (View) -> Unit) {
        binding.trailingIcon.setOnClickListener { onClick(binding.trailingIcon) }
    }

    /** Sets the trailing image content description */
    fun setTrailingContentDescription(description: String) {
        binding.leadingIcon.contentDescription = description
    }

    /** Sets the checked change listener for the switch */
    fun setOnCheckedChangeListener(onCheckedChangeListener: OnCheckedChangeListener) {
        binding.trailingSwitch.setOnCheckedChangeListener(onCheckedChangeListener)
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

    /** Sets the switch value */
    fun setIsChecked(isChecked: Boolean) {
        binding.trailingSwitch.isChecked = isChecked
    }

    /** Sets the switch as enabled or not */
    override fun setEnabled(enabled: Boolean) {
        recursiveEnable(enabled)
        super.setEnabled(enabled)
    }

    /** Allows to set a new value to the switch, without triggering the onChangeListener */
    fun quietlySetIsChecked(
        newCheckedState: Boolean,
        changeListener: CompoundButton.OnCheckedChangeListener?
    ) {
        binding.trailingSwitch.quietlySetIsChecked(newCheckedState, changeListener)
    }
}
