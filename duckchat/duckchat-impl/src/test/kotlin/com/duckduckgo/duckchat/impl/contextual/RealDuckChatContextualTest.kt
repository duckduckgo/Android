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

package com.duckduckgo.duckchat.impl.contextual

import android.view.View
import com.duckduckgo.app.browser.DuckDuckGoUrlDetector
import com.duckduckgo.app.tabs.BrowserNav
import com.duckduckgo.duckchat.impl.DuckChatInternal
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixels
import com.duckduckgo.duckchat.impl.store.DuckChatContextualDataStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class RealDuckChatContextualTest {

    private val duckChatInternal: DuckChatInternal = mock()
    private val browserNav: BrowserNav = mock()
    private val contextualDataStore: DuckChatContextualDataStore = mock()
    private val sessionTimeoutProvider: DuckChatContextualSessionTimeoutProvider = mock()
    private val timeProvider: DuckChatContextualTimeProvider = mock()
    private val duckChatPixels: DuckChatPixels = mock()
    private val duckDuckGoUrlDetector: DuckDuckGoUrlDetector = mock()
    private val contextualEntryPromptStore = RealContextualEntryPromptStore()
    private val anchor: View = mock()

    private val testee = RealDuckChatContextual(
        duckChatInternal,
        browserNav,
        contextualDataStore,
        sessionTimeoutProvider,
        timeProvider,
        duckChatPixels,
        duckDuckGoUrlDetector,
        contextualEntryPromptStore,
    )

    @Test
    fun whenRedesignDisabledThenLaunchedAndDoesNotOpenNewTab() = runTest {
        whenever(duckChatInternal.isContextualSheetRedesignEnabled()).thenReturn(false)
        var askAboutPageCount = 0

        testee.launch("tabId", sourceUrl = null, anchor = anchor) { askAboutPageCount++ }

        assertEquals(1, askAboutPageCount)
        verifyNoInteractions(browserNav)
    }

    @Test
    fun whenAnchorNullThenAskAboutPageInvokedDirectly() = runTest {
        whenever(duckChatInternal.isContextualSheetRedesignEnabled()).thenReturn(true)
        var askAboutPageCount = 0

        testee.launch("tabId", sourceUrl = null, anchor = null) { askAboutPageCount++ }

        assertEquals(1, askAboutPageCount)
        verifyNoInteractions(browserNav)
    }

    @Test
    fun whenChatInProgressThenAskAboutPageInvokedWithoutShowingMenu() = runTest {
        whenever(duckChatInternal.isContextualSheetRedesignEnabled()).thenReturn(true)
        whenever(contextualDataStore.getTabChatUrl("tabId")).thenReturn("https://duckduckgo.com/?chatId=123")
        whenever(contextualDataStore.getTabClosedTimestamp("tabId")).thenReturn(null)
        var askAboutPageCount = 0

        testee.launch("tabId", sourceUrl = null, anchor = anchor) { askAboutPageCount++ }

        assertEquals(1, askAboutPageCount)
    }

    @Test
    fun whenStoredChatSessionExpiredThenTreatedAsNoChatInProgress() = runTest {
        whenever(duckChatInternal.isContextualSheetRedesignEnabled()).thenReturn(true)
        whenever(contextualDataStore.getTabChatUrl("tabId")).thenReturn("https://duckduckgo.com/?chatId=123")
        whenever(contextualDataStore.getTabClosedTimestamp("tabId")).thenReturn(0L)
        whenever(sessionTimeoutProvider.sessionTimeoutMillis()).thenReturn(1L)
        whenever(timeProvider.currentTimeMillis()).thenReturn(1_000L)
        var askAboutPageCount = 0

        testee.launch("tabId", sourceUrl = null, anchor = anchor) { askAboutPageCount++ }

        // Session expired: the menu path is taken instead of opening the chat directly.
        assertEquals(0, askAboutPageCount)
    }
}
