/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.mobile.android.ui.view
import android.content.Context
import android.content.res.TypedArray
import android.graphics.Color
import android.util.AttributeSet
import android.util.TypedValue
import androidx.annotation.FontRes
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.res.ResourcesCompat
import com.duckduckgo.mobile.android.R

class SectionHeaderTextView
@JvmOverloads
constructor(
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
                R.style.Widget_DuckDuckGo_SectionHeader)

        val textAppearanceId = typedArray.getResourceId(R .styleable.SectionHeaderTextView_android_textAppearance, android.R.style.TextAppearance)
        val textAppearance: TypedArray = context.obtainStyledAttributes(textAppearanceId, R.styleable.SectionHeaderTextView)

        isAllCaps = textAppearance.getBoolean(R.styleable.SectionHeaderTextView_android_textAllCaps, true)
        val fontResId = textAppearance.getResourceId(R.styleable.SectionHeaderTextView_android_fontFamily, 0)
        typeface = ResourcesCompat.getFont(context, fontResId)
        setTextColor(textAppearance.getColor(R.styleable.SectionHeaderTextView_android_textColor, Color.BLUE))
        setTextSize(TypedValue.COMPLEX_UNIT_PX, textAppearance.getDimension(R.styleable.SectionHeaderTextView_android_textSize, 0.0f))
    }
}
