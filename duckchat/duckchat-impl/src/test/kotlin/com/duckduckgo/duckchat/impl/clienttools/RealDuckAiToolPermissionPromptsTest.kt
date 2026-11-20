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

package com.duckduckgo.duckchat.impl.clienttools

import app.cash.turbine.test
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.browsermode.api.BrowserModeDataProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RealDuckAiToolPermissionPromptsTest {

    private val prompt = PublishingClientToolPermissionPrompt()
    private val selectedTab = MutableStateFlow<TabEntity?>(TabEntity(tabId = "tab-1", position = 0))
    private val tabRepository: TabRepository = mock<TabRepository>().also {
        whenever(it.flowSelectedTab).thenReturn(selectedTab)
    }
    private val testee = RealDuckAiToolPermissionPrompts(
        prompt = prompt,
        tabRepositoryProvider = lazy {
            object : BrowserModeDataProvider<TabRepository> {
                override fun forMode(mode: BrowserMode): TabRepository = tabRepository
            }
        },
    )

    @Test
    fun whenPromptOpensForTheSelectedTabThenItIsShownAndTheAnswerGoesBack() = runTest {
        testee.prompts(flowOf("tab-1"), contextual = false).test {
            assertNull(awaitItem())
            val reply = async { prompt.request(request(chatTabId = "tab-1")) }
            runCurrent()

            val shown = awaitItem()!!
            shown.answer(ClientToolPermissionAnswer.DontAllow)

            assertEquals(ClientToolPermissionAnswer.DontAllow, reply.await())
            assertNull(awaitItem())
        }
    }

    @Test
    fun whenPromptsTabIsNotSelectedThenItWaitsUntilItIs() = runTest {
        selectedTab.value = TabEntity(tabId = "other", position = 0)

        testee.prompts(flowOf("tab-1"), contextual = false).test {
            assertNull(awaitItem())
            val reply = async { prompt.request(request(chatTabId = "tab-1")) }
            runCurrent()
            expectNoEvents()

            selectedTab.value = TabEntity(tabId = "tab-1", position = 0)

            assertSame(prompt.pending.value, awaitItem())
            reply.cancel()
            assertNull(awaitItem())
        }
    }

    @Test
    fun whenAnotherTabIsSelectedThenTheShownPromptIsHidden() = runTest {
        testee.prompts(flowOf("tab-1"), contextual = false).test {
            assertNull(awaitItem())
            val reply = async { prompt.request(request(chatTabId = "tab-1")) }
            runCurrent()
            awaitItem()

            selectedTab.value = TabEntity(tabId = "other", position = 0)

            assertNull(awaitItem())
            reply.cancel()
        }
    }

    @Test
    fun whenPromptBelongsToTheContextualSheetThenTheTabDoesNotShowIt() = runTest {
        testee.prompts(flowOf("tab-1"), contextual = false).test {
            assertNull(awaitItem())
            val reply = async { prompt.request(request(chatTabId = "tab-1", contextual = true)) }
            runCurrent()

            expectNoEvents()
            reply.cancel()
        }
    }

    @Test
    fun whenSheetMovesToThePromptsTabThenItIsShown() = runTest {
        val sheetTabId = MutableStateFlow("")

        testee.prompts(sheetTabId, contextual = true).test {
            assertNull(awaitItem())
            val reply = async { prompt.request(request(chatTabId = "tab-1", contextual = true)) }
            runCurrent()
            expectNoEvents()

            sheetTabId.value = "tab-1"

            assertSame(prompt.pending.value, awaitItem())
            reply.cancel()
            assertNull(awaitItem())
        }
    }

    private fun request(
        chatTabId: String,
        contextual: Boolean = false,
    ) = ClientToolPermissionRequest.GrantAccess(
        chatTabId = chatTabId,
        browserMode = BrowserMode.REGULAR,
        tools = listOf(AskingTool("listOpenTabs", ClientToolPermissionMode.Ask(1, 2, 3, 4))),
        toolsRemovable = true,
        contextual = contextual,
    )
}
