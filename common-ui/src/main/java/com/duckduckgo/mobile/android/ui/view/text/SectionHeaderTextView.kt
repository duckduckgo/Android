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

@file:Suppress("MemberVisibilityCanBePrivate")

package com.duckduckgo.mobile.android.ui.view.text

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import com.duckduckgo.mobile.android.R

@Deprecated("Use SectionHeaderListItem.kt instead")
class SectionHeaderTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.style.Widget_DuckDuckGo_SectionHeader
) : AppCompatTextView(context, attrs, defStyleAttr) {

    init {
        val typedArray =
            context.obtainStyledAttributes(
                attrs,
                R.styleable.SectionHeaderTextView,
                0,
                R.style.Widget_DuckDuckGo_SectionHeader
            )

        val textAppearanceId =
            typedArray.getResourceId(
                R.styleable.SectionHeaderTextView_android_textAppearance,
                android.R.style.TextAppearance
            )
        setTextAppearance(textAppearanceId)

        val paddingTop = typedArray.getDimensionPixelOffset(R.styleable.SectionHeaderTextView_android_paddingTop, 24)
        val paddingStart = typedArray.getDimensionPixelOffset(R.styleable.SectionHeaderTextView_android_paddingStart, 20)
        val paddingBottom = typedArray.getDimensionPixelOffset(R.styleable.SectionHeaderTextView_android_paddingBottom, 16)
        val paddingEnd = typedArray.getDimensionPixelOffset(R.styleable.SectionHeaderTextView_android_paddingEnd, 20)

        setPadding(paddingStart, paddingTop, paddingEnd, paddingBottom)

        typedArray.recycle()
    }
}
