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

package com.duckduckgo.common.ui.view.divider

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.mobile.android.R
import com.duckduckgo.mobile.android.databinding.ViewHorizontalDividerBinding

class HorizontalDivider @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding: ViewHorizontalDividerBinding by viewBinding()

    init {
        val typedArray =
            context.obtainStyledAttributes(
                attrs,
                R.styleable.HorizontalDivider,
                0,
                R.style.Widget_DuckDuckGo_HorizontalDivider,
            )

        val isFullWidth = typedArray.getBoolean(R.styleable.HorizontalDivider_fullWidth, true)
        val defaultPadding = typedArray.getBoolean(R.styleable.HorizontalDivider_defaultPadding, true)

        val sidePadding = if (!isFullWidth) {
            resources.getDimensionPixelOffset(R.dimen.horizontalDividerSidePadding)
        } else {
            0
        }

        val topPadding = if (defaultPadding) {
            resources.getDimensionPixelOffset(R.dimen.horizontalDividerTopPadding)
        } else {
            0
        }

        binding.root.setPadding(sidePadding, topPadding, sidePadding, topPadding)

        typedArray.recycle()
    }
}
