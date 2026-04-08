/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.browser.ui.browsermenu

import android.app.Application
import android.view.View
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.browser.api.ui.BrowserMenuPlugin
import com.duckduckgo.browser.ui.R
import com.duckduckgo.browser.ui.browsermenu.PageContextHeaderState.Visible
import com.duckduckgo.common.ui.view.StatusIndicatorView
import com.duckduckgo.common.utils.plugins.PluginPoint
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.annotation.Config
import com.duckduckgo.mobile.android.R as MobileR

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class BrowserMenuBottomSheetTest {
    private val mockFaviconManager: FaviconManager = mock()
    private val emptyPluginPoint = object : PluginPoint<BrowserMenuPlugin> {
        override fun getPlugins(): Collection<BrowserMenuPlugin> = emptyList()
    }
    private lateinit var dialog: BrowserMenuBottomSheet

    @Before
    fun setUp() {
        val appContext = ApplicationProvider.getApplicationContext<Application>()
        appContext.setTheme(MobileR.style.Theme_DuckDuckGo_Light)
        dialog = BrowserMenuBottomSheet(appContext, mockFaviconManager, emptyPluginPoint, {}, {})
        dialog.show()
    }

    @After
    fun tearDown() {
        if (dialog.isShowing) dialog.dismiss()
    }

    @Test
    fun whenRenderBrowserMenuWithHiddenHeaderThenMenuHeaderIsHidden() {
        val viewState = BrowserMenuViewState.Browser(pageContextHeader = PageContextHeaderState.Hidden)

        dialog.render(viewState)

        assertFalse(menuHeader.isVisible)
    }

    @Test
    fun whenRenderBrowserMenuWithVisibleHeaderThenMenuHeaderIsShown() {
        val viewState = BrowserMenuViewState.Browser(
            pageContextHeader = Visible(
                title = "Test Title",
                shortUrl = "test.com",
                tabId = "tab1",
            ),
        )

        dialog.render(viewState)

        assertTrue(menuHeader.isVisible)
        assertEquals("Test Title", headerTitle.text.toString())
        assertEquals("test.com", headerShortUrl.text.toString())
    }

    @Test
    fun whenRenderBrowserMenuWithVisibleHeaderThenNullTitleRendersEmpty() {
        val viewState = BrowserMenuViewState.Browser(
            pageContextHeader = Visible(
                title = null,
                shortUrl = "test.com",
                tabId = "tab1",
            ),
        )

        dialog.render(viewState)

        assertEquals("", headerTitle.text.toString())
    }

    @Test
    fun whenRenderDuckAiMenuThenMenuHeaderIsShown() {
        val viewState = BrowserMenuViewState.DuckAi(pageContextHeader = PageContextHeaderState.DuckAi(title = "Python help", tabId = "tab1"))

        dialog.render(viewState)

        assertTrue(menuHeader.isVisible)
        assertEquals("Python help", headerTitle.text.toString())
        assertTrue(headerShortUrl.isVisible)
        val expectedShortUrl = dialog.context.getString(R.string.browserMenuDuckChat)
        assertEquals(expectedShortUrl, headerShortUrl.text.toString())
    }

    @Test
    fun whenRenderErrorModeThenMenuHeadIsShownWithShortUrlAndNoTitle() {
        val viewState = BrowserMenuViewState.Browser(
            pageContextHeader = PageContextHeaderState.Error(shortUrl = "test.com"),
        )

        dialog.render(viewState)

        assertTrue(menuHeader.isVisible)
        assertEquals("test.com", headerShortUrl.text.toString())
        assertFalse(headerTitle.isVisible)
    }

    @Test
    fun whenRenderBrowserMenuWithFireMenuItemThenFireMenuItemIsVisible() {
        val viewState = BrowserMenuViewState.Browser(showFireMenuItem = true)

        dialog.render(viewState)

        assertTrue(dialog.fireMenuItem.isVisible)
    }

    @Test
    fun whenRenderBrowserMenuWithoutFireMenuItemThenFireMenuItemIsHidden() {
        val viewState = BrowserMenuViewState.Browser(showFireMenuItem = false)

        dialog.render(viewState)

        assertFalse(dialog.fireMenuItem.isVisible)
    }

    @Test
    fun whenRenderDuckAiMenuThenFireMenuItemIsHidden() {
        val viewState = BrowserMenuViewState.DuckAi(pageContextHeader = PageContextHeaderState.DuckAi(tabId = "tab1", title = null))

        dialog.render(viewState)

        assertFalse(dialog.fireMenuItem.isVisible)
    }

    @Test
    fun whenRenderBrowserMenuWithVpnMenuItemThenVpnMenuItemIsVisible() {
        val viewState = BrowserMenuViewState.Browser(vpnMenuState = VpnMenuState.Subscribed(isVpnEnabled = true))

        dialog.render(viewState)

        assertTrue(dialog.vpnMenuItem.isVisible)
        assertTrue(dialog.vpnMenuItem.findViewById<StatusIndicatorView>(R.id.statusIndicator).isVisible)
    }

    @Test
    fun whenRenderBrowserMenuWithVpnMenuHiddenStateThenVpnMenuItemIsHidden() {
        val viewState = BrowserMenuViewState.Browser(vpnMenuState = VpnMenuState.Hidden)

        dialog.render(viewState)

        assertFalse(dialog.vpnMenuItem.isVisible)
    }

    @Test
    fun whenRenderNewTabPageMenuWithEmailSignedInThenCreateAliasMenuItemIsVisible() {
        val viewState = BrowserMenuViewState.NewTabPage(isEmailSignedIn = true)

        dialog.render(viewState)

        assertTrue(dialog.createAliasMenuItem.isVisible)
    }

    @Test
    fun whenRenderNewTabPageMenuWithEmailNotSignedInThenCreateAliasMenuItemIsHidden() {
        val viewState = BrowserMenuViewState.NewTabPage(isEmailSignedIn = false)

        dialog.render(viewState)

        assertFalse(dialog.createAliasMenuItem.isVisible)
    }

    @Test
    fun whenRenderCustomTabsModeThenMenuActionItemsShouldBeUpdated() {
        val viewState = BrowserMenuViewState.CustomTabs()

        dialog.render(viewState)

        assertTrue(dialog.backMenuItem.isVisible)
        assertTrue(dialog.forwardMenuItem.isVisible)
        assertFalse(dialog.newTabMenuItem.isVisible)
        assertFalse(dialog.newDuckChatTabMenuItem.isVisible)
        assertFalse(dialog.newDuckChatMenuItem.isVisible)
        assertFalse(dialog.settingsMenuItem.isVisible)
        assertFalse(dialog.refreshMenuItem.isVisible)
        assertTrue(dialog.refreshActionMenuItem.isVisible)
    }

    @Test
    fun whenRenderBrowserModeThenMenuActionItemIsUpdated() {
        val viewState = BrowserMenuViewState.Browser(showDuckChatOption = true)

        dialog.render(viewState)

        assertTrue(dialog.backMenuItem.isVisible)
        assertTrue(dialog.forwardMenuItem.isVisible)
        assertTrue(dialog.newTabMenuItem.isVisible)
        assertTrue(dialog.newDuckChatMenuItem.isVisible)
        assertTrue(dialog.settingsMenuItem.isVisible)
        assertTrue(dialog.refreshMenuItem.isVisible)
        assertFalse(dialog.refreshActionMenuItem.isVisible)
    }

    @Test
    fun whenRenderDuckAIModeThenMenuActionItemIsUpdated() {
        val viewState = BrowserMenuViewState.DuckAi()

        dialog.render(viewState)

        assertTrue(dialog.backMenuItem.isVisible)
        assertTrue(dialog.forwardMenuItem.isVisible)
        assertTrue(dialog.newTabMenuItem.isVisible)
        assertTrue(dialog.newDuckChatTabMenuItem.isVisible)
        assertTrue(dialog.settingsMenuItem.isVisible)
        assertTrue(dialog.refreshMenuItem.isVisible)
        assertFalse(dialog.refreshActionMenuItem.isVisible)
    }

    @Test
    fun whenRenderNewTabModeThenMenuActionItemIsUpdated() {
        val viewState = BrowserMenuViewState.NewTabPage(showDuckChatOption = true)

        dialog.render(viewState)

        assertTrue(dialog.backMenuItem.isVisible)
        assertTrue(dialog.forwardMenuItem.isVisible)
        assertTrue(dialog.newTabMenuItem.isVisible)
        assertTrue(dialog.newDuckChatMenuItem.isVisible)
        assertTrue(dialog.settingsMenuItem.isVisible)
        assertFalse(dialog.refreshMenuItem.isVisible)
        assertFalse(dialog.refreshActionMenuItem.isVisible)
    }

    @Test
    fun whenDialogShownThenPeekHeightIsEightyPercentOfScreenHeight() {
        val screenHeight = dialog.context.resources.displayMetrics.heightPixels
        val expectedHeight = screenHeight * BrowserMenuBottomSheet.PEEK_HEIGHT_PERCENT / 100

        assertEquals(expectedHeight, dialog.computePeekHeight())
    }

    // region Helpers

    private val menuHeader: View
        get() = dialog.window!!.decorView.findViewById(R.id.menuHeader)

    private val headerTitle: TextView
        get() = dialog.window!!.decorView.findViewById(R.id.headerTitle)

    private val headerShortUrl: TextView
        get() = dialog.window!!.decorView.findViewById(R.id.headerShortUrl)

    // endregion
}
