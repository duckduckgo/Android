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
import android.widget.ImageView
import com.duckduckgo.common.ui.view.DaxSwitch
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.view.text.DaxTextView
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.mobile.android.R
import com.duckduckgo.mobile.android.databinding.ViewBookmarkTwoLineItemBinding

class BookmarksListItem @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.twoLineListItemStyle,
) : DaxListItem(context, attrs, defStyleAttr) {

    private val binding: ViewBookmarkTwoLineItemBinding by viewBinding()

    override val primaryText: DaxTextView
        get() = binding.primaryText

    override val secondaryText: DaxTextView
        get() = binding.secondaryText
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
        get() = R.dimen.twoLineItemVerticalPadding

    val favoriteStar: ImageView?
        get() = binding.favoriteStar

    /** Sets the favorite star visibility */
    fun setFavoriteStarVisible(isVisible: Boolean) {
        if (isVisible) {
            favoriteStar?.show()
        } else {
            favoriteStar?.gone()
        }
    }

    init {
        context.obtainStyledAttributes(
            attrs,
            R.styleable.TwoLineListItem,
            0,
            R.style.Widget_DuckDuckGo_TwoLineListItem,
        ).apply {
            setPrimaryText(getString(R.styleable.TwoLineListItem_primaryText))
            setSecondaryText(getString(R.styleable.TwoLineListItem_secondaryText))

            if (hasValue(R.styleable.TwoLineListItem_primaryTextColorOverlay)) {
                setPrimaryTextColorStateList(getColorStateList(R.styleable.TwoLineListItem_primaryTextColorOverlay))
            }

            val truncated = getBoolean(R.styleable.TwoLineListItem_primaryTextTruncated, true)
            setPrimaryTextTruncation(truncated)

            if (hasValue(R.styleable.TwoLineListItem_secondaryTextColorOverlay)) {
                setSecondaryTextColorStateList(getColorStateList(R.styleable.TwoLineListItem_secondaryTextColorOverlay))
            }

            if (hasValue(R.styleable.TwoLineListItem_leadingIcon)) {
                setLeadingIconDrawable(getDrawable(R.styleable.TwoLineListItem_leadingIcon)!!)
            } else {
                hideLeadingItems()
            }

            if (hasValue(R.styleable.TwoLineListItem_leadingIconBackground)) {
                val type = ImageBackground.from(
                    getInt(
                        R.styleable.TwoLineListItem_leadingIconBackground,
                        0,
                    ),
                )
                setLeadingIconBackgroundType(type)
            }

            if (hasValue(R.styleable.TwoLineListItem_leadingIconSize)) {
                val imageSize = IconSize.from(getInt(R.styleable.TwoLineListItem_leadingIconSize, 1))
                setLeadingIconSize(imageSize)
            }

            if (hasValue(R.styleable.TwoLineListItem_primaryTextColorOverlay)) {
                setPrimaryTextColorStateList(getColorStateList(R.styleable.TwoLineListItem_primaryTextColorOverlay))
            }

            setFavoriteStarVisible(getBoolean(R.styleable.TwoLineListItem_showBetaPill, false))

            val showTrailingIcon = hasValue(R.styleable.TwoLineListItem_trailingIcon)
            val showSwitch = getBoolean(R.styleable.TwoLineListItem_showSwitch, false)
            when {
                showSwitch -> showSwitch()
                showTrailingIcon -> {
                    setTrailingIconDrawable(getDrawable(R.styleable.TwoLineListItem_trailingIcon)!!)
                    showTrailingIcon()
                }
                else -> {
                    hideTrailingItems()
                }
            }
            val switchEnabled = getBoolean(R.styleable.TwoLineListItem_switchEnabled, true)
            setSwitchEnabled(switchEnabled)

            recycle()
        }
    }
}
