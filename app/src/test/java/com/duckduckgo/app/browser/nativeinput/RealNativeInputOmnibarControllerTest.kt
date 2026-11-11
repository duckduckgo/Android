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

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.omnibar.Omnibar
import com.duckduckgo.app.browser.omnibar.OmnibarView
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class RealNativeInputOmnibarControllerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val omnibar: Omnibar = mock()
    private val rootView: ViewGroup = mock()
    private val nativeInputStateBugKillSwitch = FakeFeatureToggleFactory.create(NativeInputStateBugKillSwitch::class.java)

    private val testee = RealNativeInputOmnibarController(omnibar, rootView, nativeInputStateBugKillSwitch)

    private val fireIconMenu = View(context).apply { id = R.id.fireIconMenu }
    private val plusIconMenu = View(context).apply { id = R.id.plusIconMenu }
    private val tabsMenu = View(context).apply { id = R.id.tabsMenu }
    private val browserMenu = View(context).apply { id = R.id.browserMenu }

    // :app unit tests run without Android resources, so the title's context serves the one string it reads.
    private val aiTitleContext = object : ContextWrapper(context) {
        private val resources = spy(context.resources).also {
            doReturn("Duck.ai").whenever(it).getString(R.string.duckAiHeaderPaidTitle)
        }

        override fun getResources(): Resources = resources
    }
    private val aiTitle = TextView(aiTitleContext).apply {
        id = R.id.aiTitle
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
    private val duckAIFreePill = View(context).apply { id = R.id.duckAIFreePill }
    private val duckAIFreePillUpgrade = View(context).apply { id = R.id.duckAIFreePillUpgrade }
    private val duckAIFreePillChevron = View(context).apply { id = R.id.duckAIFreePillChevron }

    private val omnibarView = object : FrameLayout(context), OmnibarView by mock() {}.apply {
        addView(fireIconMenu)
        addView(plusIconMenu)
        addView(tabsMenu)
        addView(browserMenu)
        addView(aiTitle)
        addView(duckAIFreePill)
        addView(duckAIFreePillUpgrade)
        addView(duckAIFreePillChevron)
    }

    @Test
    fun whenShowingOmnibarButtonsThenPlusIconShownAndFireIconHidden() {
        // Duck.ai chat in split mode: the leading slot is the "+" (new chat), never the fire
        // button, which already lives next to the native input field at the bottom.
        fireIconMenu.visibility = View.VISIBLE
        plusIconMenu.visibility = View.GONE

        testee.showOmnibarButtons(omnibarView)

        assertEquals(View.VISIBLE, plusIconMenu.visibility)
        assertEquals(View.GONE, fireIconMenu.visibility)
        assertEquals(View.VISIBLE, tabsMenu.visibility)
        assertEquals(View.VISIBLE, browserMenu.visibility)
    }

    @Test
    fun whenHidingOmnibarButtonsThenPlusAndFireAndMenusHidden() {
        fireIconMenu.visibility = View.VISIBLE
        plusIconMenu.visibility = View.VISIBLE
        tabsMenu.visibility = View.VISIBLE
        browserMenu.visibility = View.VISIBLE

        testee.hideOmnibarButtons(omnibarView)

        assertEquals(View.GONE, plusIconMenu.visibility)
        assertEquals(View.GONE, fireIconMenu.visibility)
        assertEquals(View.GONE, tabsMenu.visibility)
        assertEquals(View.GONE, browserMenu.visibility)
    }

    @Test
    fun whenTierUpdatedToFreeWithoutOverlayActiveThenFreePillStaysHiddenAndNoImpression() {
        whenever(omnibar.omnibarView).thenReturn(omnibarView)
        duckAIFreePill.visibility = View.VISIBLE
        var impressions = 0

        testee.updateTierTitle(DuckAiTier.Free, {}) { impressions++ }

        assertEquals(View.GONE, duckAIFreePill.visibility)
        assertEquals(0, impressions) // pill not rendered -> no free-label impression
    }

    @Test
    fun whenOverlayActiveAndTierFreeThenFreePillShownAndImpressionFired() {
        whenever(omnibar.omnibarView).thenReturn(omnibarView)
        var impressions = 0
        testee.updateTierTitle(DuckAiTier.Free, {}) { impressions++ }

        testee.hideBackground()

        assertEquals(View.VISIBLE, duckAIFreePill.visibility)
        assertEquals(1, impressions) // upgradeable pill rendered -> exactly one impression
    }

    @Test
    fun whenOverlayActiveAndTierFreeNoUpgradeThenPillShownButUpgradeAffordanceHiddenAndNoImpression() {
        whenever(omnibar.omnibarView).thenReturn(omnibarView)
        var impressions = 0
        testee.updateTierTitle(DuckAiTier.FreeNoUpgrade, {}) { impressions++ }

        testee.hideBackground()

        // The "Free Plan" pill stays visible, but the upgrade CTA/chevron are gone and it isn't tappable.
        assertEquals(View.VISIBLE, duckAIFreePill.visibility)
        assertEquals(View.GONE, duckAIFreePillUpgrade.visibility)
        assertEquals(View.GONE, duckAIFreePillChevron.visibility)
        assertEquals(View.GONE, aiTitle.visibility)
        assertFalse(duckAIFreePill.isClickable)
        assertEquals(0, impressions) // non-upgradeable pill -> no free-label impression
    }

    @Test
    fun whenOverlayActiveAndTierUnknownThenTitleAndPillHidden() {
        whenever(omnibar.omnibarView).thenReturn(omnibarView)
        aiTitle.visibility = View.VISIBLE
        duckAIFreePill.visibility = View.VISIBLE

        testee.hideBackground()

        assertEquals(View.GONE, aiTitle.visibility)
        assertEquals(View.GONE, duckAIFreePill.visibility)
    }

    @Test
    fun whenOverlayActiveAndTierPaidThenTitleShownAndPillHidden() {
        whenever(omnibar.omnibarView).thenReturn(omnibarView)
        aiTitle.visibility = View.GONE
        duckAIFreePill.visibility = View.VISIBLE
        testee.updateTierTitle(DuckAiTier.Paid, {}) {}

        testee.hideBackground()

        assertEquals(View.VISIBLE, aiTitle.visibility)
        assertEquals(View.GONE, duckAIFreePill.visibility)
    }

    @Test
    fun whenOverlayRestoredThenLaterFreeTierUpdateDoesNotResurrectFreePill() {
        whenever(omnibar.omnibarView).thenReturn(omnibarView)
        testee.updateTierTitle(DuckAiTier.Free, {}) {}
        testee.hideBackground()
        assertEquals(View.VISIBLE, duckAIFreePill.visibility)

        testee.restore()
        testee.updateTierTitle(DuckAiTier.Free, {}) {}

        assertEquals(View.GONE, duckAIFreePill.visibility)
    }

    @Test
    fun whenPillReDisplayedAfterRestoreThenImpressionFiresAgain() {
        whenever(omnibar.omnibarView).thenReturn(omnibarView)
        var impressions = 0

        testee.updateTierTitle(DuckAiTier.Free, {}) { impressions++ }
        testee.hideBackground() // pill shown -> impression 1

        testee.restore()
        testee.updateTierTitle(DuckAiTier.Free, {}) { impressions++ }
        testee.hideBackground() // pill shown again after restore -> impression 2 (latch reset)

        assertEquals(2, impressions)
    }

    @Test
    fun whenUpgradeablePillStaysVisibleAcrossReapplyThenImpressionFiresOnce() {
        whenever(omnibar.omnibarView).thenReturn(omnibarView)
        var impressions = 0

        testee.updateTierTitle(DuckAiTier.Free, {}) { impressions++ }
        testee.hideBackground() // pill shown -> impression 1
        testee.setTierVisible(true) // re-apply while still visible -> no re-fire

        assertEquals(1, impressions)
    }

    @Test
    fun whenOverlayActiveAndTierPaidThenOmnibarLayoutDoesNotRequestAnotherLayout() {
        whenever(omnibar.omnibarView).thenReturn(omnibarView)
        whenever(rootView.findViewById<View?>(R.id.inputModeRoot)).thenReturn(View(context))
        testee.updateTierTitle(DuckAiTier.Paid, {}) {}
        testee.hideBackground()

        layOut(omnibarView)

        assertFalse(aiTitle.isLayoutRequested)
    }

    private fun layOut(view: View) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, 1000, 200)
    }
}
