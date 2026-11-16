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
import com.duckduckgo.browsermode.api.BrowserMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ClientToolPermissionDialogPresenterTest {

    private val pending = MutableStateFlow<PendingClientToolPermission?>(null)
    private val selectedTabId = MutableStateFlow<String?>(CHAT_TAB)
    private val selectedFireTabId = MutableStateFlow<String?>(null)

    private val visible = ClientToolPermissionDialogPresenter.visiblePrompt(pending) { mode ->
        when (mode) {
            BrowserMode.REGULAR -> selectedTabId
            BrowserMode.FIRE -> selectedFireTabId
        }
    }

    @Test
    fun whenPromptBelongsToSelectedTabThenItIsVisible() = runTest {
        visible.test {
            assertNull(awaitItem())
            val prompt = pendingFor(CHAT_TAB)
            pending.value = prompt
            assertSame(prompt, awaitItem())
        }
    }

    @Test
    fun whenPromptBelongsToAnotherTabThenItWaitsUntilThatTabIsSelected() = runTest {
        selectedTabId.value = OTHER_TAB
        val prompt = pendingFor(CHAT_TAB)
        pending.value = prompt

        visible.test {
            assertNull(awaitItem())
            selectedTabId.value = CHAT_TAB
            assertSame(prompt, awaitItem())
            selectedTabId.value = OTHER_TAB
            assertNull(awaitItem())
        }
    }

    @Test
    fun whenPromptWithdrawnThenNothingIsVisible() = runTest {
        pending.value = pendingFor(CHAT_TAB)

        visible.test {
            awaitItem()
            pending.value = null
            assertNull(awaitItem())
        }
    }

    @Test
    fun whenUnrelatedTabSelectionChangesThenNoNewEmission() = runTest {
        selectedTabId.value = OTHER_TAB
        pending.value = pendingFor(CHAT_TAB)

        visible.test {
            assertNull(awaitItem())
            selectedTabId.value = "third"
            expectNoEvents()
        }
    }

    @Test
    fun whenPromptBelongsToFireChatThenOnlyTheFireSelectionCounts() = runTest {
        val prompt = pendingFor(CHAT_TAB, BrowserMode.FIRE)
        pending.value = prompt

        visible.test {
            assertNull(awaitItem())
            selectedFireTabId.value = CHAT_TAB
            assertSame(prompt, awaitItem())
        }
    }

    private fun pendingFor(
        chatTabId: String,
        browserMode: BrowserMode = BrowserMode.REGULAR,
    ) = PendingClientToolPermission(
        ClientToolPermissionRequest.RunTool(chatTabId, browserMode, AskingTool("listOpenTabs", ClientToolPermissionMode.Ask(1, 2, 3))),
        CompletableDeferred(),
    )

    private companion object {
        const val CHAT_TAB = "chat"
        const val OTHER_TAB = "other"
    }
}
