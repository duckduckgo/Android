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
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import com.duckduckgo.common.ui.view.StatusIndicatorView
import com.duckduckgo.common.ui.view.StatusIndicatorView.Status
import com.duckduckgo.common.ui.view.defaultSelectableItemBackground
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.view.text.DaxTextView
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.mobile.android.R
import com.duckduckgo.mobile.android.databinding.ViewSettingsListItemBinding

class SettingsListItem @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.oneLineListItemStyle,
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val binding: ViewSettingsListItemBinding by viewBinding()

    private val primaryText: DaxTextView
        get() = binding.primaryText
    private val leadingIcon: ImageView
        get() = binding.leadingIcon
    private val betaPill: ImageView
        get() = binding.betaPill
    private val statusIndicator: StatusIndicatorView
        get() = binding.statusIndicator

    init {
        context.obtainStyledAttributes(
            attrs,
            R.styleable.SettingsListItem,
            0,
            R.style.Widget_DuckDuckGo_OneLineListItem,
        ).apply {

            primaryText.text = getString(R.styleable.SettingsListItem_primaryText)

            val leadingIconRes = getResourceId(R.styleable.SettingsListItem_leadingIcon, 0)
            if (leadingIconRes != 0) {
                leadingIcon.setImageResource(leadingIconRes)
                leadingIcon.show()
            } else {
                leadingIcon.gone()
            }

            val pillIcon = getInt(R.styleable.SettingsListItem_pillIcon, 0)
            if (pillIcon != 0) {
                getPillResource(pillIcon)?.let { resId ->
                    betaPill.setImageResource(resId)
                    betaPill.show()
                } ?: run {
                    betaPill.gone()
                }
            } else {
                betaPill.gone()
            }

            val indicatorStatus = Status.from(getInt(R.styleable.SettingsListItem_indicatorStatus, 2))
            statusIndicator.setStatus(indicatorStatus)

            recycle()
        }
    }

    private fun getPillResource(enumValue: Int): Int? {
        return when (enumValue) {
            1 -> R.drawable.ic_beta_pill
            2 -> R.drawable.ic_new_pill
            else -> null
        }
    }

    /** Sets the item click listener */
    fun setClickListener(onClick: (() -> Unit)?) {
        with(binding) {
            if (onClick == null) {
                setOnClickListener(null)
                root.background = null
            } else {
                setOnClickListener { onClick() }
                root.background = ContextCompat.getDrawable(context, context.defaultSelectableItemBackground())
            }
        }
    }

    /** Sets whether the status indicator is on or off */
    fun setStatus(isOn: Boolean) {
        statusIndicator.setStatus(if (isOn) Status.ON else Status.OFF)
    }

    /** Sets the leading icon image resource */
    fun setLeadingIconResource(@DrawableRes idRes: Int) {
        leadingIcon.setImageResource(idRes)
        leadingIcon.show()
    }

    /** Sets the primary text */
    fun setPrimaryText(text: String) {
        primaryText.text = text
    }
}
