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

package com.duckduckgo.duckchat.impl.attention

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingResponseStatusTest {

    @Test
    fun `raw statuses parse to their type`() {
        assertEquals(PendingResponseStatus.Loading, PendingResponseStatus.from("loading"))
        assertEquals(PendingResponseStatus.Streaming, PendingResponseStatus.from("streaming"))
        assertEquals(PendingResponseStatus.StartStream(isRestart = false), PendingResponseStatus.from("start_stream:new_prompt"))
        assertEquals(PendingResponseStatus.StartStream(isRestart = false), PendingResponseStatus.from("start_stream:auto_retry_last_prompt"))
        assertEquals(PendingResponseStatus.StartStream(isRestart = true), PendingResponseStatus.from("start_stream:restart_stream"))
        assertEquals(PendingResponseStatus.Ready, PendingResponseStatus.from("ready"))
        assertEquals(PendingResponseStatus.Error, PendingResponseStatus.from("error"))
        assertEquals(PendingResponseStatus.Blocked, PendingResponseStatus.from("blocked"))
        assertEquals(PendingResponseStatus.Other, PendingResponseStatus.from(""))
        assertEquals(PendingResponseStatus.Other, PendingResponseStatus.from("something_new"))
    }

    @Test
    fun `loading streaming and start stream count as progress`() {
        assertTrue(PendingResponseStatus.Loading.isProgress)
        assertTrue(PendingResponseStatus.Streaming.isProgress)
        assertTrue(PendingResponseStatus.StartStream(isRestart = true).isProgress)
        assertFalse(PendingResponseStatus.Ready.isProgress)
        assertFalse(PendingResponseStatus.Error.isProgress)
        assertFalse(PendingResponseStatus.Blocked.isProgress)
        assertFalse(PendingResponseStatus.Other.isProgress)
    }

    @Test
    fun `ready error and blocked are terminal`() {
        assertTrue(PendingResponseStatus.Ready.isTerminal)
        assertTrue(PendingResponseStatus.Error.isTerminal)
        assertTrue(PendingResponseStatus.Blocked.isTerminal)
        assertFalse(PendingResponseStatus.Loading.isTerminal)
        assertFalse(PendingResponseStatus.Streaming.isTerminal)
        assertFalse(PendingResponseStatus.StartStream(isRestart = false).isTerminal)
        assertFalse(PendingResponseStatus.Other.isTerminal)
    }
}
