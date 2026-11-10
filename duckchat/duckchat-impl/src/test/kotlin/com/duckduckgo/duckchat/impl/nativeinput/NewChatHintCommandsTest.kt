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

package com.duckduckgo.duckchat.impl.nativeinput

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NewChatHintCommandsTest {

    private val testee = NewChatHintCommands()

    @Test
    fun whenCommandIsSentThenSubscriberForItsTabReceivesIt() = runTest(UnconfinedTestDispatcher()) {
        val received = mutableListOf<NewChatHintCommand>()
        val job = launch { testee.commandsFor { "tab" }.collect { received.add(it) } }

        testee.send(NewChatHintCommand.OpenCamera("tab"))

        assertEquals(listOf<NewChatHintCommand>(NewChatHintCommand.OpenCamera("tab")), received)
        job.cancel()
    }

    @Test
    fun whenCommandIsSentForAnotherTabThenSubscriberDoesNotReceiveIt() = runTest(UnconfinedTestDispatcher()) {
        val received = mutableListOf<NewChatHintCommand>()
        val job = launch { testee.commandsFor { "tab" }.collect { received.add(it) } }

        testee.send(NewChatHintCommand.OpenCamera("other"))

        assertTrue(received.isEmpty())
        job.cancel()
    }

    @Test
    fun whenCommandIsSentBeforeSubscribingThenItIsNotReplayed() = runTest(UnconfinedTestDispatcher()) {
        testee.send(NewChatHintCommand.OpenCamera("tab"))
        val received = mutableListOf<NewChatHintCommand>()

        val job = launch { testee.commandsFor { "tab" }.collect { received.add(it) } }

        assertTrue(received.isEmpty())
        job.cancel()
    }
}
