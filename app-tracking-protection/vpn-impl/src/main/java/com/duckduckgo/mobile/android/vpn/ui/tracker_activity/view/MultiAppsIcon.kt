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

package com.duckduckgo.mobile.android.vpn.ui.tracker_activity.view

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import androidx.constraintlayout.widget.ConstraintLayout
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.mobile.android.vpn.databinding.ViewMultiAppsIconBinding

class MultiAppsIcon @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : ConstraintLayout(context, attrs) {

    private val binding: ViewMultiAppsIconBinding by viewBinding()

    fun setIcons(drawableList: List<Drawable>) {
        if (drawableList.isEmpty()) return

        when (drawableList.size) {
            1 -> setIcon(drawableList[0])
            2 -> setIcons(drawableList[0], drawableList[1])
            3 -> setIcons(drawableList[0], drawableList[1], drawableList[2])
            else -> setIcons(drawableList[0], drawableList[1], drawableList[2], drawableList[3])
        }
    }

    private fun setIcons(first: Drawable, second: Drawable, third: Drawable, fourth: Drawable) {
        binding.threeOrFourIconsContainerFirstIcon.setImageDrawable(first)
        binding.threeOrFourIconsContainerSecondIcon.setImageDrawable(second)
        binding.threeOrFourIconsContainerThirdIcon.setImageDrawable(third)
        binding.threeOrFourIconsContainerFourthIcon.setImageDrawable(fourth)

        binding.threeOrFourIconsContainer.show()
        binding.twoIconsContainer.gone()
        binding.oneIconContainer.gone()
    }

    private fun setIcons(first: Drawable, second: Drawable, third: Drawable) {
        binding.threeOrFourIconsContainerFirstIcon.setImageDrawable(first)
        binding.threeOrFourIconsContainerSecondIcon.setImageDrawable(second)
        binding.threeOrFourIconsContainerThirdIcon.setImageDrawable(third)

        binding.threeOrFourIconsContainerFourthIcon.gone()

        binding.threeOrFourIconsContainer.show()
        binding.twoIconsContainer.gone()
        binding.oneIconContainer.gone()
    }

    private fun setIcons(first: Drawable, second: Drawable) {
        binding.twoIconsContainerFirstIcon.setImageDrawable(first)
        binding.twoIconsContainerSecondIcon.setImageDrawable(second)

        binding.twoIconsContainer.show()
        binding.threeOrFourIconsContainer.gone()
        binding.oneIconContainer.gone()
    }

    private fun setIcon(first: Drawable) {
        binding.oneIconContainer.setImageDrawable(first)

        binding.oneIconContainer.show()
        binding.threeOrFourIconsContainer.gone()
        binding.twoIconsContainer.gone()
    }
}
