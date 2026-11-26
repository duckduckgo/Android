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

package com.duckduckgo.duckchat.impl.responseready

import com.duckduckgo.duckchat.impl.pendingresponse.PendingResponseStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class PageSignalsTest {

    private val testee = PageSignals()

    @Test
    fun `when ready without restart then READY`() {
        assertEquals(PageSignal.READY, signal("ready"))
    }

    @Test
    fun `when restart then ready without streaming then NONE`() {
        signal("start_stream:restart_stream")

        assertEquals(PageSignal.NONE, signal("ready"))
    }

    @Test
    fun `when restart then streaming then ready then READY`() {
        signal("start_stream:new_prompt")
        signal("streaming")

        assertEquals(PageSignal.READY, signal("ready"))
    }

    @Test
    fun `when error then START_ERROR_SETTLE`() {
        assertEquals(PageSignal.START_ERROR_SETTLE, signal("error"))
    }

    @Test
    fun `when blocked then START_ERROR_SETTLE`() {
        assertEquals(PageSignal.START_ERROR_SETTLE, signal("blocked"))
    }

    @Test
    fun `when start stream then CANCEL_ERROR_SETTLE`() {
        assertEquals(PageSignal.CANCEL_ERROR_SETTLE, signal("start_stream:restart_stream"))
    }

    @Test
    fun `when loading or other then NONE`() {
        assertEquals(PageSignal.NONE, signal("loading"))
        assertEquals(PageSignal.NONE, signal("something_new"))
    }

    private fun signal(raw: String) = testee.onStatus(PendingResponseStatus.from(raw))
}
