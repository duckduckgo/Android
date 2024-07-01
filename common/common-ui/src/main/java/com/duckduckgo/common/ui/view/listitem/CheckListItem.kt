/*
 * Copyright (c) 2023 DuckDuckGo
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
import android.text.TextUtils
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.children
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.listitem.CheckListItem.CheckItemStatus.ALERT
import com.duckduckgo.common.ui.view.listitem.CheckListItem.CheckItemStatus.DISABLED
import com.duckduckgo.common.ui.view.listitem.CheckListItem.CheckItemStatus.ENABLED
import com.duckduckgo.common.ui.view.listitem.CheckListItem.CheckItemStatus.WARNING
import com.duckduckgo.common.ui.view.setEnabledOpacity
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.mobile.android.R as CommonR
import com.duckduckgo.mobile.android.R.styleable
import com.duckduckgo.mobile.android.databinding.ViewCheckListItemBinding

class CheckListItem @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = CommonR.attr.checkListItemStyle,
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val binding: ViewCheckListItemBinding by viewBinding()

    init {
        context.obtainStyledAttributes(
            attrs,
            CommonR.styleable.CheckListItem,
            0,
            CommonR.style.Widget_DuckDuckGo_CheckListItem,
        ).apply {

            binding.primaryText.text = getString(CommonR.styleable.CheckListItem_primaryText)
            binding.secondaryText.text = getString(CommonR.styleable.CheckListItem_secondaryText)

            if (hasValue(CommonR.styleable.CheckListItem_primaryTextColorOverlay)) {
                binding.primaryText.setTextColor(getColorStateList(CommonR.styleable.CheckListItem_primaryTextColorOverlay))
            }

            val truncated = getBoolean(CommonR.styleable.CheckListItem_primaryTextTruncated, true)
            if (truncated) {
                binding.primaryText.maxLines = 1
                binding.primaryText.ellipsize = TextUtils.TruncateAt.END
            } else {
                binding.primaryText.maxLines = Int.MAX_VALUE
            }

            if (hasValue(CommonR.styleable.CheckListItem_secondaryTextColorOverlay)) {
                binding.secondaryText.setTextColor(getColorStateList(CommonR.styleable.CheckListItem_secondaryTextColorOverlay))
            }

            val status = if (hasValue(CommonR.styleable.CheckListItem_itemState)) {
                CheckItemStatus.from(getInt(styleable.CheckListItem_itemState, 0))
            } else {
                DISABLED
            }
            setItemStatus(status)

            setPillVisible(getBoolean(CommonR.styleable.CheckListItem_showBetaPill, false))

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

    /** Sets the item click listener */
    fun setClickListener(onClick: () -> Unit) {
        binding.itemContainer.setOnClickListener { onClick() }
    }

    /** Sets the visibility for the pill image */
    fun setPillVisible(isVisible: Boolean) {
        if (isVisible) {
            binding.betaPill.show()
        } else {
            binding.betaPill.gone()
        }
    }

    /** Sets the status: enabled, disabled, warning */
    fun setItemStatus(status: CheckItemStatus) {
        when (status) {
            DISABLED -> binding.leadingIcon.setImageResource(CommonR.drawable.ic_check_grey_round_16)
            ENABLED -> binding.leadingIcon.setImageResource(CommonR.drawable.ic_check_green_round_16)
            WARNING -> binding.leadingIcon.setImageResource(CommonR.drawable.ic_exclamation_yellow_16)
            ALERT -> binding.leadingIcon.setImageResource(CommonR.drawable.ic_exclamation_red_16)
        }
    }

    override fun setEnabled(enabled: Boolean) {
        setEnabledOpacity(enabled)
        recursiveEnable(enabled)
        super.setEnabled(enabled)
    }

    fun View.recursiveEnable(enabled: Boolean) {
        (this as? ViewGroup)?.children?.forEach {
            it.isEnabled = enabled
            it.recursiveEnable(enabled)
        }
    }

    enum class CheckItemStatus {
        DISABLED,
        ENABLED,
        WARNING,
        ALERT,
        ;

        companion object {
            fun from(value: Int): CheckItemStatus {
                // same order as attrs-settings-check-list-item.xml
                return when (value) {
                    0 -> DISABLED
                    1 -> ENABLED
                    2 -> WARNING
                    3 -> ALERT
                    else -> DISABLED
                }
            }
        }
    }
}
