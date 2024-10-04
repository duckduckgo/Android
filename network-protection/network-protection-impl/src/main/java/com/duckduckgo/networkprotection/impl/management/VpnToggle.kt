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

package com.duckduckgo.networkprotection.impl.management

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.CompoundButton.OnCheckedChangeListener
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import com.duckduckgo.common.ui.view.DaxSwitch
import com.duckduckgo.common.ui.view.quietlySetIsChecked
import com.duckduckgo.common.ui.view.recursiveEnable
import com.duckduckgo.common.ui.view.setEnabledOpacity
import com.duckduckgo.common.ui.view.text.DaxTextView
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.mobile.android.R
import com.duckduckgo.networkprotection.impl.databinding.ViewVpnToggleBinding

class VpnToggle @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.twoLineListItemStyle,
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val binding: ViewVpnToggleBinding by viewBinding()

    val primaryText: DaxTextView
        get() = binding.primaryText
    val secondaryText: DaxTextView
        get() = binding.secondaryText
    val trailingSwitch: DaxSwitch
        get() = binding.trailingSwitch
    val indicator: ImageView
        get() = binding.indicator
    val itemContainer: View
        get() = binding.itemContainer

    fun setClickListener(onClick: () -> Unit) {
        itemContainer.setOnClickListener { onClick() }
    }

    /** Sets the primary text title */
    fun setPrimaryText(title: String?) {
        primaryText.text = title
    }

    /** Sets the secondary text title */
    fun setSecondaryText(title: String?) {
        secondaryText.text = title
    }

    fun setSwitchEnabled(enabled: Boolean) {
        if (enabled) {
            trailingSwitch.setEnabledOpacity(true)
            trailingSwitch.isEnabled = true
        } else {
            trailingSwitch.setEnabledOpacity(false)
            trailingSwitch.isEnabled = false
        }
    }

    /** Sets the switch value */
    fun setIsChecked(isChecked: Boolean) {
        trailingSwitch.isChecked = isChecked
    }

    /** Sets the checked change listener for the switch */
    fun setOnCheckedChangeListener(onCheckedChangeListener: OnCheckedChangeListener) {
        trailingSwitch.setOnCheckedChangeListener(onCheckedChangeListener)
    }

    /** Allows to set a new value to the switch, without triggering the onChangeListener */
    fun quietlySetIsChecked(
        newCheckedState: Boolean,
        changeListener: OnCheckedChangeListener?,
    ) {
        trailingSwitch.quietlySetIsChecked(newCheckedState, changeListener)
    }

    /** Sets the switch as enabled or not */
    override fun setEnabled(enabled: Boolean) {
        setEnabledOpacity(enabled)
        recursiveEnable(enabled)
        super.setEnabled(enabled)
    }
}
