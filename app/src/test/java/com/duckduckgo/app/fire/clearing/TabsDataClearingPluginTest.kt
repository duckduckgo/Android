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

package com.duckduckgo.app.fire.clearing

import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.browsermode.api.BrowserModeDataProvider
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.dataclearing.api.plugin.ClearableData
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class TabsDataClearingPluginTest {
    @get:Rule val coroutineRule = CoroutineTestRule()
    private val fireTabRepository: TabRepository = mock()
    private val provider: BrowserModeDataProvider<TabRepository> = mock()
    private val testee = TabsDataClearingPlugin(provider)

    @Test fun whenFireTabsThenDeletesAllFireTabs() = runTest {
        whenever(provider.forMode(BrowserMode.FIRE)).thenReturn(fireTabRepository)
        testee.onClearData(setOf(ClearableData.Tabs.AllForMode(BrowserMode.FIRE)))
        verify(fireTabRepository).deleteAll()
    }

    @Test fun whenTabsAllThenDeletesAllFireTabs() = runTest {
        whenever(provider.forMode(BrowserMode.FIRE)).thenReturn(fireTabRepository)
        testee.onClearData(setOf(ClearableData.Tabs.All))
        verify(fireTabRepository).deleteAll()
    }

    @Test fun whenRegularTabsThenDoesNothing() = runTest {
        testee.onClearData(setOf(ClearableData.Tabs.AllForMode(BrowserMode.REGULAR)))
        verify(fireTabRepository, never()).deleteAll()
    }

    @Test fun whenFireSingleForModeThenDeletesThatFireTab() = runTest {
        whenever(provider.forMode(BrowserMode.FIRE)).thenReturn(fireTabRepository)
        testee.onClearData(setOf(ClearableData.Tabs.SingleForMode("tabX", BrowserMode.FIRE)))
        verify(provider).forMode(BrowserMode.FIRE)
        verify(fireTabRepository).deleteTabs(listOf("tabX"))
    }

    @Test fun whenRegularSingleForModeThenDoesNothing() = runTest {
        testee.onClearData(setOf(ClearableData.Tabs.SingleForMode("tabX", BrowserMode.REGULAR)))
        verify(provider, never()).forMode(BrowserMode.REGULAR)
        verify(fireTabRepository, never()).deleteTabs(any())
    }
}
