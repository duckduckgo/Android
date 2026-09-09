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

package com.duckduckgo.app.browser

import com.duckduckgo.duckchat.api.InputMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InputScreenLaunchTargetTest {

    private val testee = InputScreenLaunchTargetImpl()

    @Test
    fun `when not armed then consume returns null`() {
        assertNull(testee.consumeInitialInputMode())
    }

    @Test
    fun `when armed then consume returns the mode`() {
        testee.setInitialInputMode(InputMode.SEARCH)
        assertEquals(InputMode.SEARCH, testee.consumeInitialInputMode())
    }

    @Test
    fun `when armed then peek returns the mode without clearing it`() {
        testee.setInitialInputMode(InputMode.DUCK_AI)

        assertEquals(InputMode.DUCK_AI, testee.peekInitialInputMode())
        assertEquals(InputMode.DUCK_AI, testee.consumeInitialInputMode())
    }

    @Test
    fun `when consumed then the signal is cleared`() {
        testee.setInitialInputMode(InputMode.DUCK_AI)
        testee.consumeInitialInputMode()

        assertNull(testee.consumeInitialInputMode())
    }
}
