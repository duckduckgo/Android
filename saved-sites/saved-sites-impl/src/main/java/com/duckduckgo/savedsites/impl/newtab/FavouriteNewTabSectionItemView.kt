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

package com.duckduckgo.savedsites.impl.newtab

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.hide
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.saved.sites.impl.R
import com.duckduckgo.saved.sites.impl.databinding.ViewFavouriteSectionItemBinding

class FavouriteNewTabSectionItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding: ViewFavouriteSectionItemBinding by viewBinding()

    init {
        context.obtainStyledAttributes(
            attrs,
            R.styleable.FavouriteNewTabSectionItemView,
            0,
            0,
        ).apply {
            setPrimaryText(getString(R.styleable.FavouriteNewTabSectionItemView_primaryText))
            if (hasValue(R.styleable.FavouriteNewTabSectionItemView_leadingIcon)) {
                setLeadingIconDrawable(getDrawable(R.styleable.FavouriteNewTabSectionItemView_leadingIcon)!!)
            }

            if (hasValue(R.styleable.FavouriteNewTabSectionItemView_gridItemType)) {
                val itemType = FavouriteItemType.from(getInt(R.styleable.FavouriteNewTabSectionItemView_gridItemType, 0))
                setItemType(itemType)
            } else {
                setItemType(FavouriteItemType.Favicon)
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
    fun setItemType(itemType: FavouriteItemType) {
        when (itemType) {
            FavouriteItemType.Favicon -> setAsFavicon()
            FavouriteItemType.Placeholder -> setAsPlaceholder()
        }
    }

    private fun setAsPlaceholder() {
        binding.quickAccessFaviconCard.setOnClickListener { }
        binding.quickAccessTitle.hide()
        binding.quickAccessFavicon.gone()
        binding.quickAccessFaviconCard.gone()
        binding.gridItemPlaceholder.show()
    }

    private fun setAsFavicon() {
        binding.quickAccessTitle.show()
        binding.quickAccessFavicon.show()
        binding.quickAccessFaviconCard.show()
        binding.gridItemPlaceholder.gone()
    }

    enum class FavouriteItemType {
        Favicon,
        Placeholder,
        ;

        companion object {
            fun from(type: Int): FavouriteItemType {
                // same order as attrs-lists.xml
                return when (type) {
                    0 -> Favicon
                    1 -> Placeholder
                    else -> Favicon
                }
            }
        }
    }
}
