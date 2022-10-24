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
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.duckduckgo.mobile.android.R
import com.duckduckgo.mobile.android.databinding.ViewSectionHeaderListItemBinding
import com.duckduckgo.mobile.android.ui.view.gone
import com.duckduckgo.mobile.android.ui.view.show
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding

class SectionHeaderListItem @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val binding: ViewSectionHeaderListItemBinding by viewBinding()

    var titleText: String = ""
        set(value) {
            binding.sectionHeaderText.text = value
            field = value
        }

    init {
        context.obtainStyledAttributes(
            attrs,
            R.styleable.SectionHeaderListItem,
            0,
            defStyleAttr
        ).apply {
            // TODO ADS remove uppercase() method in milestone3
            titleText = getString(R.styleable.SectionHeaderListItem_titleText).orEmpty()
            binding.sectionHeaderText.text = titleText.uppercase()
            binding.sectionHeaderText.setTextColor(ContextCompat.getColorStateList(context, R.color.blue_accent_text_color_selector))

            val showOverflowMenuIcon = getBoolean(R.styleable.SectionHeaderListItem_showOverflowMenu, false)
            if (showOverflowMenuIcon) {
                binding.sectionHeaderOverflowIcon.show()
            } else {
                binding.sectionHeaderOverflowIcon.gone()
            }

            recycle()
        }
    }

    @Deprecated("Delete function in ADS milestone3 when removing uppercase from headers")
    fun revertUpperCaseTitleText() {
        binding.sectionHeaderText.text = titleText
    }

    /** Sets the item title from a string resource*/
    fun setText(@StringRes stringRes: Int) {
        titleText = context.getString(stringRes)
        binding.sectionHeaderText.text = titleText.uppercase() // TODO ADS remove uppercase() method in milestone3
    }

    /** Sets the Trailing Icon Visible */
    fun showOverflowMenuIcon(show: Boolean) {
        if (show) {
            binding.sectionHeaderOverflowIcon.show()
        } else {
            binding.sectionHeaderOverflowIcon.gone()
        }
    }

    /** Sets the item overflow menu click listener */
    fun setOverflowMenuClickListener(onClick: (View) -> Unit) {
        binding.sectionHeaderOverflowIcon.setOnClickListener { onClick(binding.sectionHeaderOverflowIcon) }
    }
}
