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
import android.view.View
import android.widget.LinearLayout
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat
import com.duckduckgo.mobile.android.R
import com.duckduckgo.mobile.android.databinding.ViewSingleLineListItemBinding
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding

class SingleLineListItem : LinearLayout {

    private val binding: ViewSingleLineListItemBinding by viewBinding()

    constructor(context: Context) : this(context, null)
    constructor(
        context: Context,
        attrs: AttributeSet?
    ) : this(context, attrs, R.style.Widget_DuckDuckGo_SingleLineListItem)

    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyle: Int
    ) : super(context, attrs, defStyle) {
        val attributes = context.obtainStyledAttributes(attrs, R.styleable.SingleLineListItem)
        setTitle(attributes.getString(R.styleable.SingleLineListItem_singleLineItemTitle) ?: "")
        setImageVisibility(
            attributes.getBoolean(R.styleable.SingleLineListItem_singleLineItemImageVisible, true))
        setImageResource(
            attributes.getResourceId(
                R.styleable.SingleLineListItem_singleLineItemDrawable, R.drawable.ic_overflow))
        attributes.recycle()
    }

    /** Sets the item title */
    fun setTitle(title: String) {
        binding.title.text = title
    }

    /** Sets the item image resource */
    fun setImageResource(idRes: Int) {
        val drawable = VectorDrawableCompat.create(resources, idRes, null)
        binding.image.setImageDrawable(drawable)
    }

    /** Sets the item title */
    fun setImageVisibility(isVisible: Boolean) {
        if (isVisible) {
            binding.imageContainer.show()
        } else {
            binding.imageContainer.gone()
        }
    }

    /** Sets the item click listener */
    fun setClickListener(onClick: () -> Unit) {
        binding.itemContainer.setOnClickListener { onClick() }
    }

    /** Sets the item oveflow menu click listener */
    fun setOverflowClickListener(onClick: (View) -> Unit) {
        binding.overflowMenu.setOnClickListener { onClick(binding.overflowMenu) }
    }
}
