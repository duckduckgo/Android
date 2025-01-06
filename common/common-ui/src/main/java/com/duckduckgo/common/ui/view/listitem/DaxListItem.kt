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

package com.duckduckgo.common.ui.view.listitem

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.text.TextUtils.TruncateAt
import android.util.AttributeSet
import android.view.View
import android.widget.CompoundButton.OnCheckedChangeListener
import android.widget.ImageView
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import com.duckduckgo.common.ui.view.DaxSwitch
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.quietlySetIsChecked
import com.duckduckgo.common.ui.view.recursiveEnable
import com.duckduckgo.common.ui.view.setEnabledOpacity
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.view.text.DaxTextView
import com.duckduckgo.mobile.android.R

abstract class DaxListItem(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int,
) : ConstraintLayout(context, attrs, defStyleAttr) {

    internal abstract val primaryText: DaxTextView
    internal abstract val secondaryText: DaxTextView?
    internal abstract val leadingIcon: ImageView
    internal abstract val leadingIconContainer: View
    internal abstract val trailingIcon: ImageView
    internal abstract val trailingIconContainer: View
    internal abstract val trailingSwitch: DaxSwitch
    internal abstract val betaPill: ImageView?
    internal abstract val itemContainer: View
    internal abstract val verticalPadding: Int

    /** Sets the item click listener */
    fun setClickListener(onClick: () -> Unit) {
        itemContainer.setOnClickListener { onClick() }
    }

    /** Sets the primary text title */
    fun setPrimaryText(title: CharSequence?) {
        primaryText.text = title
    }

    /** Sets primary text color */
    fun setPrimaryTextColor(@ColorRes colorRes: Int) {
        primaryText.setTextColor(ContextCompat.getColorStateList(context, colorRes))
    }

    /** Sets primary text color state list */
    fun setPrimaryTextColorStateList(stateList: ColorStateList?) {
        primaryText.setTextColor(stateList)
    }

    /** Sets primary text truncation */
    fun setPrimaryTextTruncation(truncated: Boolean) {
        if (truncated) {
            primaryText.maxLines = 1
            primaryText.ellipsize = TruncateAt.END
        } else {
            primaryText.maxLines = Int.MAX_VALUE
        }
    }

    /** Sets the secondary text title */
    fun setSecondaryText(title: CharSequence?) {
        secondaryText?.text = title
    }

    /** Sets secondary text color */
    fun setSecondaryTextColor(@ColorRes colorRes: Int) {
        secondaryText?.setTextColor(ContextCompat.getColorStateList(context, colorRes))
    }

    /** Sets secondary text color state list */
    fun setSecondaryTextColorStateList(stateList: ColorStateList?) {
        secondaryText?.setTextColor(stateList)
    }

    /** Sets the leading icon image drawable */
    fun setLeadingIconDrawable(drawable: Drawable) {
        leadingIcon.setImageDrawable(drawable)
        setLeadingIconVisibility(true)
    }

    /** Sets the leading icon image resource */
    fun setLeadingIconResource(@DrawableRes idRes: Int) {
        leadingIcon.setImageResource(idRes)
        setLeadingIconVisibility(true)
    }

    /** Sets the leading image content description */
    fun setLeadingIconContentDescription(description: String) {
        leadingIcon.contentDescription = description
    }

    /** Sets the leading icon image visibility */
    fun setLeadingIconVisibility(visible: Boolean) {
        if (visible) {
            val padding = resources.getDimensionPixelSize(verticalPadding)
            itemContainer.setPadding(0, padding, 0, padding)
            leadingIconContainer.show()
        } else {
            leadingIconContainer.gone()
        }
    }

    /** Sets the leading icon background image type */
    fun setLeadingIconBackgroundType(type: ImageBackground) {
        leadingIconContainer.setBackgroundResource(ImageBackground.background(type))
        setLeadingIconVisibility(true)
    }

    /** Sets the leading icon background image type */
    fun setLeadingIconSize(imageSize: IconSize) {
        val size = resources.getDimensionPixelSize(IconSize.dimension(imageSize))
        leadingIcon.layoutParams.width = size
        leadingIcon.layoutParams.height = size
    }

    /** Sets the leading icon size and the background type
     * The need to be set together because the size of the leading background container
     * depends on the size of the image
     */

    fun setLeadingIconSize(imageSize: IconSize, type: ImageBackground) {
        val iconSize = resources.getDimensionPixelSize(IconSize.dimension(imageSize))
        val backgroundSize = if (type == ImageBackground.None) {
            iconSize
        } else {
            resources.getDimensionPixelSize(R.dimen.listItemImageContainerSize)
        }

        leadingIcon.layoutParams.width = iconSize
        leadingIcon.layoutParams.height = iconSize

        leadingIconContainer.setBackgroundResource(ImageBackground.background(type))
        leadingIconContainer.layoutParams.width = backgroundSize
        leadingIconContainer.layoutParams.height = backgroundSize
    }

    /** Returns the binding of the leading icon */
    fun leadingIcon() = leadingIcon

    /** Sets the trailing icon image drawable */
    fun setTrailingIconDrawable(drawable: Drawable) {
        trailingIcon.setImageDrawable(drawable)
        showTrailingIcon()
    }

    /** Sets the trailing icon image resource */
    fun setTrailingIconResource(@DrawableRes idRes: Int) {
        trailingIcon.setImageResource(idRes)
        showTrailingIcon()
    }

    /** Sets the trailing image content description */
    fun setTrailingIconContentDescription(description: String) {
        trailingIcon.contentDescription = description
    }

    /** Sets the item overflow menu click listener */
    fun setLeadingIconClickListener(onClick: (View) -> Unit) {
        leadingIcon.setOnClickListener { onClick(leadingIcon) }
    }

    /** Sets the item overflow menu click listener */
    fun setTrailingIconClickListener(onClick: (View) -> Unit) {
        trailingIconContainer.setOnClickListener { onClick(trailingIcon) }
    }

    /** Sets the Switch Visible */
    fun showSwitch() {
        trailingIconContainer.gone()
        trailingSwitch.show()
    }

    fun setSwitchEnabled(enabled: Boolean) {
        if (enabled) {
            trailingSwitch.setEnabledOpacity(true)
            trailingSwitch.isEnabled = true
        } else {
            trailingSwitch.setEnabledOpacity(false)
            trailingSwitch.isEnabled = false
        }
    }

    /** Sets the trailing icon Visible */
    fun showTrailingIcon() {
        trailingIconContainer.show()
        trailingSwitch.gone()
    }

    /** Sets the trailing icon size */
    fun setTrailingIconSize(imageSize: IconSize) {
        val size = resources.getDimensionPixelSize(IconSize.dimension(imageSize))
        trailingIcon.layoutParams.width = size
        trailingIcon.layoutParams.height = size
    }

    /** Hides all trailing items */
    fun hideTrailingItems() {
        trailingIconContainer.gone()
        trailingSwitch.gone()
    }

    /** Hides all leading items */
    fun hideLeadingItems() {
        leadingIconContainer.gone()
        val padding = resources.getDimensionPixelSize(R.dimen.twoLineItemVerticalBigPadding)
        itemContainer.setPadding(0, padding, 0, padding)
    }

    /** Sets the trailing image content description */
    fun setPillVisible(isVisible: Boolean) {
        if (isVisible) {
            betaPill?.show()
        } else {
            betaPill?.gone()
        }
    }

    /** Sets the switch value */
    fun setIsChecked(isChecked: Boolean) {
        trailingSwitch.isChecked = isChecked
    }

    /** Sets the checked change listener for the switch */
    fun setOnCheckedChangeListener(onCheckedChangeListener: OnCheckedChangeListener) {
        trailingSwitch.setOnCheckedChangeListener(onCheckedChangeListener)
    }

    /** Allows to set a new value to the switch, without triggering the onChangeListener */
    fun quietlySetIsChecked(
        newCheckedState: Boolean,
        changeListener: OnCheckedChangeListener?,
    ) {
        trailingSwitch.quietlySetIsChecked(newCheckedState, changeListener)
    }

    /** Sets the switch as enabled or not */
    override fun setEnabled(enabled: Boolean) {
        setEnabledOpacity(enabled)
        recursiveEnable(enabled)
        super.setEnabled(enabled)
    }

    enum class ImageBackground {
        None,
        Circular,
        Rounded,
        ;

        companion object {
            fun from(value: Int): ImageBackground {
                // same order as attrs-lists.xml
                return when (value) {
                    0 -> None
                    1 -> Circular
                    2 -> Rounded
                    else -> None
                }
            }

            fun background(type: ImageBackground): Int {
                return when (type) {
                    None -> android.R.color.transparent
                    Circular -> R.drawable.list_item_image_circular_background
                    Rounded -> R.drawable.list_item_image_round_background
                }
            }
        }
    }

    enum class IconSize {
        Small,
        Medium,
        Large,
        ExtraLarge,
        ;

        companion object {
            fun from(size: Int): IconSize {
                // same order as attrs-lists.xml
                return when (size) {
                    0 -> Small
                    1 -> Medium
                    2 -> Large
                    3 -> ExtraLarge
                    else -> Medium
                }
            }

            fun dimension(size: IconSize): Int {
                return when (size) {
                    Small -> R.dimen.listItemImageSmallSize
                    Medium -> R.dimen.listItemImageMediumSize
                    Large -> R.dimen.listItemImageLargeSize
                    ExtraLarge -> R.dimen.listItemImageExtraLargeSize
                }
            }
        }
    }
}
