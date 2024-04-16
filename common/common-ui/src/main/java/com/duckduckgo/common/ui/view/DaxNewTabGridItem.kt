/*
 * Copyright (c) 2024 DuckDuckGo
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
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.widget.FrameLayout
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.mobile.android.R
import com.duckduckgo.mobile.android.databinding.ViewGridListItemBinding

class DaxNewTabGridItem @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding: ViewGridListItemBinding by viewBinding()

    init {
        context.obtainStyledAttributes(
            attrs,
            R.styleable.DaxNewTabGridItem,
            0,
           0,
        ).apply {
            setPrimaryText(getString(R.styleable.DaxNewTabGridItem_primaryText))
            if (hasValue(R.styleable.DaxNewTabGridItem_leadingIcon)) {
                setLeadingIconDrawable(getDrawable(R.styleable.DaxNewTabGridItem_leadingIcon)!!)
            }
            val isPlaceholder = getBoolean(R.styleable.DaxNewTabGridItem_placeholder, false)
            setAsPlaceholder(isPlaceholder)
            recycle()
        }
    }

    /** Sets the primary text title */
    fun setPrimaryText(text: String?) {
        binding.quickAccessTitle.text = text
    }

    /** Sets the leading icon image drawable */
    fun setLeadingIconDrawable(drawable: Drawable) {
        binding.quickAccessFavicon.setImageDrawable(drawable)
    }

    /** Sets the item click listener */
    fun setClickListener(onClick: () -> Unit) {
        binding.root.setOnClickListener { onClick() }
    }

    /** Sets the item as a placeholder (see https://www.figma.com/file/6Yfag3rmVECFxs9PTYXdIt/New-Tab-page-exploration-(iOS%2FAndroid)?type=design&node-id=590-31843&mode=design&t=s7gAJlxNYHG02uJl-4 */
    fun setAsPlaceholder(isPlaceholder: Boolean){
        if (isPlaceholder){
            binding.quickAccessTitle.gone()
            binding.quickAccessFavicon.gone()
            binding.quickAccessFaviconCard.gone()
            binding.gridItemPlaceholder.show()
        } else {
            binding.quickAccessTitle.show()
            binding.quickAccessFavicon.show()
            binding.quickAccessFaviconCard.show()
            binding.gridItemPlaceholder.gone()
        }
    }
}
