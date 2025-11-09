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

package com.duckduckgo.common.ui.view.listitem

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ImageView.ScaleType.FIT_XY
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.listitem.DaxGridItem.GridItemType.Favicon
import com.duckduckgo.common.ui.view.listitem.DaxGridItem.GridItemType.Placeholder
import com.duckduckgo.common.ui.view.listitem.DaxGridItem.GridItemType.Shortcut
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.mobile.android.R
import com.duckduckgo.mobile.android.databinding.ViewGridItemBinding

class DaxGridItem @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding: ViewGridItemBinding by viewBinding()

    init {
        context.obtainStyledAttributes(
            attrs,
            R.styleable.DaxGridItem,
            0,
            0,
        ).apply {
            setPrimaryText(getString(R.styleable.DaxGridItem_primaryText))
            if (hasValue(R.styleable.DaxGridItem_leadingIcon)) {
                setLeadingIconDrawable(getDrawable(R.styleable.DaxGridItem_leadingIcon)!!)
            }

            if (hasValue(R.styleable.DaxGridItem_gridItemType)) {
                val itemType = GridItemType.from(getInt(R.styleable.DaxGridItem_gridItemType, 0))
                setItemType(itemType)
            } else {
                setItemType(Favicon)
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
        binding.root.setOnClickListener { onClick() }
    }

    /** Sets the item click listener */
    fun setLongClickListener(onClick: OnLongClickListener) {
        binding.root.setOnLongClickListener(onClick)
    }

    @SuppressLint("ClickableViewAccessibility")
    fun setTouchListener(onTouch: OnTouchListener) {
        binding.root.setOnTouchListener(onTouch)
    }

    fun favicon(): ImageView {
        return binding.quickAccessFavicon
    }

    fun hideTitle() {
        binding.quickAccessTitle.alpha = 0f
    }

    fun showTitle() {
        binding.quickAccessTitle.alpha = 1f
    }

    /** Sets the item type (see https://www.figma.com/file/6Yfag3rmVECFxs9PTYXdIt/New-Tab-page-exploration-(iOS%2FAndroid)?type=design&node-id=590-31843&mode=design&t=s7gAJlxNYHG02uJl-4 */
    fun setItemType(itemType: GridItemType) {
        when (itemType) {
            Placeholder -> setAsPlaceholder()
            Favicon -> setAsFavicon()
            Shortcut -> setAsShortcut()
        }
        setImageSize(itemType)
    }

    private fun setAsPlaceholder() {
        binding.root.setOnClickListener { }
        binding.quickAccessTitle.gone()
        binding.quickAccessFavicon.gone()
        binding.quickAccessFaviconCard.gone()
        binding.gridItemPlaceholder.show()
        binding.root.setBackgroundResource(R.drawable.background_rounded_transparent)
    }

    private fun setAsFavicon() {
        binding.quickAccessTitle.show()
        binding.quickAccessFavicon.show()
        binding.quickAccessFaviconCard.show()
        binding.gridItemPlaceholder.gone()
        binding.root.setBackgroundResource(R.drawable.selectable_rounded_ripple)
    }

    private fun setAsShortcut() {
        binding.quickAccessTitle.show()
        binding.quickAccessFavicon.show()
        binding.quickAccessFavicon.scaleType = FIT_XY
        binding.quickAccessFaviconCard.show()
        binding.gridItemPlaceholder.gone()
        binding.root.setBackgroundResource(R.drawable.selectable_rounded_ripple)
    }

    private fun setImageSize(itemType: GridItemType) {
        val size = resources.getDimensionPixelSize(GridItemType.dimension(itemType))
        binding.quickAccessFavicon.layoutParams.width = size
        binding.quickAccessFavicon.layoutParams.height = size
    }

    enum class GridItemType {
        Favicon,
        Placeholder,
        Shortcut,
        ;

        companion object {
            fun from(type: Int): GridItemType {
                // same order as attrs-lists.xml
                return when (type) {
                    0 -> Favicon
                    1 -> Placeholder
                    2 -> Shortcut
                    else -> Favicon
                }
            }

            fun dimension(size: GridItemType): Int {
                return when (size) {
                    Shortcut -> R.dimen.gridItemIconSize
                    else -> R.dimen.gridItemImageSize
                }
            }
        }
    }
}
