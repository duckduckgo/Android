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

package com.duckduckgo.sync.impl.ui.qrcode

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import androidx.constraintlayout.widget.ConstraintLayout
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.databinding.ViewBlurredCtaBinding

class BlurredTextContainer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val binding: ViewBlurredCtaBinding by viewBinding()

    init {
        context.obtainStyledAttributes(
            attrs,
            R.styleable.BlurredTextContainer,
            0,
            0,
        ).apply {
            setPrimaryText(getString(R.styleable.BlurredTextContainer_primaryText))
            val showTrailingIcon = hasValue(R.styleable.BlurredTextContainer_trailingIcon)
            if (showTrailingIcon) {
                setTrailingIconDrawable(getDrawable(R.styleable.BlurredTextContainer_trailingIcon)!!)
                binding.trailingIconContainer.show()
            } else {
                binding.trailingIconContainer.gone()
            }
            recycle()
        }
    }

    /** Sets the primary text title */
    fun setPrimaryText(title: String?) {
        binding.primaryText.text = title
    }

    /** Sets the trailing icon image drawable */
    fun setTrailingIconDrawable(drawable: Drawable) {
        binding.trailingIcon.setImageDrawable(drawable)
    }
}
