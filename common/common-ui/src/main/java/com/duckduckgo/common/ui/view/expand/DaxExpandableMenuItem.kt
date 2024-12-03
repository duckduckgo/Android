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

package com.duckduckgo.common.ui.view.expand

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.LayoutRes
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.listitem.DaxListItem.ImageBackground
import com.duckduckgo.common.ui.view.listitem.DaxListItem.IconSize
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.view.updateLayoutParams
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.mobile.android.R
import com.duckduckgo.mobile.android.databinding.ViewExpandableMenuItemBinding

/** An expandable layout that shows a two-level layout with an indicator. */
class DaxExpandableMenuItem @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.daxExpandableMenuItemStyle,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding: ViewExpandableMenuItemBinding by viewBinding()

    private var expandedChangedListener: OnExpandedChangedListener? = null

    var customExpandedLayout: View? = null

    private var expanded = false
    private var animating = false
    private var animDuration = 200L

    private val expandAnimator: ValueAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = animDuration
        addUpdateListener {
            val progress = it.animatedValue as Float
            val wrapContentHeight = binding.daxExpandableLayoutContent.measureWrapContentHeight()
            binding.daxExpandableLayoutContent.updateLayoutParams {
                height = (wrapContentHeight * progress).toInt()
            }

            binding.daxExpandableMenuItemExpander.rotation = progress * 180
        }
        addListener(
            object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    super.onAnimationStart(animation)
                    animating = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    super.onAnimationEnd(animation)
                    animating = false
                }
            },
        )
    }

    init {
        context.obtainStyledAttributes(
            attrs,
            R.styleable.DaxExpandableMenuItem,
            0,
            R.style.Widget_DuckDuckGo_DaxExpandableItem,
        ).apply {
            setPrimaryText(getString(R.styleable.DaxExpandableMenuItem_primaryText))

            if (hasValue(R.styleable.DaxExpandableMenuItem_primaryTextColorOverlay)) {
                setPrimaryTextColor(getColorStateList(R.styleable.DaxExpandableMenuItem_primaryTextColorOverlay))
            }

            if (hasValue(R.styleable.DaxExpandableMenuItem_leadingIcon)) {
                setLeadingIconDrawable(getDrawable(R.styleable.DaxExpandableMenuItem_leadingIcon)!!)
            } else {
                setLeadingIconVisibility(false)
            }

            if (hasValue(R.styleable.DaxExpandableMenuItem_leadingIconSize)) {
                val imageSize = IconSize.from(getInt(R.styleable.DaxExpandableMenuItem_leadingIconSize, 1))
                setLeadingIconSize(imageSize)
            }

            if (hasValue(R.styleable.DaxExpandableMenuItem_leadingIconBackground)) {
                val type = ImageBackground.from(getInt(R.styleable.DaxExpandableMenuItem_leadingIconBackground, 0))
                setLeadingIconBackgroundType(type)
            }

            if (hasValue(R.styleable.DaxExpandableMenuItem_secondaryText)) {
                setSecondaryText(getString(R.styleable.DaxExpandableMenuItem_secondaryText))
            }

            if (hasValue(R.styleable.DaxExpandableMenuItem_primaryButtonText)) {
                setPrimaryButtonText(getString(R.styleable.DaxExpandableMenuItem_primaryButtonText))
            } else {
                hidePrimaryButton()
            }

            if (hasValue(R.styleable.DaxExpandableMenuItem_expandedLayout)) {
                setCustomExpandedLayoutResource(getResourceId(R.styleable.DaxExpandableMenuItem_expandedLayout, 0))
            }

            recycle()
        }
    }

    /** Sets the primary text title */
    fun setPrimaryText(title: String?) {
        binding.daxExpandableMenuItemPrimaryText.text = title
    }

    /** Sets primary text color */
    fun setPrimaryTextColor(stateList: ColorStateList?) {
        binding.daxExpandableMenuItemPrimaryText.setTextColor(stateList)
    }

    /** Sets the secondary text title */
    fun setSecondaryText(title: String?) {
        binding.daxExpandableMenuItemSecondaryText.text = title
    }

    /** Sets the leading icon image drawable */
    fun setLeadingIconDrawable(drawable: Drawable) {
        binding.daxExpandableMenuItemIcon.setImageDrawable(drawable)
    }

    /** Sets the leading icon image visibility */
    fun setLeadingIconVisibility(visible: Boolean) {
        if (visible) {
            binding.daxExpandableMenuItemIconBackground.show()
        } else {
            binding.daxExpandableMenuItemIconBackground.gone()
        }
    }

    /** Sets the leading icon background image type */
    fun setLeadingIconBackgroundType(type: ImageBackground) {
        binding.daxExpandableMenuItemIconBackground.setBackgroundResource(ImageBackground.background(type))
        setLeadingIconVisibility(true)
    }

    /** Sets the leading icon background image type */
    fun setLeadingIconSize(imageSize: IconSize) {
        val size = resources.getDimensionPixelSize(IconSize.dimension(imageSize))
        binding.daxExpandableMenuItemIcon.layoutParams.width = size
        binding.daxExpandableMenuItemIcon.layoutParams.height = size
    }

    /** Sets the text for the primary button */
    fun setPrimaryButtonText(text: String?) {
        binding.daxExpandableMenuItemPrimaryButton.text = text
    }

    /** Sets the primary button click listener */
    fun setPrimaryButtonClickListener(onClick: () -> Unit) {
        binding.daxExpandableMenuItemPrimaryButton.setOnClickListener { onClick() }
    }

    /** Hides the primary button */
    fun hidePrimaryButton() {
        binding.daxExpandableMenuItemPrimaryButton.gone()
    }

    fun setExpandedChangeListener(listener: OnExpandedChangedListener) {
        expandedChangedListener = listener
    }

    fun setExpandableMenuCustomLayout(view: View) {
        customExpandedLayout = view
        binding.daxExpandableLayoutContent.removeAllViews()
        binding.daxExpandableLayoutContent.addView(customExpandedLayout)
    }

    private fun setCustomExpandedLayoutResource(@LayoutRes expandedLayoutResource: Int) {
        LayoutInflater.from(context).inflate(expandedLayoutResource, this, false).apply {
            setExpandableMenuCustomLayout(this)
        }
    }

    override fun onFinishInflate() {
        super.onFinishInflate()

        if (expanded) {
            binding.daxExpandableMenuItemExpander.rotation = 180f
        } else {
            binding.daxExpandableLayoutContent.updateLayoutParams {
                height = 0
            }
            binding.daxExpandableMenuItemExpander.rotation = 0f
        }

        // headerView.setOnClickListener {
        //     toggle()
        // }

        binding.daxExpandableMenuItemExpander.setOnClickListener {
            toggle()
        }
    }

    fun toggle() {
        when {
            animating -> {
                expandAnimator.reverse()
                expanded = !expanded
            }

            expanded -> {
                expandAnimator.reverse()
                expanded = false
                expandedChangedListener?.onExpandedChange(this, false)
            }

            else -> {
                expandAnimator.start()
                expanded = true
                expandedChangedListener?.onExpandedChange(this, true)
            }
        }
    }

    /** Builder class for creating [ExpandableLayout]. */
    @DaxExpandableMenuItemDsl
    class Builder(context: Context) {
        private val expandableMenuItem = DaxExpandableMenuItem(context)

        fun setPrimaryText(value: String) = apply {
            this.expandableMenuItem.setPrimaryText(value)
        }
        fun setPrimaryTextColor(value: ColorStateList?) = apply {
            this.expandableMenuItem.setPrimaryTextColor(value)
        }

        fun setSecondaryText(value: String?) = apply {
            this.expandableMenuItem.setSecondaryText(value)
        }

        fun setLeadingIconDrawable(value: Drawable) = apply {
            this.expandableMenuItem.setLeadingIconDrawable(value)
        }

        fun setLeadingIconVisibility(value: Boolean) = apply {
            this.expandableMenuItem.setLeadingIconVisibility(value)
        }

        fun setLeadingIconBackgroundType(value: ImageBackground) = apply {
            this.expandableMenuItem.setLeadingIconBackgroundType(value)
        }

        fun setLeadingIconSize(value: IconSize) = apply {
            this.expandableMenuItem.setLeadingIconSize(value)
        }
        fun setPrimaryButtonText(value: String?) = apply {
            this.expandableMenuItem.setPrimaryButtonText(value)
        }
        fun setPrimaryButtonClickListener(value: () -> Unit) = apply {
            this.expandableMenuItem.setPrimaryButtonClickListener(value)
        }

        fun setExpandedChangeListener(value: OnExpandedChangedListener) = apply {
            this.expandableMenuItem.setExpandedChangeListener(value)
        }

        fun setExpandedLayout(value: View) = apply {
            this.expandableMenuItem.customExpandedLayout = value
        }

        fun build() = this.expandableMenuItem
    }
}

@JvmSynthetic
@DaxExpandableMenuItemDsl
inline fun Context.daxExpandableMenuItem(
    block: DaxExpandableMenuItem.Builder.() -> Unit,
): DaxExpandableMenuItem =
    DaxExpandableMenuItem.Builder(this).apply(block).build()

fun ViewGroup.measureWrapContentHeight(): Int {
    this.measure(
        View.MeasureSpec
            .makeMeasureSpec((this.parent as View).measuredWidth, View.MeasureSpec.EXACTLY),
        View.MeasureSpec
            .makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
    )
    return measuredHeight
}
