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

package com.duckduckgo.newtabpage.impl.shortcuts

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.newtabpage.impl.R
import com.duckduckgo.newtabpage.impl.databinding.ViewShortcutSectionItemBinding

class ShortcutSectionItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding: ViewShortcutSectionItemBinding by viewBinding()

    init {
        context.obtainStyledAttributes(
            attrs,
            R.styleable.ShortcutSectionItemView,
            0,
            0,
        ).apply {
            setPrimaryText(getString(R.styleable.ShortcutSectionItemView_primaryText))
            if (hasValue(R.styleable.ShortcutSectionItemView_leadingIcon)) {
                setLeadingIconDrawable(getDrawable(R.styleable.ShortcutSectionItemView_leadingIcon)!!)
            }

            recycle()
        }
    }

    /** Sets the primary text title */
    fun setPrimaryText(text: String?) {
        binding.quickAccessTitle.text = text
    }

    /** Sets the primary text title */
    fun setPrimaryText(@StringRes text: Int) {
        binding.quickAccessTitle.text = context.getString(text)
    }

    /** Sets the leading icon image drawable */
    fun setLeadingIconDrawable(@DrawableRes drawable: Int) {
        binding.quickAccessFavicon.setImageResource(drawable)
    }

    /** Sets the leading icon image drawable */
    fun setLeadingIconDrawable(drawable: Drawable) {
        binding.quickAccessFavicon.setImageDrawable(drawable)
    }

    /** Sets the item click listener */
    fun setClickListener(onClick: () -> Unit) {
        binding.quickAccessFaviconCard.setOnClickListener { onClick() }
    }

    /** Sets the item click listener */
    fun setLongClickListener(onClick: OnLongClickListener) {
        binding.quickAccessFaviconCard.setOnLongClickListener(onClick)
    }

    @SuppressLint("ClickableViewAccessibility")
    fun setTouchListener(onTouch: OnTouchListener) {
        binding.quickAccessFaviconCard.setOnTouchListener(onTouch)
    }

    fun hideTitle() {
        binding.quickAccessTitle.alpha = 0f
    }

    fun showTitle() {
        binding.quickAccessTitle.alpha = 1f
    }
}
