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

        assertEquals(REQUEST, testee.pending.value?.request)
        assertTrue(testee.pending.value!!.isOpen)
        answer.cancel()
    }

    @Test
    fun whenAnsweredThenRequestReturnsAnswerAndPendingClears() = runTest {
        val answer = async { testee.request(REQUEST) }
        runCurrent()

        testee.pending.value!!.answer(ClientToolPermissionAnswer.AllowForChat())

        assertEquals(ClientToolPermissionAnswer.AllowForChat(), answer.await())
        assertNull(testee.pending.value)
    }

    @Test
    fun whenAnsweredTwiceThenFirstAnswerWins() = runTest {
        val answer = async { testee.request(REQUEST) }
        runCurrent()
        val pending = testee.pending.value!!

        pending.answer(ClientToolPermissionAnswer.DontAllow)
        pending.answer(ClientToolPermissionAnswer.AllowForChat())

        assertEquals(ClientToolPermissionAnswer.DontAllow, answer.await())
    }

    @Test
    fun whenCallerCancelledThenPendingClearsAndLateAnswerIsIgnored() = runTest {
        val answer = async { testee.request(REQUEST) }
        runCurrent()
        val pending = testee.pending.value!!

        answer.cancel()
        runCurrent()

        assertNull(testee.pending.value)
        assertFalse(pending.isOpen)
        pending.answer(ClientToolPermissionAnswer.AllowForChat())
        assertTrue(answer.isCancelled)
    }

    @Test
    fun whenNextRequestArrivesThenItReplacesTheSettledOne() = runTest {
        val first = async { testee.request(REQUEST) }
        runCurrent()
        testee.pending.value!!.answer(ClientToolPermissionAnswer.AllowForChat())
        first.await()

        val second = async { testee.request(REQUEST.copy(chatTabId = "other")) }
        runCurrent()

        assertEquals("other", testee.pending.value?.request?.chatTabId)
        second.cancel()
    }

    private companion object {
        val REQUEST = ClientToolPermissionRequest.RunTool(
            chatTabId = "chat",
            browserMode = BrowserMode.REGULAR,
            tool = AskingTool("listOpenTabs", ClientToolPermissionMode.Ask(promptTitle = 1, promptMessage = 2, name = 3, description = 4)),
        )
    }
}
