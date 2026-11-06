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
import android.content.Context
import android.content.res.Resources
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
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import com.duckduckgo.mobile.android.R as CommonR

@RunWith(AndroidJUnit4::class)
class NativeInputLayoutCoordinatorTest {

    private val coordinator = NativeInputLayoutCoordinator(
        rootView = mock(),
        omnibarState = mock(),
        inputWidgetFixEnabled = true,
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
        val coordinator = NativeInputLayoutCoordinator(root, mock(), inputWidgetFixEnabled = true)
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
        val coordinator = NativeInputLayoutCoordinator(root, mock(), inputWidgetFixEnabled = true)

        coordinator.configureContentOffset(widget, isBottom = true)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(CARD_TOP_MARGIN_PX + CARD_HEIGHT_PX, browserLayout.paddingBottom)
    }

    @Test
    fun whenWidgetReattachesThenForcedBottomTranslationFollowsRootSize() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val widget = View(activity)
        val params = FrameLayout.LayoutParams(MATCH_PARENT, 400, Gravity.TOP)
        val root = FrameLayout(activity).apply { addView(widget, params) }
        activity.setContentView(root)
        root.measure(
            View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, 1000, 1000)
        val coordinator = NativeInputLayoutCoordinator(root, mock(), inputWidgetFixEnabled = true)
        coordinator.applyForcedBottomTranslation(widget, isBottom = true)
        assertEquals(600f, widget.translationY)

        root.removeView(widget)
        root.addView(widget, params)
        root.measure(
            View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, 1000, 600)

        assertEquals(200f, widget.translationY)
    }

    @Test
    fun whenForcedBottomIsDisabledThenReattachingDoesNotOverrideTopTranslation() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val widget = View(activity)
        val root = FrameLayout(activity).apply { addView(widget) }
        activity.setContentView(root)
        root.layout(0, 0, 1000, 1000)
        widget.layout(0, 0, 1000, 400)
        val coordinator = NativeInputLayoutCoordinator(root, mock(), inputWidgetFixEnabled = true)
        coordinator.applyForcedBottomTranslation(widget, isBottom = true)
        assertEquals(600f, widget.translationY)

        root.removeView(widget)
        coordinator.applyForcedBottomTranslation(widget, isBottom = false)
        widget.translationY = -NAV_BAR_HEIGHT_PX.toFloat()
        root.addView(widget)
        root.layout(0, 0, 1000, 600)
        widget.layout(0, 0, 1000, 300)

        assertEquals(-NAV_BAR_HEIGHT_PX.toFloat(), widget.translationY)
    }

    @Test
    fun whenNewChatHintsConfiguredThenTheySitKeyline2AboveTheCardNotTheWidgetRoot() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val card = View(activity).apply { id = R.id.inputModeWidgetCard }
        val widget = FrameLayout(activity).apply {
            addView(card, FrameLayout.LayoutParams(MATCH_PARENT, CARD_HEIGHT_PX).apply { topMargin = CARD_TOP_MARGIN_PX })
        }
        val hints = View(activity)
        val resources: Resources = spy(activity.resources)
        doReturn(HINTS_SPACING_PX).whenever(resources).getDimensionPixelSize(CommonR.dimen.keyline_2)
        val root = object : CoordinatorLayout(activity) {
            override fun getResources(): Resources = resources
        }.apply {
            addView(widget, CoordinatorLayout.LayoutParams(MATCH_PARENT, CARD_TOP_MARGIN_PX + CARD_HEIGHT_PX).apply { gravity = Gravity.BOTTOM })
        }
        activity.setContentView(root)
        val coordinator = NativeInputLayoutCoordinator(root, mock(), inputWidgetFixEnabled = true)

        coordinator.configureNewChatHints(widget, hints)
        root.addView(hints)
        shadowOf(Looper.getMainLooper()).idle()
        root.viewTreeObserver.dispatchOnPreDraw()

        assertEquals(widget.top + card.top - HINTS_SPACING_PX, (hints.bottom + hints.translationY).toInt())
    }

    @Test
    fun whenTabViewIsReattachedThenBrowserContentKeepsClearingWidget() {
        val pager = givenTabPager()
        val tab = BottomWidgetTab(pager.context)
        pager.addView(tab.root)
        val coordinator = NativeInputLayoutCoordinator(tab.root, mock(), inputWidgetFixEnabled = true)
        coordinator.configureContentOffset(tab.widget, isBottom = true)
        idle()

        switchAwayAndBack(pager, tab)

        assertEquals(WIDGET_HEIGHT_PX, tab.browserLayout.paddingBottom)
    }

    @Test
    fun whenWidgetIsRemovedThenBrowserContentReturnsToBasePadding() {
        val pager = givenTabPager()
        val tab = BottomWidgetTab(pager.context)
        pager.addView(tab.root)
        val coordinator = NativeInputLayoutCoordinator(tab.root, mock(), inputWidgetFixEnabled = true)
        coordinator.configureContentOffset(tab.widget, isBottom = true)
        idle()

        tab.root.removeView(tab.widget)
        coordinator.onWidgetRemoved()
        idle()

        assertEquals(0, tab.browserLayout.paddingBottom)
    }

    @Test
    fun whenWidgetConfiguredWhileTabWasDetachedIsRemovedThenItNoLongerDrivesBrowserContent() {
        val pager = givenTabPager()
        val tab = BottomWidgetTab(pager.context)
        val coordinator = NativeInputLayoutCoordinator(tab.root, mock(), inputWidgetFixEnabled = true)
        coordinator.configureContentOffset(tab.widget, isBottom = true)
        pager.addView(tab.root)
        idle()
        switchAwayAndBack(pager, tab)

        tab.root.removeView(tab.widget)
        coordinator.onWidgetRemoved()
        tab.browserLayout.setPadding(0, 0, 0, OTHER_SESSION_PADDING_PX)
        pager.viewTreeObserver.dispatchOnGlobalLayout()

        assertEquals(OTHER_SESSION_PADDING_PX, tab.browserLayout.paddingBottom)
    }

    @Test
    fun whenWidgetIsRemovedThenItNoLongerDrivesBrowserContent() {
        val pager = givenTabPager()
        val tab = BottomWidgetTab(pager.context)
        pager.addView(tab.root)
        val coordinator = NativeInputLayoutCoordinator(tab.root, mock(), inputWidgetFixEnabled = true)
        coordinator.configureContentOffset(tab.widget, isBottom = true)
        idle()

        tab.root.removeView(tab.widget)
        coordinator.onWidgetRemoved()
        tab.browserLayout.setPadding(0, 0, 0, OTHER_SESSION_PADDING_PX)
        pager.viewTreeObserver.dispatchOnGlobalLayout()

        assertEquals(OTHER_SESSION_PADDING_PX, tab.browserLayout.paddingBottom)
    }

    @Test
    fun whenTabViewIsReattachedThenAutocompleteKeepsClearingWidget() {
        val pager = givenTabPager()
        val tab = BottomWidgetTab(pager.context)
        pager.addView(tab.root)
        val coordinator = NativeInputLayoutCoordinator(tab.root, mock(), inputWidgetFixEnabled = true)
        coordinator.configureAutocompleteLayout(tab.widget, isBottom = true)
        idle()

        switchAwayAndBack(pager, tab)

        assertEquals(WIDGET_HEIGHT_PX, tab.autoCompleteList.paddingBottom)
    }

    @Test
    fun whenWidgetIsRemovedThenAutocompleteReturnsToBasePadding() {
        val pager = givenTabPager()
        val tab = BottomWidgetTab(pager.context)
        pager.addView(tab.root)
        val coordinator = NativeInputLayoutCoordinator(tab.root, mock(), inputWidgetFixEnabled = true)
        coordinator.configureAutocompleteLayout(tab.widget, isBottom = true)
        idle()

        tab.root.removeView(tab.widget)
        coordinator.onWidgetRemoved()
        idle()

        assertEquals(0, tab.autoCompleteList.paddingBottom)
    }

    @Test
    fun whenLayoutFixDisabledThenSwitchingTabsReturnsBrowserContentToBasePadding() {
        val pager = givenTabPager()
        val tab = BottomWidgetTab(pager.context)
        pager.addView(tab.root)
        val coordinator = NativeInputLayoutCoordinator(tab.root, mock(), inputWidgetFixEnabled = false)
        coordinator.configureContentOffset(tab.widget, isBottom = true)
        idle()

        switchAwayAndBack(pager, tab)

        assertEquals(0, tab.browserLayout.paddingBottom)
    }

    @Test
    fun whenLayoutFixDisabledThenSwitchingTabsReturnsAutocompleteToBasePadding() {
        val pager = givenTabPager()
        val tab = BottomWidgetTab(pager.context)
        pager.addView(tab.root)
        val coordinator = NativeInputLayoutCoordinator(tab.root, mock(), inputWidgetFixEnabled = false)
        coordinator.configureAutocompleteLayout(tab.widget, isBottom = true)
        idle()

        switchAwayAndBack(pager, tab)

        assertEquals(0, tab.autoCompleteList.paddingBottom)
    }

    @Test
    fun whenLayoutFixDisabledThenOnWidgetRemovedLeavesLayoutToDetach() {
        val pager = givenTabPager()
        val tab = BottomWidgetTab(pager.context)
        pager.addView(tab.root)
        val coordinator = NativeInputLayoutCoordinator(tab.root, mock(), inputWidgetFixEnabled = false)
        coordinator.configureAutocompleteLayout(tab.widget, isBottom = true)
        coordinator.configureContentOffset(tab.widget, isBottom = true)
        idle()

        coordinator.onWidgetRemoved()

        assertEquals(WIDGET_HEIGHT_PX, tab.browserLayout.paddingBottom)
        assertEquals(WIDGET_HEIGHT_PX, tab.autoCompleteList.paddingBottom)
    }

    private class BottomWidgetTab(context: Context) {
        val browserLayout = FrameLayout(context).apply { id = R.id.browserLayout }
        val autoCompleteList = FrameLayout(context).apply { id = R.id.autoCompleteSuggestionsList }
        val widget = FrameLayout(context)
        val root = FrameLayout(context).apply {
            addView(FrameLayout(context).apply { id = R.id.newTabPage })
            addView(browserLayout, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            addView(autoCompleteList, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            addView(widget, FrameLayout.LayoutParams(MATCH_PARENT, WIDGET_HEIGHT_PX, Gravity.BOTTOM))
        }
    }

    private fun givenTabPager(): FrameLayout {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        return FrameLayout(activity).also { activity.setContentView(it) }
    }

    // Mirrors the tab pager paging a tab out: it removes the tab view from its container and re-adds the same view
    // when the tab is selected again.
    private fun switchAwayAndBack(pager: FrameLayout, tab: BottomWidgetTab) {
        pager.removeAllViews()
        idle()
        pager.addView(tab.root)
        idle()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private companion object {
        private const val NAV_BAR_HEIGHT_PX = 56
        private const val CARD_TOP_MARGIN_PX = 20
        private const val CARD_HEIGHT_PX = 60
        private const val HINTS_SPACING_PX = 8
        private const val WIDGET_HEIGHT_PX = 80
        private const val OTHER_SESSION_PADDING_PX = 123
    }
}
