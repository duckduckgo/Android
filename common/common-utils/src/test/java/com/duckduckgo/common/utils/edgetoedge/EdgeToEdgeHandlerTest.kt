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

package com.duckduckgo.common.utils.edgetoedge

import android.app.Activity
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

@RunWith(AndroidJUnit4::class)
class EdgeToEdgeHandlerTest {

    private val testee = EdgeToEdgeHandler()

    private lateinit var activity: Activity
    private lateinit var content: ViewGroup
    private lateinit var anchor: View

    @Before
    fun setup() {
        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        content = activity.findViewById(android.R.id.content)
        anchor = View(activity).also { content.addView(it) }
    }

    @Test
    fun whenCoverGestureNavThenScrimSizedToNavigationBarInsetUnderGestureNav() {
        testee.applyNavigationBarScrim(anchor, Color.RED, coverGestureNav = true)

        // Gesture navigation: a navigation bar is present but nothing is tappable there.
        dispatchInsets(navigationBar = 100, tappableElement = 0)

        assertEquals(100, scrim().layoutParams.height)
    }

    @Test
    fun whenNotCoverGestureNavThenScrimCollapsedUnderGestureNav() {
        testee.applyNavigationBarScrim(anchor, Color.RED, coverGestureNav = false)

        dispatchInsets(navigationBar = 100, tappableElement = 0)

        assertEquals(0, scrim().layoutParams.height)
    }

    @Test
    fun whenButtonNavThenScrimSizedToButtonBarHeightRegardlessOfCoverGestureNav() {
        // 2/3-button navigation: the navigation bar is tappable, so both modes report the same height.
        testee.applyNavigationBarScrim(anchor, Color.RED, coverGestureNav = false)

        dispatchInsets(navigationBar = 100, tappableElement = 100)

        assertEquals(100, scrim().layoutParams.height)
    }

    @Test
    fun whenCoverGestureNavThenScrimIncludesBottomDisplayCutout() {
        testee.applyNavigationBarScrim(anchor, Color.RED, coverGestureNav = true)

        dispatchInsets(navigationBar = 40, tappableElement = 0, displayCutout = 80)

        assertEquals(80, scrim().layoutParams.height)
    }

    @Test
    fun whenAppliedTwiceThenOnlyOneScrimAdded() {
        testee.applyNavigationBarScrim(anchor, Color.RED, coverGestureNav = true)
        testee.applyNavigationBarScrim(anchor, Color.RED, coverGestureNav = true)

        val scrims = (0 until content.childCount)
            .map { content.getChildAt(it) }
            .count { it.tag == NAVIGATION_BAR_SCRIM_TAG }
        assertEquals(1, scrims)
    }

    @Test
    fun whenNotFullScreenThenReservesStatusBarAndHorizontalInsetsAsPadding() {
        testee.applyStatusBarAndHorizontalInsets(anchor, installScrim = false, isFullScreen = { false })

        dispatchStatusBarAndCutout(statusBarTop = 63, cutoutLeft = 48, cutoutRight = 24)

        assertEquals(48, anchor.paddingLeft)
        assertEquals(63, anchor.paddingTop)
        assertEquals(24, anchor.paddingRight)
    }

    @Test
    fun whenFullScreenThenReservesNoPadding() {
        testee.applyStatusBarAndHorizontalInsets(anchor, installScrim = false, isFullScreen = { true })

        dispatchStatusBarAndCutout(statusBarTop = 63, cutoutLeft = 48, cutoutRight = 24)

        assertEquals(0, anchor.paddingLeft)
        assertEquals(0, anchor.paddingTop)
        assertEquals(0, anchor.paddingRight)
    }

    @Test
    fun whenFullScreenDefaultThenReservesInsets() {
        testee.applyStatusBarAndHorizontalInsets(anchor, installScrim = false)

        dispatchStatusBarAndCutout(statusBarTop = 63, cutoutLeft = 48)

        assertEquals(48, anchor.paddingLeft)
        assertEquals(63, anchor.paddingTop)
    }

    @Test
    fun whenFullScreenTogglesThenPaddingDropsAndRestoresWithoutAccumulating() {
        var fullScreen = false
        testee.applyStatusBarAndHorizontalInsets(anchor, installScrim = false, isFullScreen = { fullScreen })

        dispatchStatusBarAndCutout(statusBarTop = 63, cutoutLeft = 48, cutoutRight = 24)
        assertEquals(48, anchor.paddingLeft)
        assertEquals(63, anchor.paddingTop)
        assertEquals(24, anchor.paddingRight)

        // Entering fullscreen: the caller flips its state and re-dispatches insets.
        fullScreen = true
        dispatchStatusBarAndCutout(statusBarTop = 63, cutoutLeft = 48, cutoutRight = 24)
        assertEquals(0, anchor.paddingLeft)
        assertEquals(0, anchor.paddingTop)
        assertEquals(0, anchor.paddingRight)

        // Exiting fullscreen restores the original insets, not double them.
        fullScreen = false
        dispatchStatusBarAndCutout(statusBarTop = 63, cutoutLeft = 48, cutoutRight = 24)
        assertEquals(48, anchor.paddingLeft)
        assertEquals(63, anchor.paddingTop)
        assertEquals(24, anchor.paddingRight)
    }

    private fun dispatchStatusBarAndCutout(
        statusBarTop: Int,
        cutoutLeft: Int = 0,
        cutoutRight: Int = 0,
    ) {
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, statusBarTop, 0, 0))
            .setInsets(WindowInsetsCompat.Type.displayCutout(), Insets.of(cutoutLeft, 0, cutoutRight, 0))
            .build()
        ViewCompat.dispatchApplyWindowInsets(anchor, insets)
    }

    private fun scrim(): View = content.findViewWithTag(NAVIGATION_BAR_SCRIM_TAG)

    private fun dispatchInsets(
        navigationBar: Int,
        tappableElement: Int,
        displayCutout: Int = 0,
    ) {
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, navigationBar))
            .setInsets(WindowInsetsCompat.Type.tappableElement(), Insets.of(0, 0, 0, tappableElement))
            .setInsets(WindowInsetsCompat.Type.displayCutout(), Insets.of(0, 0, 0, displayCutout))
            .build()
        ViewCompat.dispatchApplyWindowInsets(scrim(), insets)
    }

    private companion object {
        private const val NAVIGATION_BAR_SCRIM_TAG = "edge_to_edge_navigation_bar_scrim"
    }
}
