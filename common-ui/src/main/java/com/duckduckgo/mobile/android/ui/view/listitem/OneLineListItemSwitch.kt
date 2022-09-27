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

import android.R.attr
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.CompoundButton
import android.widget.CompoundButton.OnCheckedChangeListener
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat
import com.duckduckgo.mobile.android.R
import com.duckduckgo.mobile.android.databinding.ViewOneLineListSwitchItemBinding
import com.duckduckgo.mobile.android.ui.view.getColorFromAttr
import com.duckduckgo.mobile.android.ui.view.gone
import com.duckduckgo.mobile.android.ui.view.quietlySetIsChecked
import com.duckduckgo.mobile.android.ui.view.show
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding

class OneLineListItemSwitch @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.oneLineListItemStyle
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val binding: ViewOneLineListSwitchItemBinding by viewBinding()

    init {
        context.obtainStyledAttributes(
            attrs,
            R.styleable.OneLineListItemSwitch,
            0,
            R.style.Widget_DuckDuckGo_OneLineListItem
        ).apply {

            binding.primaryText.text = getString(R.styleable.OneLineListItemSwitch_primaryText)
            if (hasValue(R.styleable.OneLineListItemSwitch_primaryTextColor)) {
                binding.primaryText.setTextColor(
                    getColor(
                        R.styleable.OneLineListItemSwitch_primaryTextColor,
                        context.getColorFromAttr(R.attr.normalTextColor)
                    )
                )
            }

            if (hasValue(R.styleable.OneLineListItemSwitch_leadingIcon)) {
                binding.leadingIcon.setImageDrawable(getDrawable(R.styleable.OneLineListItemSwitch_leadingIcon))
            } else {
                binding.leadingIconBackground.gone()
            }

            if (hasValue(R.styleable.OneLineListItemSwitch_leadingIconBackground)) {
                val value = getInt(R.styleable.OneLineListItemSwitch_leadingIconBackground, 0)
                if (value == 1) {
                    binding.leadingIconBackground.setBackgroundResource(R.drawable.list_item_image_circular_background)
                }
                if (value == 2) {
                    binding.leadingIconBackground.setBackgroundResource(R.drawable.list_item_image_round_background)
                }
                binding.leadingIconBackground.show()
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

    /** Sets the item click listener */
    fun setClickListener(onClick: () -> Unit) {
        binding.itemContainer.setOnClickListener { onClick() }
    }

    /** Sets the item overflow menu click listener */
    fun setLeadingIconClickListener(onClick: (View) -> Unit) {
        binding.leadingIcon.setOnClickListener { onClick(binding.leadingIcon) }
    }

    /** Sets the leading image content description */
    fun setLeadingIcongContentDescription(description: String) {
        binding.leadingIcon.contentDescription = description
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
