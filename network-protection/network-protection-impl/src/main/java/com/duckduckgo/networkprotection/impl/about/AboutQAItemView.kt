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

package com.duckduckgo.networkprotection.impl.about

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import com.duckduckgo.networkprotection.impl.R
import com.duckduckgo.networkprotection.impl.databinding.ItemAboutQaBinding

internal class AboutQAItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {
    private val binding: ItemAboutQaBinding by viewBinding()

    init {
        context.obtainStyledAttributes(
            attrs,
            R.styleable.AboutQAItemView,
        ).apply {
            binding.aboutQuestion.text = getText(R.styleable.AboutQAItemView_question)
            binding.aboutAnswer.text = getText(R.styleable.AboutQAItemView_answer)
            recycle()
        }
    }
}
