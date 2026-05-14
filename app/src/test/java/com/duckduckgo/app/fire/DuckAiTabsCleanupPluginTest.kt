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
    private lateinit var mockTabRepository: TabRepository

    @Mock
    private lateinit var mockDuckChat: DuckChat

    private lateinit var testee: DuckAiTabsCleanupPlugin

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        runBlocking {
            whenever(mockTabRepository.getTabs()).thenReturn(emptyList())
        }
        testee = DuckAiTabsCleanupPlugin(mockTabRepository, mockDuckChat)
    }

    @Test
    fun whenDuckChatsAllAndDuckAiTabOpen_thenCloseOnlyDuckAiTab() = runTest {
        val duckAiTab = TabEntity(tabId = "duck-ai", url = "https://duck.ai")
        val browserTab = TabEntity(tabId = "browser", url = "https://example.com")
        whenever(mockTabRepository.getTabs()).thenReturn(listOf(duckAiTab, browserTab))
        whenever(mockDuckChat.isDuckChatUrl(eq("https://duck.ai".toUri()))).thenReturn(true)
        whenever(mockDuckChat.isDuckChatUrl(eq("https://example.com".toUri()))).thenReturn(false)

        testee.onClearData(setOf(ClearableData.DuckChats.All))

        verify(mockTabRepository).deleteTabs(listOf("duck-ai"))
    }

    @Test
    fun whenDuckChatsAllAndNoDuckAiTabs_thenDoNotCallDeleteTabs() = runTest {
        whenever(mockTabRepository.getTabs()).thenReturn(listOf(TabEntity(tabId = "browser", url = "https://example.com")))
        whenever(mockDuckChat.isDuckChatUrl(any())).thenReturn(false)

        testee.onClearData(setOf(ClearableData.DuckChats.All))

        verify(mockTabRepository, never()).deleteTabs(any())
    }

    @Test
    fun whenDuckChatsAllAndNoOpenTabs_thenNoOp() = runTest {
        testee.onClearData(setOf(ClearableData.DuckChats.All))

        verify(mockTabRepository, never()).deleteTabs(any())
    }

    @Test
    fun whenDuckChatsSelectedAndTabUrlMatches_thenCloseMatchingTabOnly() = runTest {
        val matchingTab = TabEntity(tabId = "tab1", url = "https://duck.ai?chatID=abc")
        val unrelatedDuckAiTab = TabEntity(tabId = "tab2", url = "https://duck.ai?chatID=zzz")
        val browserTab = TabEntity(tabId = "tab3", url = "https://example.com")
        whenever(mockTabRepository.getTabs()).thenReturn(listOf(matchingTab, unrelatedDuckAiTab, browserTab))

        testee.onClearData(setOf(ClearableData.DuckChats.Selected(setOf("https://duck.ai?chatID=abc"))))

        verify(mockTabRepository).deleteTabs(listOf("tab1"))
    }

    @Test
    fun whenDuckChatsSelectedAndMultipleUrlsMatch_thenCloseAllMatching() = runTest {
        val t1 = TabEntity(tabId = "tab1", url = "https://duck.ai?chatID=a")
        val t2 = TabEntity(tabId = "tab2", url = "https://duck.ai?chatID=b")
        val t3 = TabEntity(tabId = "tab3", url = "https://example.com")
        whenever(mockTabRepository.getTabs()).thenReturn(listOf(t1, t2, t3))

        testee.onClearData(
            setOf(ClearableData.DuckChats.Selected(setOf("https://duck.ai?chatID=a", "https://duck.ai?chatID=b"))),
        )

        verify(mockTabRepository).deleteTabs(listOf("tab1", "tab2"))
    }

    @Test
    fun whenDuckChatsSelectedAndNoTabMatches_thenDoNotCallDeleteTabs() = runTest {
        val unrelated = TabEntity(tabId = "tab1", url = "https://duck.ai?chatID=other")
        whenever(mockTabRepository.getTabs()).thenReturn(listOf(unrelated))

        testee.onClearData(setOf(ClearableData.DuckChats.Selected(setOf("https://duck.ai?chatID=abc"))))

        verify(mockTabRepository, never()).deleteTabs(any())
    }

    @Test
    fun whenDuckChatsSelectedWithEmptyUrlSet_thenNoOp() = runTest {
        testee.onClearData(setOf(ClearableData.DuckChats.Selected(emptySet())))

        verify(mockTabRepository, never()).deleteTabs(any())
    }

    @Test
    fun whenUnrelatedClearableData_thenNoOp() = runTest {
        testee.onClearData(setOf(ClearableData.BrowserData.All, ClearableData.Tabs.All))

        verify(mockTabRepository, never()).deleteTabs(any())
    }
}
