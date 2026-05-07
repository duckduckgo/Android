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

package com.duckduckgo.firemode.impl

import com.duckduckgo.firemode.api.BrowserMode
import org.junit.Assert.assertEquals
import org.junit.Test

class RealBrowserModeStateHolderTest {

    private val testee = RealBrowserModeStateHolder()

    @Test
    fun `initial mode is REGULAR`() {
        assertEquals(BrowserMode.REGULAR, testee.currentMode.value)
    }

    @Test
    fun `switchTo updates current mode`() {
        testee.switchTo(BrowserMode.FIRE)
        assertEquals(BrowserMode.FIRE, testee.currentMode.value)

        testee.switchTo(BrowserMode.REGULAR)
        assertEquals(BrowserMode.REGULAR, testee.currentMode.value)
    }
}
