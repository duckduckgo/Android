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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NewChatHintCommandsTest {

    private val testee = NewChatHintCommands()

    @Test
    fun whenCommandIsSentWhenAttachedThenItIsTakenOnceForItsTab() {
        testee.sendWhenAttached(NewChatHintCommand.OpenCamera("tab"))

        assertEquals(NewChatHintCommand.OpenCamera("tab"), testee.takePending("tab"))
        assertNull(testee.takePending("tab"))
    }

    @Test
    fun whenCommandIsPendingForAnotherTabThenNothingIsTaken() {
        testee.sendWhenAttached(NewChatHintCommand.OpenCamera("tab"))

        assertNull(testee.takePending("other"))
        assertNull(testee.takePending(null))
    }

    @Test
    fun whenCommandIsSentWhenAttachedTwiceForATabThenTheLatestWins() {
        testee.sendWhenAttached(NewChatHintCommand.OpenPhoto("tab"))
        testee.sendWhenAttached(NewChatHintCommand.OpenCamera("tab"))

        assertEquals(NewChatHintCommand.OpenCamera("tab"), testee.takePending("tab"))
    }
}
