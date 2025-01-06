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

package com.duckduckgo.common.ui.view.listitem

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import com.duckduckgo.common.ui.view.DaxSwitch
import com.duckduckgo.common.ui.view.listitem.DaxListItem.IconSize.Medium
import com.duckduckgo.common.ui.view.text.DaxTextView
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.mobile.android.R
import com.duckduckgo.mobile.android.databinding.ViewOneLineListItemBinding

class OneLineListItem @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.oneLineListItemStyle,
) : DaxListItem(context, attrs, defStyleAttr) {

    private val binding: ViewOneLineListItemBinding by viewBinding()
    override val primaryText: DaxTextView
        get() = binding.primaryText

    override val secondaryText: DaxTextView?
        get() = null
    override val leadingIcon: ImageView
        get() = binding.leadingIcon
    override val leadingIconContainer: View
        get() = binding.leadingIconBackground
    override val trailingIcon: ImageView
        get() = binding.trailingIcon
    override val trailingIconContainer: View
        get() = binding.trailingIconContainer
    override val trailingSwitch: DaxSwitch
        get() = binding.trailingSwitch

    override val betaPill: ImageView?
        get() = null

    override val itemContainer: View
        get() = binding.itemContainer

    override val verticalPadding: Int
        get() = R.dimen.oneLineItemVerticalPadding

    init {
        context.obtainStyledAttributes(
            attrs,
            R.styleable.OneLineListItem,
            0,
            R.style.Widget_DuckDuckGo_OneLineListItem,
        ).apply {

            setPrimaryText(getString(R.styleable.OneLineListItem_primaryText))

            if (hasValue(R.styleable.OneLineListItem_primaryTextColorOverlay)) {
                setPrimaryTextColorStateList(getColorStateList(R.styleable.OneLineListItem_primaryTextColorOverlay))
            }

            val truncated = getBoolean(R.styleable.OneLineListItem_primaryTextTruncated, false)
            setPrimaryTextTruncation(truncated)

            if (hasValue(R.styleable.OneLineListItem_leadingIcon)) {
                setLeadingIconDrawable(getDrawable(R.styleable.OneLineListItem_leadingIcon)!!)
            } else {
                setLeadingIconVisibility(false)
            }

            val leadingIconBackground = if (hasValue(R.styleable.OneLineListItem_leadingIconBackground)) {
                ImageBackground.from(
                    getInt(
                        R.styleable.OneLineListItem_leadingIconBackground,
                        0,
                    ),
                )
            } else {
                ImageBackground.None
            }

            val iconSize = if (hasValue(R.styleable.OneLineListItem_leadingIconSize)) {
                IconSize.from(getInt(R.styleable.OneLineListItem_leadingIconSize, 1))
            } else {
                Medium
            }

            setLeadingIconSize(iconSize, leadingIconBackground)

            val showTrailingIcon = hasValue(R.styleable.OneLineListItem_trailingIcon)
            val showSwitch = getBoolean(R.styleable.OneLineListItem_showSwitch, false)
            when {
                showSwitch -> showSwitch()
                showTrailingIcon -> {
                    setTrailingIconDrawable(getDrawable(R.styleable.OneLineListItem_trailingIcon)!!)
                    showTrailingIcon()
                }
                else -> {
                    hideTrailingItems()
                }
            }
            val switchEnabled = getBoolean(R.styleable.OneLineListItem_switchEnabled, true)
            setSwitchEnabled(switchEnabled)

            recycle()
        }
    }
}
