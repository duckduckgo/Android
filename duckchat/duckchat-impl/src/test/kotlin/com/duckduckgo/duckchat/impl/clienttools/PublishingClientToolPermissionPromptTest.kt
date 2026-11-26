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

import com.duckduckgo.browsermode.api.BrowserMode
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PublishingClientToolPermissionPromptTest {

    private val testee = PublishingClientToolPermissionPrompt()

    @Test
    fun whenRequestedThenPromptIsPendingWithTheRequest() = runTest {
        val answer = async { testee.request(REQUEST) }
        runCurrent()

        assertEquals(REQUEST, pending()?.request)
        assertTrue(pending()!!.isOpen)
        answer.cancel()
    }

    @Test
    fun whenAnsweredThenRequestReturnsAnswerAndPendingClears() = runTest {
        val answer = async { testee.request(REQUEST) }
        runCurrent()

        pending()!!.answer(ClientToolPermissionAnswer.AllowForChat())

        assertEquals(ClientToolPermissionAnswer.AllowForChat(), answer.await())
        assertNull(pending())
    }

    @Test
    fun whenAnsweredTwiceThenFirstAnswerWins() = runTest {
        val answer = async { testee.request(REQUEST) }
        runCurrent()
        val pending = pending()!!

        pending.answer(ClientToolPermissionAnswer.DontAllow)
        pending.answer(ClientToolPermissionAnswer.AllowForChat())

        assertEquals(ClientToolPermissionAnswer.DontAllow, answer.await())
    }

    @Test
    fun whenCallerCancelledThenPendingClearsAndLateAnswerIsIgnored() = runTest {
        val answer = async { testee.request(REQUEST) }
        runCurrent()
        val pending = pending()!!

        answer.cancel()
        runCurrent()

        assertNull(pending())
        assertFalse(pending.isOpen)
        pending.answer(ClientToolPermissionAnswer.AllowForChat())
        assertTrue(answer.isCancelled)
    }

    @Test
    fun whenNextRequestArrivesThenItReplacesTheSettledOne() = runTest {
        val first = async { testee.request(REQUEST) }
        runCurrent()
        pending()!!.answer(ClientToolPermissionAnswer.AllowForChat())
        first.await()

        val second = async { testee.request(REQUEST.copy(chatTabId = "other")) }
        runCurrent()

        assertEquals("other", pending("other")?.request?.chatTabId)
        second.cancel()
    }

    @Test
    fun whenTwoChatsPromptAtOnceThenEachKeepsItsOwnPrompt() = runTest {
        val first = async { testee.request(REQUEST) }
        val second = async { testee.request(REQUEST.copy(chatTabId = "other")) }
        val sheet = async { testee.request(REQUEST.copy(contextual = true)) }
        runCurrent()

        assertEquals(REQUEST, pending()?.request)
        assertEquals(REQUEST.copy(chatTabId = "other"), pending("other")?.request)
        assertEquals(REQUEST.copy(contextual = true), pending(contextual = true)?.request)

        pending("other")!!.answer(ClientToolPermissionAnswer.DontAllow)
        assertEquals(ClientToolPermissionAnswer.DontAllow, second.await())
        assertNull(pending("other"))
        assertTrue(pending()!!.isOpen)
        first.cancel()
        sheet.cancel()
    }

    private suspend fun pending(
        chatTabId: String = "chat",
        contextual: Boolean = false,
    ): PendingClientToolPermission? = testee.pendingFor(chatTabId, contextual).first()

    private companion object {
        val REQUEST = ClientToolPermissionRequest.RunTool(
            chatTabId = "chat",
            browserMode = BrowserMode.REGULAR,
            tool = AskingTool("listOpenTabs", ClientToolPermissionMode.Ask(promptTitle = 1, promptMessage = 2, name = 4)),
        )
    }
}
