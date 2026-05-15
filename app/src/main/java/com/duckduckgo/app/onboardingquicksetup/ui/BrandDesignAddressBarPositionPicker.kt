/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.app.onboardingquicksetup.ui

import android.content.Context
import android.content.res.Configuration
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ViewBrandDesignAddressBarPositionPickerBinding
import com.duckduckgo.app.browser.omnibar.OmnibarType

/**
 * Address-bar-position picker: a row of three option cards (top / bottom / split) with their
 * selected/unselected drawables, radio button states, and click → callback wiring.
 *
 * Hosts (the onboarding ADDRESS_BAR_POSITION dialog and the quick-setup bottom sheet) are
 * responsible for everything else — title, visibility, alpha animations, dialog framing.
 */
class BrandDesignAddressBarPositionPicker @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val binding: ViewBrandDesignAddressBarPositionPickerBinding =
        ViewBrandDesignAddressBarPositionPickerBinding.inflate(LayoutInflater.from(context), this)

    private var currentSelection: OmnibarType = OmnibarType.SINGLE_TOP
    private var selectionListener: ((OmnibarType) -> Unit)? = null

    init {
        binding.topOmnibarContainer.setOnClickListener { onOptionClicked(OmnibarType.SINGLE_TOP) }
        binding.bottomOmnibarContainer.setOnClickListener { onOptionClicked(OmnibarType.SINGLE_BOTTOM) }
        binding.splitOmnibarContainer.setOnClickListener { onOptionClicked(OmnibarType.SPLIT) }
    }

    var isSplitOptionVisible: Boolean
        get() = binding.splitOmnibarContainer.isVisible
        set(value) {
            binding.splitOmnibarContainer.isVisible = value
        }

    /**
     * Applies [selection] to the option cards' drawables and radio states.
     *
     * @param animate when true, all three cards crossfade through their back ImageViews over
     * [CROSSFADE_DURATION]. When false, drawables snap instantly (first paint, snap-restore on rotation).
     */
    fun setSelection(selection: OmnibarType, animate: Boolean = false) {
        currentSelection = selection
        val light = isLightMode()
        val topRes = drawableRes(OmnibarType.SINGLE_TOP, selection, light)
        val bottomRes = drawableRes(OmnibarType.SINGLE_BOTTOM, selection, light)
        val splitRes = drawableRes(OmnibarType.SPLIT, selection, light)

        if (animate) {
            crossfade(binding.topOmnibarToggleImage, binding.topOmnibarToggleImageBack, topRes)
            crossfade(binding.bottomOmnibarToggleImage, binding.bottomOmnibarToggleImageBack, bottomRes)
            crossfade(binding.splitOmnibarToggleImage, binding.splitOmnibarToggleImageBack, splitRes)
        } else {
            binding.topOmnibarToggleImage.setImageResource(topRes)
            binding.bottomOmnibarToggleImage.setImageResource(bottomRes)
            binding.splitOmnibarToggleImage.setImageResource(splitRes)
        }

        binding.topOmnibarToggleCheck.isChecked = selection == OmnibarType.SINGLE_TOP
        binding.bottomOmnibarToggleCheck.isChecked = selection == OmnibarType.SINGLE_BOTTOM
        binding.splitOmnibarToggleCheck.isChecked = selection == OmnibarType.SPLIT
    }

    fun setOnSelectionChangedListener(listener: (OmnibarType) -> Unit) {
        selectionListener = listener
    }

    private fun onOptionClicked(option: OmnibarType) {
        if (currentSelection != option) {
            setSelection(option, animate = true)
        }
        selectionListener?.invoke(option)
    }

    private fun isLightMode(): Boolean {
        val nightFlag = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightFlag != Configuration.UI_MODE_NIGHT_YES
    }

    private fun drawableRes(type: OmnibarType, selected: OmnibarType, isLight: Boolean): Int {
        val active = type == selected
        return when (type) {
            OmnibarType.SINGLE_TOP -> when {
                active && isLight -> R.drawable.mobile_toolbar_top_selected_brand_design_update_light
                active -> R.drawable.mobile_toolbar_top_selected_brand_design_update_dark
                isLight -> R.drawable.mobile_toolbar_top_unselected_brand_design_update_light
                else -> R.drawable.mobile_toolbar_top_unselected_brand_design_update_dark
            }
            OmnibarType.SINGLE_BOTTOM -> when {
                active && isLight -> R.drawable.mobile_toolbar_bottom_selected_brand_design_update_light
                active -> R.drawable.mobile_toolbar_bottom_selected_brand_design_update_dark
                isLight -> R.drawable.mobile_toolbar_bottom_unselected_brand_design_update_light
                else -> R.drawable.mobile_toolbar_bottom_unselected_brand_design_update_dark
            }
            OmnibarType.SPLIT -> when {
                active && isLight -> R.drawable.mobile_toolbar_split_selected_brand_design_update_light
                active -> R.drawable.mobile_toolbar_split_selected_brand_design_update_dark
                isLight -> R.drawable.mobile_toolbar_split_unselected_brand_design_update_light
                else -> R.drawable.mobile_toolbar_split_unselected_brand_design_update_dark
            }
        }
    }

    private fun crossfade(front: ImageView, back: ImageView, newRes: Int) {
        back.setImageDrawable(front.drawable)
        front.setImageResource(newRes)
        front.animate().cancel()
        back.animate().cancel()
        back.alpha = 1f
        front.alpha = 0f
        front.animate().alpha(1f).setDuration(CROSSFADE_DURATION).setListener(null)
        back.animate().alpha(0f).setDuration(CROSSFADE_DURATION).setListener(null)
    }

    companion object {
        private const val CROSSFADE_DURATION = 200L
    }
}
