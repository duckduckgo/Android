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
import android.widget.FrameLayout
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat
import com.duckduckgo.mobile.android.R
import com.duckduckgo.mobile.android.databinding.ViewInfoPanelBinding
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding

class InfoPanel : FrameLayout {

    private val binding: ViewInfoPanelBinding by viewBinding()

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(
        context,
        attrs,
        R.style.Widget_DuckDuckGo_InfoPanel
    )

    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle) {
        val attributes = context.obtainStyledAttributes(attrs, R.styleable.InfoPanel)
        setText(attributes.getString(R.styleable.InfoPanel_panelText) ?: "")
        setImageResource(
            attributes.getResourceId(
                R.styleable.InfoPanel_panelDrawable,
                R.drawable.ic_link_color_24
            )
        )
        setBackgroundResource(
            attributes.getResourceId(
                R.styleable.InfoPanel_panelBackground,
                R.drawable.background_blue_tooltip
            )
        )
        attributes.recycle()
    }

    /**
     * Sets the panel text
     */
    fun setText(text: String) {
        binding.infoPanelText.text = text
    }

    /**
     * Sets the panel image resource
     */
    fun setImageResource(idRes: Int) {
        val drawable = VectorDrawableCompat.create(resources, idRes, null)
        binding.infoPanelImage.setImageDrawable(drawable)
    }

}
