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
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
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
<<<<<<< HEAD
                R.style.Widget_DuckDuckGo_SectionHeader
            )
=======
                R.style.Widget_DuckDuckGo_SectionHeader)
>>>>>>> feature/david/design_system_templates_complete

        val textAppearanceId =
            typedArray.getResourceId(
                R.styleable.SectionHeaderTextView_android_textAppearance,
<<<<<<< HEAD
                android.R.style.TextAppearance
            )
=======
                android.R.style.TextAppearance)
>>>>>>> feature/david/design_system_templates_complete
        setTextAppearance(context, textAppearanceId)

        typedArray.recycle()
    }
}
