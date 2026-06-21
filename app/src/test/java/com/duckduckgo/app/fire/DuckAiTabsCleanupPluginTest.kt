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

package com.duckduckgo.app.fire

import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.browsermode.api.BrowserModeDataProvider
import com.duckduckgo.browsermode.api.FireModeAvailability
import com.duckduckgo.dataclearing.api.plugin.ClearableData
import com.duckduckgo.duckchat.api.DuckChat
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class DuckAiTabsCleanupPluginTest {

    @Mock
    private lateinit var mockRegularTabRepository: TabRepository

    @Mock
    private lateinit var mockFireTabRepository: TabRepository

    @Mock
    private lateinit var mockTabRepositoryProvider: BrowserModeDataProvider<TabRepository>

    @Mock
    private lateinit var mockFireModeAvailability: FireModeAvailability

    @Mock
    private lateinit var mockDuckChat: DuckChat

    private lateinit var testee: DuckAiTabsCleanupPlugin

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        runBlocking {
            whenever(mockRegularTabRepository.getTabs()).thenReturn(emptyList())
            whenever(mockFireTabRepository.getTabs()).thenReturn(emptyList())
        }
        whenever(mockTabRepositoryProvider.forMode(BrowserMode.REGULAR)).thenReturn(mockRegularTabRepository)
        whenever(mockTabRepositoryProvider.forMode(BrowserMode.FIRE)).thenReturn(mockFireTabRepository)
        // Default: fire mode is available (flag on)
        whenever(mockFireModeAvailability.isAvailable()).thenReturn(true)

        testee = DuckAiTabsCleanupPlugin(mockTabRepositoryProvider, mockFireModeAvailability, mockDuckChat)
    }

    // ── flag ON: Regular-mode tests (existing behavior, routed through forMode(REGULAR)) ──

    @Test
    fun `DuckChats All closes only DuckAi tabs in regular repo when flag on`() = runTest {
        val duckAiTab = TabEntity(tabId = "duck-ai", url = "https://duck.ai")
        val browserTab = TabEntity(tabId = "browser", url = "https://example.com")
        whenever(mockRegularTabRepository.getTabs()).thenReturn(listOf(duckAiTab, browserTab))
        whenever(mockDuckChat.isDuckChatUrl(eq("https://duck.ai".toUri()))).thenReturn(true)
        whenever(mockDuckChat.isDuckChatUrl(eq("https://example.com".toUri()))).thenReturn(false)

        testee.onClearData(setOf(ClearableData.DuckChats.All))

        verify(mockRegularTabRepository).deleteTabs(listOf("duck-ai"))
    }

    @Test
    fun `DuckChats All with no DuckAi tabs does not call deleteTabs`() = runTest {
        whenever(mockRegularTabRepository.getTabs()).thenReturn(listOf(TabEntity(tabId = "browser", url = "https://example.com")))
        whenever(mockDuckChat.isDuckChatUrl(any())).thenReturn(false)

        testee.onClearData(setOf(ClearableData.DuckChats.All))

        verify(mockRegularTabRepository, never()).deleteTabs(any())
        verify(mockFireTabRepository, never()).deleteTabs(any())
    }

    @Test
    fun `DuckChats All with no open tabs is a no-op`() = runTest {
        testee.onClearData(setOf(ClearableData.DuckChats.All))

        verify(mockRegularTabRepository, never()).deleteTabs(any())
        verify(mockFireTabRepository, never()).deleteTabs(any())
    }

    @Test
    fun `DuckChats Selected closes only the matching tab`() = runTest {
        val matchingTab = TabEntity(tabId = "tab1", url = "https://duck.ai?chatID=abc")
        val unrelatedDuckAiTab = TabEntity(tabId = "tab2", url = "https://duck.ai?chatID=zzz")
        val browserTab = TabEntity(tabId = "tab3", url = "https://example.com")
        whenever(mockRegularTabRepository.getTabs()).thenReturn(listOf(matchingTab, unrelatedDuckAiTab, browserTab))
        whenever(mockDuckChat.isDuckChatUrl(eq("https://duck.ai?chatID=abc".toUri()))).thenReturn(true)
        whenever(mockDuckChat.isDuckChatUrl(eq("https://duck.ai?chatID=zzz".toUri()))).thenReturn(true)
        whenever(mockDuckChat.isDuckChatUrl(eq("https://example.com".toUri()))).thenReturn(false)

        testee.onClearData(setOf(ClearableData.DuckChats.SelectedForMode(setOf("https://duck.ai?chatID=abc"), BrowserMode.REGULAR)))

        verify(mockRegularTabRepository).deleteTabs(listOf("tab1"))
    }

    @Test
    fun `DuckChats Selected with multiple matching urls closes all of them`() = runTest {
        val t1 = TabEntity(tabId = "tab1", url = "https://duck.ai?chatID=a")
        val t2 = TabEntity(tabId = "tab2", url = "https://duck.ai?chatID=b")
        val t3 = TabEntity(tabId = "tab3", url = "https://example.com")
        whenever(mockRegularTabRepository.getTabs()).thenReturn(listOf(t1, t2, t3))
        whenever(mockDuckChat.isDuckChatUrl(eq("https://duck.ai?chatID=a".toUri()))).thenReturn(true)
        whenever(mockDuckChat.isDuckChatUrl(eq("https://duck.ai?chatID=b".toUri()))).thenReturn(true)
        whenever(mockDuckChat.isDuckChatUrl(eq("https://example.com".toUri()))).thenReturn(false)

        testee.onClearData(
            setOf(ClearableData.DuckChats.SelectedForMode(setOf("https://duck.ai?chatID=a", "https://duck.ai?chatID=b"), BrowserMode.REGULAR)),
        )

        verify(mockRegularTabRepository).deleteTabs(listOf("tab1", "tab2"))
    }

    @Test
    fun `DuckChats Selected with no matching tab does not call deleteTabs`() = runTest {
        val unrelated = TabEntity(tabId = "tab1", url = "https://duck.ai?chatID=other")
        whenever(mockRegularTabRepository.getTabs()).thenReturn(listOf(unrelated))
        whenever(mockDuckChat.isDuckChatUrl(any())).thenReturn(true)

        testee.onClearData(setOf(ClearableData.DuckChats.SelectedForMode(setOf("https://duck.ai?chatID=abc"), BrowserMode.REGULAR)))

        verify(mockRegularTabRepository, never()).deleteTabs(any())
    }

    @Test
    fun `DuckChats Selected closes every tab sharing the same chatID`() = runTest {
        val t1 = TabEntity(tabId = "tab1", url = "https://duck.ai?chatID=abc")
        val t2 = TabEntity(tabId = "tab2", url = "https://duck.ai?chatID=abc")
        val t3 = TabEntity(tabId = "tab3", url = "https://duck.ai?chatID=abc")
        whenever(mockRegularTabRepository.getTabs()).thenReturn(listOf(t1, t2, t3))
        whenever(mockDuckChat.isDuckChatUrl(any())).thenReturn(true)

        testee.onClearData(setOf(ClearableData.DuckChats.SelectedForMode(setOf("https://duck.ai?chatID=abc"), BrowserMode.REGULAR)))

        verify(mockRegularTabRepository).deleteTabs(listOf("tab1", "tab2", "tab3"))
    }

    @Test
    fun `tab url drifted from canonical chat url still closes the tab by chatID`() = runTest {
        // Tab URLs that share the same chatID but differ in path, extra params, fragments —
        // the kinds of drift that happen as the user interacts with the chat over a session.
        val original = TabEntity(tabId = "tab1", url = "https://duck.ai?chatID=abc")
        val withPath = TabEntity(tabId = "tab2", url = "https://duck.ai/chat?chatID=abc")
        val withExtraParams = TabEntity(tabId = "tab3", url = "https://duck.ai?chatID=abc&model=gpt&session=xyz")
        val withFragment = TabEntity(tabId = "tab4", url = "https://duck.ai?chatID=abc#message-5")
        whenever(mockRegularTabRepository.getTabs()).thenReturn(listOf(original, withPath, withExtraParams, withFragment))
        whenever(mockDuckChat.isDuckChatUrl(any())).thenReturn(true)

        testee.onClearData(setOf(ClearableData.DuckChats.SelectedForMode(setOf("https://duck.ai?chatID=abc"), BrowserMode.REGULAR)))

        verify(mockRegularTabRepository).deleteTabs(listOf("tab1", "tab2", "tab3", "tab4"))
    }

    @Test
    fun `Selected url with no chatID query param is a no-op`() = runTest {
        val anyTab = TabEntity(tabId = "tab1", url = "https://duck.ai?chatID=abc")
        whenever(mockRegularTabRepository.getTabs()).thenReturn(listOf(anyTab))
        whenever(mockDuckChat.isDuckChatUrl(any())).thenReturn(true)

        testee.onClearData(setOf(ClearableData.DuckChats.SelectedForMode(setOf("https://duck.ai"), BrowserMode.REGULAR)))

        verify(mockRegularTabRepository, never()).deleteTabs(any())
    }

    @Test
    fun `DuckChats Selected with empty url set is a no-op`() = runTest {
        testee.onClearData(setOf(ClearableData.DuckChats.SelectedForMode(emptySet(), BrowserMode.REGULAR)))

        verify(mockRegularTabRepository, never()).deleteTabs(any())
    }

    @Test
    fun `unrelated ClearableData is a no-op`() = runTest {
        testee.onClearData(setOf(ClearableData.BrowserData.All, ClearableData.Tabs.All))

        verify(mockRegularTabRepository, never()).deleteTabs(any())
        verify(mockFireTabRepository, never()).deleteTabs(any())
    }

    // ── flag ON: Fire-mode tests ──

    @Test
    fun `DuckChats All closes tabs in both regular and fire repos when flag on`() = runTest {
        val regularDuckAiTab = TabEntity(tabId = "regular-duck-ai", url = "https://duck.ai")
        val fireDuckAiTab = TabEntity(tabId = "fire-duck-ai", url = "https://duck.ai")
        whenever(mockRegularTabRepository.getTabs()).thenReturn(listOf(regularDuckAiTab))
        whenever(mockFireTabRepository.getTabs()).thenReturn(listOf(fireDuckAiTab))
        whenever(mockDuckChat.isDuckChatUrl(eq("https://duck.ai".toUri()))).thenReturn(true)

        testee.onClearData(setOf(ClearableData.DuckChats.All))

        verify(mockRegularTabRepository).deleteTabs(listOf("regular-duck-ai"))
        verify(mockFireTabRepository).deleteTabs(listOf("fire-duck-ai"))
    }

    @Test
    fun `DuckChats AllForMode FIRE closes tabs in fire repo when flag on`() = runTest {
        val fireDuckAiTab = TabEntity(tabId = "fire-duck-ai", url = "https://duck.ai")
        whenever(mockFireTabRepository.getTabs()).thenReturn(listOf(fireDuckAiTab))
        whenever(mockDuckChat.isDuckChatUrl(eq("https://duck.ai".toUri()))).thenReturn(true)

        testee.onClearData(setOf(ClearableData.DuckChats.AllForMode(BrowserMode.FIRE)))

        verify(mockFireTabRepository).deleteTabs(listOf("fire-duck-ai"))
        verify(mockRegularTabRepository, never()).deleteTabs(any())
    }

    @Test
    fun `DuckChats SelectedForMode FIRE closes matching tabs in fire repo when flag on`() = runTest {
        val fireDuckAiTab = TabEntity(tabId = "fire-tab1", url = "https://duck.ai?chatID=abc")
        whenever(mockFireTabRepository.getTabs()).thenReturn(listOf(fireDuckAiTab))
        whenever(mockDuckChat.isDuckChatUrl(any())).thenReturn(true)

        testee.onClearData(setOf(ClearableData.DuckChats.SelectedForMode(setOf("https://duck.ai?chatID=abc"), BrowserMode.FIRE)))

        verify(mockFireTabRepository).deleteTabs(listOf("fire-tab1"))
        verify(mockRegularTabRepository, never()).deleteTabs(any())
    }

    // ── flag OFF: revert cases ──

    @Test
    fun `DuckChats All closes only regular repo when flag off`() = runTest {
        whenever(mockFireModeAvailability.isAvailable()).thenReturn(false)
        val regularDuckAiTab = TabEntity(tabId = "regular-duck-ai", url = "https://duck.ai")
        val fireDuckAiTab = TabEntity(tabId = "fire-duck-ai", url = "https://duck.ai")
        whenever(mockRegularTabRepository.getTabs()).thenReturn(listOf(regularDuckAiTab))
        whenever(mockFireTabRepository.getTabs()).thenReturn(listOf(fireDuckAiTab))
        whenever(mockDuckChat.isDuckChatUrl(eq("https://duck.ai".toUri()))).thenReturn(true)

        testee.onClearData(setOf(ClearableData.DuckChats.All))

        verify(mockRegularTabRepository).deleteTabs(listOf("regular-duck-ai"))
        verify(mockFireTabRepository, never()).deleteTabs(any())
    }

    @Test
    fun `DuckChats AllForMode FIRE is a no-op when flag off`() = runTest {
        whenever(mockFireModeAvailability.isAvailable()).thenReturn(false)
        val fireDuckAiTab = TabEntity(tabId = "fire-duck-ai", url = "https://duck.ai")
        whenever(mockFireTabRepository.getTabs()).thenReturn(listOf(fireDuckAiTab))
        whenever(mockDuckChat.isDuckChatUrl(any())).thenReturn(true)

        testee.onClearData(setOf(ClearableData.DuckChats.AllForMode(BrowserMode.FIRE)))

        verify(mockFireTabRepository, never()).deleteTabs(any())
        verify(mockRegularTabRepository, never()).deleteTabs(any())
    }

    @Test
    fun `DuckChats SelectedForMode FIRE is a no-op when flag off`() = runTest {
        whenever(mockFireModeAvailability.isAvailable()).thenReturn(false)
        val fireDuckAiTab = TabEntity(tabId = "fire-tab1", url = "https://duck.ai?chatID=abc")
        whenever(mockFireTabRepository.getTabs()).thenReturn(listOf(fireDuckAiTab))
        whenever(mockDuckChat.isDuckChatUrl(any())).thenReturn(true)

        testee.onClearData(setOf(ClearableData.DuckChats.SelectedForMode(setOf("https://duck.ai?chatID=abc"), BrowserMode.FIRE)))

        verify(mockFireTabRepository, never()).deleteTabs(any())
        verify(mockRegularTabRepository, never()).deleteTabs(any())
    }

    @Test
    fun `DuckChats AllForMode REGULAR closes regular repo even when flag off`() = runTest {
        whenever(mockFireModeAvailability.isAvailable()).thenReturn(false)
        val regularDuckAiTab = TabEntity(tabId = "regular-duck-ai", url = "https://duck.ai")
        whenever(mockRegularTabRepository.getTabs()).thenReturn(listOf(regularDuckAiTab))
        whenever(mockDuckChat.isDuckChatUrl(eq("https://duck.ai".toUri()))).thenReturn(true)

        testee.onClearData(setOf(ClearableData.DuckChats.AllForMode(BrowserMode.REGULAR)))

        verify(mockRegularTabRepository).deleteTabs(listOf("regular-duck-ai"))
        verify(mockFireTabRepository, never()).deleteTabs(any())
    }
}
