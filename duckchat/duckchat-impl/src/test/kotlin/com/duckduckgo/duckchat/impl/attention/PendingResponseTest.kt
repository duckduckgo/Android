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

import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.duckchat.impl.attention.PendingResponse.ScreenChange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingResponseTest {

    @Test
    fun `first update with its tab takes the chat ID and puts it on screen`() {
        val response = PendingResponse(tabId = "t1", browserMode = BrowserMode.REGULAR)

        response.updateOnScreen(OnScreen(tabId = "t1", chatId = "A"))

        assertEquals("A", response.chatId)
        assertTrue(response.isOnScreenIn("t1"))
    }

    @Test
    fun `first update with another tab keeps it off screen without a chat ID`() {
        val response = PendingResponse(tabId = "t1", browserMode = BrowserMode.REGULAR)

        assertNull(response.updateOnScreen(OnScreen(tabId = "t2", chatId = "B")))
        assertNull(response.chatId)
        assertFalse(response.onScreen)
        assertFalse(response.chatSwitchedAway)
    }

    @Test
    fun `chat coming on screen enters once and moves to that tab`() {
        val response = PendingResponse(tabId = "t1", browserMode = BrowserMode.REGULAR, chatId = "A")

        assertEquals(ScreenChange.ENTERED, response.updateOnScreen(OnScreen(tabId = "t2", chatId = "A")))
        assertNull(response.updateOnScreen(OnScreen(tabId = "t2", chatId = "A")))
        assertTrue(response.isOnScreenIn("t2"))
    }

    @Test
    fun `chat leaving the screen exits`() {
        val response = PendingResponse(tabId = "t1", browserMode = BrowserMode.REGULAR, chatId = "A")
        response.updateOnScreen(OnScreen(tabId = "t1", chatId = "A"))

        assertEquals(ScreenChange.EXITED, response.updateOnScreen(null))
        assertFalse(response.onScreen)
        assertFalse(response.chatSwitchedAway)
    }

    @Test
    fun `its tab showing another chat sets chatSwitchedAway`() {
        val response = PendingResponse(tabId = "t1", browserMode = BrowserMode.REGULAR, chatId = "A")
        response.updateOnScreen(OnScreen(tabId = "t1", chatId = "A"))

        assertEquals(ScreenChange.EXITED, response.updateOnScreen(OnScreen(tabId = "t1", chatId = "B")))
        assertTrue(response.chatSwitchedAway)
    }

    @Test
    fun `chat without an ID matches by tab until switched away`() {
        assertEquals(
            ScreenChange.ENTERED,
            PendingResponse(tabId = "t1", browserMode = BrowserMode.REGULAR).updateOnScreen(OnScreen(tabId = "t1", chatId = "B")),
        )
        assertNull(
            PendingResponse(
                tabId = "t1",
                browserMode = BrowserMode.REGULAR,
                chatSwitchedAway = true,
            ).updateOnScreen(OnScreen(tabId = "t1", chatId = "B")),
        )
    }

    @Test
    fun `chat back on screen clears chatSwitchedAway`() {
        val response = PendingResponse(tabId = "t1", browserMode = BrowserMode.REGULAR, chatId = "A", chatSwitchedAway = true)

        assertEquals(ScreenChange.ENTERED, response.updateOnScreen(OnScreen(tabId = "t1", chatId = "A")))
        assertFalse(response.chatSwitchedAway)
    }

    @Test
    fun `status from a tab prefers the on-screen response`() {
        val offScreen = PendingResponse(tabId = "t1", browserMode = BrowserMode.REGULAR, chatId = "A")
        val onScreen = PendingResponse(tabId = "t1", browserMode = BrowserMode.REGULAR, chatId = "B").apply {
            updateOnScreen(OnScreen(tabId = "t1", chatId = "B"))
        }

        assertEquals(onScreen, listOf(offScreen, onScreen).describedByStatusFrom("t1"))
    }

    @Test
    fun `status from a tab falls back to an off-screen response that has not switched away`() {
        val offScreen = PendingResponse(tabId = "t1", browserMode = BrowserMode.REGULAR, chatId = "A")

        assertEquals(offScreen, listOf(offScreen).describedByStatusFrom("t1"))
    }

    @Test
    fun `status from a tab describes no response once it switched away`() {
        val switched = PendingResponse(tabId = "t1", browserMode = BrowserMode.REGULAR, chatId = "A", chatSwitchedAway = true)

        assertNull(listOf(switched).describedByStatusFrom("t1"))
    }
}
