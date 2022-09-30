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

package com.duckduckgo.mobile.android.ui.view.divider

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import com.duckduckgo.mobile.android.R
import com.duckduckgo.mobile.android.databinding.ViewHorizontalDividerBinding
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding

class HorizontalDivider @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.horizontalDividerStyle
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding: ViewHorizontalDividerBinding by viewBinding()

    init {
        val typedArray =
            context.obtainStyledAttributes(
                attrs,
                R.styleable.HorizontalDivider,
                0,
                R.style.Widget_DuckDuckGo_HorizontalDivider
            )

        val isFullWidth = typedArray.getBoolean(R.styleable.HorizontalDivider_fullWidth, true)
        val defaultPadding = typedArray.getBoolean(R.styleable.HorizontalDivider_defaultPadding, true)

        if (!isFullWidth) {
            (binding.root.layoutParams as MarginLayoutParams).leftMargin = resources.getDimension(R.dimen.horizontalDividerSidePadding).toInt()
            (binding.root.layoutParams as MarginLayoutParams).rightMargin = resources.getDimension(R.dimen.horizontalDividerSidePadding).toInt()
        }

        if (defaultPadding) {
            (binding.root.layoutParams as MarginLayoutParams).topMargin = resources.getDimension(R.dimen.horizontalDividerTopPadding).toInt()
            (binding.root.layoutParams as MarginLayoutParams).bottomMargin = resources.getDimension(R.dimen.horizontalDividerBottomPadding).toInt()
        }

        typedArray.recycle()
    }
}
