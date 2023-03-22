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

package com.duckduckgo.app.onboarding.ui.customisationexperiment

import android.content.Context
import android.util.AttributeSet
import androidx.annotation.StringRes
import androidx.constraintlayout.widget.ConstraintLayout
import com.duckduckgo.app.browser.databinding.ViewMultiselectListItemBinding
import com.duckduckgo.mobile.android.R
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding

class MultiselectListItem @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.multiselectListItemStyle,
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val binding: ViewMultiselectListItemBinding by viewBinding()

    var primaryText: String = ""
        private set
    var trailingEmoji: String = ""
        private set
    var isItemSelected: Boolean = false
        set(value) {
            when (value) {
                true -> binding.itemContainer.setBackgroundResource(com.duckduckgo.app.browser.R.drawable.background_multiselect_list_item_selected)
                false -> binding.itemContainer.setBackgroundResource(com.duckduckgo.app.browser.R.drawable.background_multiselect_list_item)
            }
            field = value
        }

    init {
        context.obtainStyledAttributes(
            attrs,
            R.styleable.MultiselectListItem,
            0,
            R.style.Widget_DuckDuckGo_MultiselectListItem,
        ).apply {

            setPrimaryText(getString(R.styleable.MultiselectListItem_primaryText))
            setTrailingEmoji(getString(R.styleable.MultiselectListItem_trailingEmoji))
            isItemSelected = getBoolean(R.styleable.MultiselectListItem_selected, false)

            recycle()
        }
        binding.itemContainer.setOnClickListener { isItemSelected = !isItemSelected }
    }

    /** Sets the item title */
    fun setPrimaryText(title: String?) {
        primaryText = title.orEmpty()
        binding.primaryText.text = primaryText
    }

    /** Sets the item title */
    fun setPrimaryText(@StringRes title: Int) {
        primaryText = context.getString(title)
        binding.primaryText.text = primaryText
    }

    /** Sets the item trailing emoji */
    fun setTrailingEmoji(emojiText: String?) {
        trailingEmoji = emojiText.orEmpty()
        binding.emojiText.text = emojiText
    }

    /** Sets the item trailing emoji */
    fun setTrailingEmoji(@StringRes emojiTextRes: Int) {
        trailingEmoji = context.getString(emojiTextRes)
        binding.emojiText.text = trailingEmoji
    }

    fun setOnClickListener(onClick: () -> Unit) {
        binding.itemContainer.setOnClickListener {
            isItemSelected = !isItemSelected
            onClick.invoke()
        }
    }
}
