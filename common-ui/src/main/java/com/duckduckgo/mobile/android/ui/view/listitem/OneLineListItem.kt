/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.mobile.android.ui.view.listitem

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat
import com.duckduckgo.mobile.android.R
import com.duckduckgo.mobile.android.databinding.ViewSingleLineListItemBinding
import com.duckduckgo.mobile.android.ui.view.gone
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding

class OneLineListItem @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.oneLineListItemStyle
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val binding: ViewSingleLineListItemBinding by viewBinding()

    init {
        context.obtainStyledAttributes(
            attrs,
            R.styleable.OneLineListItem,
            0,
            R.style.Widget_DuckDuckGo_OneLineListItem
        ).apply {

            binding.primaryText.text = getString(R.styleable.OneLineListItem_primaryText)

            if (hasValue(R.styleable.OneLineListItem_leadingIcon)) {
                binding.leadingIcon.setImageDrawable(getDrawable(R.styleable.OneLineListItem_leadingIcon))
            } else {
                binding.leadingIconContainer.gone()
            }

            if (hasValue(R.styleable.OneLineListItem_trailingIcon)) {
                binding.trailingIcon.setImageDrawable(getDrawable(R.styleable.OneLineListItem_trailingIcon))
            } else {
                binding.trailingIcon.gone()
            }

            recycle()
        }
    }

    /** Sets the item title */
    fun setPrimaryText(title: String) {
        binding.primaryText.text = title
    }

    /** Sets the leading icon image resource */
    fun setLeadingIcon(idRes: Int) {
        val drawable = VectorDrawableCompat.create(resources, idRes, null)
        binding.leadingIcon.setImageDrawable(drawable)
    }

    /** Sets the trailing icon image resource */
    fun setTrailingIcon(idRes: Int) {
        val drawable = VectorDrawableCompat.create(resources, idRes, null)
        binding.trailingIcon.setImageDrawable(drawable)
    }

    /** Sets the item click listener */
    fun setClickListener(onClick: () -> Unit) {
        binding.itemContainer.setOnClickListener { onClick() }
    }

    /** Sets the item overflow menu click listener */
    fun setLeadingIconClickListener(onClick: (View) -> Unit) {
        binding.leadingIcon.setOnClickListener { onClick(binding.leadingIcon) }
    }

    /** Sets the item overflow menu click listener */
    fun setTrailingIconClickListener(onClick: (View) -> Unit) {
        binding.trailingIcon.setOnClickListener { onClick(binding.trailingIcon) }
    }
}
