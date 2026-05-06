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

package com.duckduckgo.duckchat.impl.helper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealPendingDuckChatOpenActionStoreTest {

    private val testee = RealPendingDuckChatOpenActionStore()

    @Test
    fun whenInitialThenConsumeReturnsFalse() {
        assertFalse(testee.consumeOpenSidebar())
    }

    @Test
    fun whenMarkedThenConsumeReturnsTrueOnce() {
        testee.markOpenSidebar()

        assertTrue(testee.consumeOpenSidebar())
        assertFalse(testee.consumeOpenSidebar())
    }

    @Test
    fun whenClearedThenConsumeReturnsFalse() {
        testee.markOpenSidebar()
        testee.clear()

        assertFalse(testee.consumeOpenSidebar())
    }
}
