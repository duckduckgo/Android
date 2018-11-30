/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.onboarding.ui

import androidx.annotation.ColorInt
import androidx.viewpager.widget.ViewPager
import com.duckduckgo.app.global.view.ColorCombiner


class ColorChangingPageListener(private val colorCombiner: ColorCombiner, private val newColorListener: NewColorListener) :
    ViewPager.OnPageChangeListener {

    interface NewColorListener {
        fun update(@ColorInt color: Int)

        @ColorInt
        fun getColorForPage(position: Int): Int?
    }

    override fun onPageScrolled(
        position: Int,
        positionOffset: Float,
        positionOffsetPixels: Int
    ) {

        val totalPosition = (position + positionOffset).toDouble()

        val leftPageColor = newColorListener.getColorForPage(Math.floor(totalPosition).toInt())
        val rightPage = newColorListener.getColorForPage(position + 1)

        transitionToNewColor(positionOffset, leftPageColor, rightPage)
    }

    private fun transitionToNewColor(positionOffset: Float, @ColorInt leftPageColor: Int?, @ColorInt rightPageColor: Int?) {
        if (positionOffset == 0f || leftPageColor == null || rightPageColor == null) {
            return
        }

        val newColor = colorCombiner.combine(leftPageColor, rightPageColor, positionOffset)
        newColorListener.update(newColor)
    }

    override fun onPageScrollStateChanged(state: Int) {}

    override fun onPageSelected(position: Int) {}
}