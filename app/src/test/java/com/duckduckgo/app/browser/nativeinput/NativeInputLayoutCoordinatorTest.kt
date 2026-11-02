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

import android.app.Activity
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.browser.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

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

    @Test
    fun whenImeAndDriveSuspendReflowThenItResumesOnlyAfterBothEnd() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val ntp = FrameLayout(activity).apply { id = R.id.newTabPage }
        val widget = View(activity)
        val root = FrameLayout(activity).apply {
            addView(ntp)
            addView(widget)
        }
        val coordinator = NativeInputLayoutCoordinator(root, mock())
        coordinator.configureContentOffset(widget, isBottom = false)

        coordinator.setImeAnimating(true)
        coordinator.enableContentLayoutTransition()
        assertNull(ntp.layoutTransition)

        coordinator.suspendContentReflow()
        coordinator.setImeAnimating(false)
        assertNull(ntp.layoutTransition)

        coordinator.resumeContentReflow()
        assertNotNull(ntp.layoutTransition)
    }

    @Test
    fun whenBottomModeThenBrowserContentClearsWidgetRootIncludingCardTopMargin() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val browserLayout = FrameLayout(activity).apply { id = R.id.browserLayout }
        val card = View(activity).apply { id = R.id.inputModeWidgetCard }
        val widget = FrameLayout(activity).apply {
            addView(card, FrameLayout.LayoutParams(MATCH_PARENT, CARD_HEIGHT_PX).apply { topMargin = CARD_TOP_MARGIN_PX })
        }
        val root = FrameLayout(activity).apply {
            addView(FrameLayout(activity).apply { id = R.id.newTabPage })
            addView(browserLayout, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            addView(widget, FrameLayout.LayoutParams(MATCH_PARENT, CARD_TOP_MARGIN_PX + CARD_HEIGHT_PX, Gravity.BOTTOM))
        }
        activity.setContentView(root)
        val coordinator = NativeInputLayoutCoordinator(root, mock())

        coordinator.configureContentOffset(widget, isBottom = true)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(CARD_TOP_MARGIN_PX + CARD_HEIGHT_PX, browserLayout.paddingBottom)
    }

    private companion object {
        private const val NAV_BAR_HEIGHT_PX = 56
        private const val CARD_TOP_MARGIN_PX = 20
        private const val CARD_HEIGHT_PX = 60
    }
}
