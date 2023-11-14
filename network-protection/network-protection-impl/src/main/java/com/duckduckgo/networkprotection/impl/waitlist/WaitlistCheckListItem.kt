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

package com.duckduckgo.networkprotection.impl.waitlist

import android.content.Context
import android.text.TextUtils
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.children
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.setEnabledOpacity
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.mobile.android.R as CommonR
import com.duckduckgo.networkprotection.impl.R
import com.duckduckgo.networkprotection.impl.databinding.ViewWaitlistCheckListItemBinding

class WaitlistCheckListItem @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = CommonR.attr.checkListItemStyle,
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val binding: ViewWaitlistCheckListItemBinding by viewBinding()

    init {
        context.obtainStyledAttributes(
            attrs,
            R.styleable.WaitlistCheckListItem,
            0,
            CommonR.style.Widget_DuckDuckGo_CheckListItem,
        ).apply {

            binding.primaryText.text = getString(R.styleable.WaitlistCheckListItem_primaryText)
            binding.secondaryText.text = getString(R.styleable.WaitlistCheckListItem_secondaryText)

            if (hasValue(R.styleable.WaitlistCheckListItem_primaryTextColorOverlay)) {
                binding.primaryText.setTextColor(getColorStateList(R.styleable.WaitlistCheckListItem_primaryTextColorOverlay))
            }

            val truncated = getBoolean(R.styleable.WaitlistCheckListItem_primaryTextTruncated, true)
            if (truncated) {
                binding.primaryText.maxLines = 1
                binding.primaryText.ellipsize = TextUtils.TruncateAt.END
            } else {
                binding.primaryText.maxLines = Int.MAX_VALUE
            }

            if (hasValue(R.styleable.WaitlistCheckListItem_secondaryTextColorOverlay)) {
                binding.secondaryText.setTextColor(getColorStateList(R.styleable.WaitlistCheckListItem_secondaryTextColorOverlay))
            }

            setLeadingIcon(
                getResourceId(R.styleable.WaitlistCheckListItem_leadingIcon, R.drawable.ic_check_grey_round_16),
            )

            setPillVisible(getBoolean(R.styleable.WaitlistCheckListItem_showBetaPill, false))

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
    fun setLeadingIcon(status: Int) {
        binding.leadingIcon.setImageResource(status)
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
        ;

        companion object {
            fun from(value: Int): CheckItemStatus {
                // same order as attrs-settings-check-list-item.xml
                return when (value) {
                    0 -> DISABLED
                    1 -> ENABLED
                    2 -> WARNING
                    else -> DISABLED
                }
            }
        }
    }
}
