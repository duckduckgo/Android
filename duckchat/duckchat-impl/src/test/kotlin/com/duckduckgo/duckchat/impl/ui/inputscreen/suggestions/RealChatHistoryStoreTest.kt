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

package com.duckduckgo.duckchat.impl.ui.inputscreen.suggestions

import app.cash.turbine.test
import com.duckduckgo.duckchat.impl.inputscreen.ui.suggestions.RealChatHistoryStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealChatHistoryStoreTest {
    private val testee = RealChatHistoryStore()

    @Test
    fun `hasChatHistory does not emit any initial value`() = runTest {
        testee.hasChatHistory.test {
            expectNoEvents()

            cancel()
        }
    }

    @Test
    fun `hasChatHistory updates value`() = runTest {
        testee.hasChatHistory.test {
            testee.setHasChatHistory(true)
            assertTrue(awaitItem())

            testee.setHasChatHistory(false)
            assertFalse(awaitItem())

            cancel()
        }
    }

    @Test
    fun `hasChatHistory does not emit the same value`() = runTest {
        testee.hasChatHistory.test {
            testee.setHasChatHistory(true)
            assertTrue(awaitItem())

            testee.setHasChatHistory(true)
            expectNoEvents()

            cancel()
        }
    }

    @Test
    fun `hasChatHistory emits last known value`() = runTest {
        testee.setHasChatHistory(false)
        testee.setHasChatHistory(true)

        testee.hasChatHistory.test {
            assertTrue(awaitItem())

            cancel()
        }
    }

    @Test
    fun `clearCachedValue removes last known value`() = runTest {
        testee.setHasChatHistory(true)
        testee.clearCachedValue()

        testee.hasChatHistory.test {
            expectNoEvents()

            cancel()
        }
    }
}
