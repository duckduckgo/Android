/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.common.ui.view

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import com.duckduckgo.mobile.android.R

/**
 * A custom yellow pill view that displays text with a yellow background.
 * This is a replacement for static pill images like ic_new_pill, ic_beta_pill, etc.
 * that allows for translatable text content.
 */
class DaxYellowPill @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AppCompatTextView(context, attrs, defStyleAttr) {

    init {
        setupView()
        parseAttributes(attrs)
    }

    private fun setupView() {
        setBackgroundResource(R.drawable.background_yellow_pill)

        setTextColor(ContextCompat.getColor(context, R.color.black))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
        setTypeface(typeface, android.graphics.Typeface.BOLD)

        val horizontalPadding = resources.getDimensionPixelSize(R.dimen.yellowPillPaddingHorizontal)
        setPadding(horizontalPadding, 0, horizontalPadding, 0)

        minimumHeight = resources.getDimensionPixelSize(R.dimen.yellowPillHeight)
        height = resources.getDimensionPixelSize(R.dimen.yellowPillHeight)
        gravity = Gravity.CENTER

        maxLines = 1
        isSingleLine = true
        isAllCaps = true
    }

    private fun parseAttributes(attrs: AttributeSet?) {
        context.obtainStyledAttributes(
            attrs,
            R.styleable.DaxYellowPill,
            0,
            0,
        ).apply {
            if (hasValue(R.styleable.DaxYellowPill_android_text)) {
                text = getString(R.styleable.DaxYellowPill_android_text)
            }
            recycle()
        }
    }
}
