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

package com.duckduckgo.mobile.android.ui.view.expand

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.duckduckgo.mobile.android.R
import com.duckduckgo.mobile.android.databinding.ViewExpandableMenuItemBinding
import com.duckduckgo.mobile.android.ui.view.gone
import com.duckduckgo.mobile.android.ui.view.listitem.DaxListItem.ImageBackground
import com.duckduckgo.mobile.android.ui.view.listitem.DaxListItem.LeadingIconSize
import com.duckduckgo.mobile.android.ui.view.show
import com.duckduckgo.mobile.android.ui.view.updateLayoutParams
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding

/** An expandable layout that shows a two-level layout with an indicator. */
class DaxExpandableMenuItem @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.daxExpandableMenuItemStyle
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding: ViewExpandableMenuItemBinding by viewBinding()

    private lateinit var headerView: View
    private lateinit var expandedContentView: ViewGroup
    private lateinit var arrow: View

    private var expanded = false
    private var animating = false
    private var animDuration = 200L

    private val expandAnimator: ValueAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = animDuration
        addUpdateListener {
            val progress = it.animatedValue as Float
            val wrapContentHeight = expandedContentView.measureWrapContentHeight()
            expandedContentView.updateLayoutParams {
                height = (wrapContentHeight * progress).toInt()
            }

            arrow.rotation = progress * 180
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
            R.style.Widget_DuckDuckGo_DaxExpandableItem
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
                val imageSize = LeadingIconSize.from(getInt(R.styleable.DaxExpandableMenuItem_leadingIconSize, 1))
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

            recycle()
        }
    }

    /** Sets the primary text title */
    fun setPrimaryText(title: String?) {
        binding.daxExpandableMenuItemHeader.setPrimaryText(title)
    }

    /** Sets the secondary text title */
    fun setSecondaryText(title: String?) {
        binding.daxExpandableMenuItemSecondaryText.text = title
    }

    /** Sets the leading icon image drawable */
    fun setLeadingIconDrawable(drawable: Drawable) {
        binding.daxExpandableMenuItemHeader.setLeadingIconDrawable(drawable)
    }

    /** Sets the leading icon image visibility */
    fun setLeadingIconVisibility(visible: Boolean) {
        binding.daxExpandableMenuItemHeader.setLeadingIconVisibility(visible)
    }

    /** Sets the leading icon background image type */
    fun setLeadingIconSize(imageSize: LeadingIconSize) {
        binding.daxExpandableMenuItemHeader.setLeadingIconSize(imageSize)
    }

    /** Sets primary text color */
    fun setPrimaryTextColor(stateList: ColorStateList?) {
        binding.daxExpandableMenuItemHeader.setPrimaryTextColorStateList(stateList)
    }

    /** Sets the leading icon background image type */
    fun setLeadingIconBackgroundType(type: ImageBackground) {
        binding.daxExpandableMenuItemHeader.setLeadingIconBackgroundType(type)
    }

    /** Sets the text for the primary button */
    fun setPrimaryButtonText(title: String?) {
        binding.daxExpandableMenuItemPrimaryButton.text = title
    }

    /** Sets the primary button click listener */
    fun setPrimaryButtonClickListener(onClick: () -> Unit) {
        binding.daxExpandableMenuItemPrimaryButton.setOnClickListener { onClick() }
    }

    /** Hides the primary button */
    fun hidePrimaryButton() {
        binding.daxExpandableMenuItemPrimaryButton.gone()
    }

    override fun onFinishInflate() {
        super.onFinishInflate()

        headerView = binding.daxExpandableMenuItemHeader
        expandedContentView = binding.daxExpandableLayoutContent
        arrow = binding.daxExpandableMenuItemExpander

        if (expanded) {
            arrow.rotation = 180f
        } else {
            expandedContentView.updateLayoutParams {
                height = 0
            }
            arrow.rotation = 0f
        }

        // headerView.setOnClickListener {
        //     toggle()
        // }

        arrow.setOnClickListener {
            toggle()
        }
    }

    private fun toggle(){
        when {
            animating -> {
                expandAnimator.reverse()
                expanded = !expanded
            }

            expanded -> {
                expandAnimator.reverse()
                expanded = false
            }

            else -> {
                expandAnimator.start()
                expanded = true
            }
        }
    }
}

fun ViewGroup.measureWrapContentHeight(): Int {
    this.measure(
        View.MeasureSpec
            .makeMeasureSpec((this.parent as View).measuredWidth, View.MeasureSpec.EXACTLY),
        View.MeasureSpec
            .makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
    )
    return measuredHeight
}
