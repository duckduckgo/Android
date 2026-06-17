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
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.omnibar.Omnibar
import com.duckduckgo.app.browser.omnibar.OmnibarView
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class RealNativeInputOmnibarControllerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val omnibar: Omnibar = mock()
    private val rootView: ViewGroup = mock()

    private val testee = RealNativeInputOmnibarController(omnibar, rootView)

    private val fireIconMenu = View(context).apply { id = R.id.fireIconMenu }
    private val plusIconMenu = View(context).apply { id = R.id.plusIconMenu }
    private val tabsMenu = View(context).apply { id = R.id.tabsMenu }
    private val browserMenu = View(context).apply { id = R.id.browserMenu }

    private val aiTitle = TextView(context).apply { id = R.id.aiTitle }
    private val duckAIFreePill = View(context).apply { id = R.id.duckAIFreePill }

    private val omnibarView = object : FrameLayout(context), OmnibarView by mock() {}.apply {
        addView(fireIconMenu)
        addView(plusIconMenu)
        addView(tabsMenu)
        addView(browserMenu)
        addView(aiTitle)
        addView(duckAIFreePill)
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
    fun whenTierUpdatedToFreeWithoutOverlayActiveThenFreePillStaysHidden() {
        whenever(omnibar.omnibarView).thenReturn(omnibarView)
        duckAIFreePill.visibility = View.VISIBLE

        testee.updateTierTitle(DuckAiTier.Free) {}

        assertEquals(View.GONE, duckAIFreePill.visibility)
    }

    @Test
    fun whenOverlayActiveAndTierFreeThenFreePillShown() {
        whenever(omnibar.omnibarView).thenReturn(omnibarView)
        testee.updateTierTitle(DuckAiTier.Free) {}

        testee.hideBackground()

        assertEquals(View.VISIBLE, duckAIFreePill.visibility)
    }

    @Test
    fun whenOverlayRestoredThenLaterFreeTierUpdateDoesNotResurrectFreePill() {
        whenever(omnibar.omnibarView).thenReturn(omnibarView)
        testee.updateTierTitle(DuckAiTier.Free) {}
        testee.hideBackground()
        assertEquals(View.VISIBLE, duckAIFreePill.visibility)

        testee.restore()
        testee.updateTierTitle(DuckAiTier.Free) {}

        assertEquals(View.GONE, duckAIFreePill.visibility)
    }
}
