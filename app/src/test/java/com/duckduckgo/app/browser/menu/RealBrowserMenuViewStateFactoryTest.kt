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

package com.duckduckgo.app.browser.menu

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.duckduckgo.app.browser.DuckDuckGoUrlDetector
import com.duckduckgo.app.browser.SSLErrorType.NONE
import com.duckduckgo.app.browser.WebViewErrorResponse
import com.duckduckgo.app.browser.omnibar.Omnibar.ViewMode
import com.duckduckgo.app.browser.viewstate.BrowserViewState
import com.duckduckgo.browser.ui.browsermenu.BrowserMenuViewState
import com.duckduckgo.browser.ui.browsermenu.PageContextHeaderState
import com.duckduckgo.browser.ui.browsermenu.VpnMenuState
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock

class RealBrowserMenuViewStateFactoryTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private val downloadMenuStateProvider: DownloadMenuStateProvider = mock()
    private val duckDuckGoUrlDetector: DuckDuckGoUrlDetector = mock()

    private lateinit var testee: RealBrowserMenuViewStateFactory

    @Before
    fun setup() {
        testee = RealBrowserMenuViewStateFactory(downloadMenuStateProvider, duckDuckGoUrlDetector)
    }

    @Test
    fun `when creating menu in custom tab mode we return the proper state`() = runTest {
        val browserViewState = BrowserViewState(
            canGoBack = false,
            canGoForward = false,
            canSharePage = true,
            canChangeBrowsingMode = true,
            isDesktopBrowsingMode = false,
            canChangePrivacyProtection = false,
            isPrivacyProtectionDisabled = false,
        )
        val omnibarViewMode = ViewMode.CustomTab(toolbarColor = 100, title = "example")

        val result = testee.create(
            omnibarViewMode = omnibarViewMode,
            viewState = browserViewState,
            customTabsMode = true,
            tabId = "",
            title = null,
            shortUrl = null,
            omnibarText = null,
        )
        val viewState = result as BrowserMenuViewState.CustomTabs

        assertFalse(viewState.canGoBack)
        assertFalse(viewState.canGoForward)
        assertTrue(viewState.canSharePage)
        assertTrue(viewState.canChangeBrowsingMode)
        assertFalse(viewState.isDesktopBrowsingMode)
        assertFalse(viewState.canChangePrivacyProtection)
        assertFalse(viewState.isPrivacyProtectionDisabled)
    }

    @Test
    fun `when creating menu in duck ai mode we return the proper state`() = runTest {
        val browserViewState = BrowserViewState(
            canPrintPage = false,
            canReportSite = false,
            showAutofill = true,
        )

        val omnibarViewMode = ViewMode.DuckAI

        val result = testee.create(
            omnibarViewMode = omnibarViewMode,
            viewState = browserViewState,
            customTabsMode = false,
            tabId = "",
            title = null,
            shortUrl = null,
            omnibarText = null,
        )
        val viewState = result as BrowserMenuViewState.DuckAi

        assertFalse(viewState.canPrintPage)
        assertFalse(viewState.canPrintPage)
        assertTrue(viewState.showAutofill)
    }

    @Test
    fun `when creating menu in error mode we return the proper state`() = runTest {
        val browserViewState = BrowserViewState(
            showDuckChatOption = true,
            vpnMenuState = VpnMenuState.Hidden,
            showAutofill = true,
        )

        val omnibarViewMode = ViewMode.Error

        val result = testee.create(
            omnibarViewMode = omnibarViewMode,
            viewState = browserViewState,
            customTabsMode = false,
            tabId = "",
            title = null,
            shortUrl = null,
            omnibarText = null,
        )
        val viewState = result as BrowserMenuViewState.NewTabPage

        assertTrue(viewState.showDuckChatOption)
        assertTrue(viewState.showAutofill)
        assertTrue(viewState.vpnMenuState == VpnMenuState.Hidden)
    }

    @Test
    fun `when creating menu in ssl warning mode we return the proper state`() = runTest {
        val browserViewState = BrowserViewState(
            showDuckChatOption = true,
            vpnMenuState = VpnMenuState.Hidden,
            showAutofill = true,
        )

        val omnibarViewMode = ViewMode.SSLWarning

        val result = testee.create(
            omnibarViewMode = omnibarViewMode,
            viewState = browserViewState,
            customTabsMode = false,
            tabId = "",
            title = null,
            shortUrl = null,
            omnibarText = null,
        )
        val viewState = result as BrowserMenuViewState.NewTabPage

        assertTrue(viewState.showDuckChatOption)
        assertTrue(viewState.showAutofill)
        assertTrue(viewState.vpnMenuState == VpnMenuState.Hidden)
    }

    @Test
    fun `when creating menu in new tab mode we return the proper state`() = runTest {
        val browserViewState = BrowserViewState(
            showDuckChatOption = true,
            vpnMenuState = VpnMenuState.Hidden,
            showAutofill = true,
        )

        val omnibarViewMode = ViewMode.NewTab

        val result = testee.create(
            omnibarViewMode = omnibarViewMode,
            viewState = browserViewState,
            customTabsMode = false,
            tabId = "",
            title = null,
            shortUrl = null,
            omnibarText = null,
        )
        val viewState = result as BrowserMenuViewState.NewTabPage

        assertTrue(viewState.showDuckChatOption)
        assertTrue(viewState.showAutofill)
        assertTrue(viewState.vpnMenuState == VpnMenuState.Hidden)
    }

    @Test
    fun `when creating menu in malicious site mode we return the proper state`() = runTest {
        val browserViewState = BrowserViewState(
            showDuckChatOption = true,
            vpnMenuState = VpnMenuState.Hidden,
            showAutofill = true,
        )

        val omnibarViewMode = ViewMode.MaliciousSiteWarning

        val result = testee.create(
            omnibarViewMode = omnibarViewMode,
            viewState = browserViewState,
            customTabsMode = false,
            tabId = "",
            title = null,
            shortUrl = null,
            omnibarText = null,
        )
        val viewState = result as BrowserMenuViewState.NewTabPage

        assertTrue(viewState.showDuckChatOption)
        assertTrue(viewState.showAutofill)
        assertTrue(viewState.vpnMenuState == VpnMenuState.Hidden)
    }

    @Test
    fun `when creating menu in browser mode with fullscreen disabled we return the proper state`() = runTest {
        val browserViewState = BrowserViewState(
            canGoBack = true,
            canGoForward = true,
            showDuckChatOption = true,
            canSharePage = true,
            showSelectDefaultBrowserMenuItem = false,
            canSaveSite = true,
            bookmark = null,
            canFireproofSite = true,
            isFireproofWebsite = true,
            isEmailSignedIn = true,
            canChangeBrowsingMode = true,
            isDesktopBrowsingMode = true,
            previousAppLink = null,
            canFindInPage = true,
            addToHomeVisible = true,
            addToHomeEnabled = true,
            canChangePrivacyProtection = true,
            isPrivacyProtectionDisabled = true,
            canReportSite = true,
            showAutofill = true,
            sslError = NONE,
            canPrintPage = true,
        )

        val initialUrl = "https://example.com/page"
        val omnibarViewMode = ViewMode.Browser(initialUrl)

        val result = testee.create(
            omnibarViewMode = omnibarViewMode,
            viewState = browserViewState,
            customTabsMode = false,
            tabId = "",
            title = null,
            shortUrl = null,
            omnibarText = null,
        )
        val viewState = result as BrowserMenuViewState.Browser

        assertTrue(viewState.canGoBack)
        assertTrue(viewState.canGoForward)
        assertTrue(viewState.showDuckChatOption)
        assertFalse(viewState.showNewDuckChatTabOption)
        assertTrue(viewState.canSharePage)
        assertFalse(viewState.showSelectDefaultBrowserMenuItem)
        assertTrue(viewState.canSaveSite)
        assertFalse(viewState.isBookmark)
        assertTrue(viewState.canFireproofSite)
        assertTrue(viewState.isFireproofWebsite)
        assertTrue(viewState.isEmailSignedIn)
        assertTrue(viewState.canChangeBrowsingMode)
        assertTrue(viewState.isDesktopBrowsingMode)
        assertFalse(viewState.hasPreviousAppLink)
        assertTrue(viewState.canFindInPage)
        assertTrue(viewState.addToHomeVisible)
        assertTrue(viewState.canChangePrivacyProtection)
        assertTrue(viewState.isPrivacyProtectionDisabled)
        assertTrue(viewState.canReportSite)
        assertTrue(viewState.showAutofill)
        assertFalse(viewState.isSSLError)
        assertTrue(viewState.canPrintPage)
    }

    @Test
    fun `when site has url and title then page context header is Visible`() = runTest {
        val result = testee.create(
            omnibarViewMode = ViewMode.Browser("https://www.example.com/"),
            viewState = BrowserViewState(),
            customTabsMode = false,
            tabId = "TAB_ID",
            title = "Example Site",
            shortUrl = "example.com",
            omnibarText = null,
        )
        val header = (result as BrowserMenuViewState.Browser).pageContextHeader

        assertTrue(header is PageContextHeaderState.Visible)
        val visible = header as PageContextHeaderState.Visible
        assertEquals("TAB_ID", visible.tabId)
        assertEquals("example.com", visible.shortUrl)
        assertEquals("Example Site", visible.title)
        assertNull(visible.serpLogoUrl)
    }

    @Test
    fun `when serpLogoUrl is provided then page context header contains it`() = runTest {
        val result = testee.create(
            omnibarViewMode = ViewMode.Browser("https://www.example.com/"),
            viewState = BrowserViewState(),
            customTabsMode = false,
            tabId = "TAB_ID",
            title = "Example Site",
            shortUrl = "example.com",
            omnibarText = null,
            serpLogoUrl = "https://duckduckgo.com/logo.png",
        )
        val header = (result as BrowserMenuViewState.Browser).pageContextHeader

        assertTrue(header is PageContextHeaderState.Visible)
        val visible = header as PageContextHeaderState.Visible
        assertEquals("https://duckduckgo.com/logo.png", visible.serpLogoUrl)
    }

    @Test
    fun `when site has url and null title then page context header is Visible with null title`() = runTest {
        val result = testee.create(
            omnibarViewMode = ViewMode.Browser("https://www.example.com/"),
            viewState = BrowserViewState(),
            customTabsMode = false,
            tabId = "TAB_ID",
            title = null,
            shortUrl = "example.com",
            omnibarText = null,
        )
        val header = (result as BrowserMenuViewState.Browser).pageContextHeader

        assertTrue(header is PageContextHeaderState.Visible)
        assertNull((header as PageContextHeaderState.Visible).title)
    }

    @Test
    fun `when no site then page context header is Hidden`() = runTest {
        val result = testee.create(
            omnibarViewMode = ViewMode.Browser("https://www.example.com/"),
            viewState = BrowserViewState(),
            customTabsMode = false,
            tabId = "TAB_ID",
            title = null,
            shortUrl = null,
            omnibarText = null,
        )
        val header = (result as BrowserMenuViewState.Browser).pageContextHeader

        assertEquals(PageContextHeaderState.Hidden, header)
    }

    @Test
    fun `when site is duck ai url then page context header is DuckAi`() = runTest {
        val result = testee.create(
            omnibarViewMode = ViewMode.DuckAI,
            viewState = BrowserViewState(),
            customTabsMode = false,
            tabId = "TAB_ID",
            title = "Duck.ai Chat",
            shortUrl = null,
            omnibarText = null,
        )
        val header = (result as BrowserMenuViewState.DuckAi).pageContextHeader

        assertTrue(header is PageContextHeaderState.DuckAi)
        assertEquals("TAB_ID", (header as PageContextHeaderState.DuckAi).tabId)
    }

    @Test
    fun `when site has error then page context header is Error`() = runTest {
        val result = testee.create(
            omnibarViewMode = ViewMode.Browser("https://www.example.com/"),
            viewState = BrowserViewState(browserError = WebViewErrorResponse.BAD_URL),
            customTabsMode = false,
            tabId = "TAB_ID",
            title = null,
            shortUrl = "example.com",
            omnibarText = null,
        )
        val header = (result as BrowserMenuViewState.Browser).pageContextHeader

        assertTrue(header is PageContextHeaderState.Error)
        assertEquals("example.com", (header as PageContextHeaderState.Error).shortUrl)
    }

    @Test
    fun `when error with no site but omnibarText then page context header is Error with omnibarText`() = runTest {
        val result = testee.create(
            omnibarViewMode = ViewMode.Browser("https://broken-site.com/"),
            viewState = BrowserViewState(browserError = WebViewErrorResponse.BAD_URL),
            customTabsMode = false,
            tabId = "TAB_ID",
            title = null,
            shortUrl = null,
            omnibarText = "broken-site.com",
        )
        val header = (result as BrowserMenuViewState.Browser).pageContextHeader

        assertTrue(header is PageContextHeaderState.Error)
        assertEquals("broken-site.com", (header as PageContextHeaderState.Error).shortUrl)
    }
}
