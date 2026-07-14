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

package com.duckduckgo.app.browser.nativeinput

import android.view.Gravity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

@RunWith(AndroidJUnit4::class)
class NativeInputLayoutCoordinatorTest {

    private val coordinator = NativeInputLayoutCoordinator(
        rootView = mock(),
        omnibarState = mock(),
    )

    @Test
    fun whenTopModeThenWidgetIsTopGravityAndOffsetBelowNavBar() {
        val params = coordinator.buildWidgetLayoutParams(isBottom = false, topInsetPx = NAV_BAR_HEIGHT_PX) as CoordinatorLayout.LayoutParams

        assertEquals(Gravity.TOP, params.gravity)
        assertEquals(NAV_BAR_HEIGHT_PX, params.topMargin)
    }

    @Test
    fun whenBottomModeThenWidgetIsBottomGravityAndNotOffset() {
        val params = coordinator.buildWidgetLayoutParams(isBottom = true, topInsetPx = NAV_BAR_HEIGHT_PX) as CoordinatorLayout.LayoutParams

        assertEquals(Gravity.BOTTOM, params.gravity)
        assertEquals(0, params.topMargin)
    }

    @Test
    fun whenTopModeWithoutInsetThenWidgetNotOffset() {
        val params = coordinator.buildWidgetLayoutParams(isBottom = false) as CoordinatorLayout.LayoutParams

        assertEquals(Gravity.TOP, params.gravity)
        assertEquals(0, params.topMargin)
    }

    @Test
    fun whenNavBarParamsThenTopGravityWithGivenHeightAndNoTopMargin() {
        val params = coordinator.buildNavBarLayoutParams(heightPx = NAV_BAR_HEIGHT_PX) as CoordinatorLayout.LayoutParams

        assertEquals(Gravity.TOP, params.gravity)
        assertEquals(NAV_BAR_HEIGHT_PX, params.height)
        assertEquals(0, params.topMargin)
    }

    private companion object {
        private const val NAV_BAR_HEIGHT_PX = 56
    }
}
