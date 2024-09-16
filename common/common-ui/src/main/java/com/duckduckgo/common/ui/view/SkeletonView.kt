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

package com.duckduckgo.common.ui.view

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import com.duckduckgo.mobile.android.R

class SkeletonView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.style.Widget_DuckDuckGo_SkeletonView,
) : AppCompatTextView(context, attrs, defStyleAttr) {

    init {
        context.obtainStyledAttributes(
            attrs,
            R.styleable.SkeletonView,
            0,
            R.style.Widget_DuckDuckGo_SkeletonView,
        ).apply {
            background = getDrawable(R.styleable.SkeletonView_android_background)
            recycle()
        }
    }
}
