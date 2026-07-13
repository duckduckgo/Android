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
import com.duckduckgo.duckchat.impl.inputscreen.ui.suggestions.RealChatSuggestionsStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealChatSuggestionsStoreTest {
    private val testee = RealChatSuggestionsStore()

    @Test
    fun `hasChatSuggestions does not emit any initial value`() = runTest {
        testee.hasChatSuggestions.test {
            expectNoEvents()

            cancel()
        }
    }

    @Test
    fun `hasChatSuggestions updates value`() = runTest {
        testee.hasChatSuggestions.test {
            testee.setHasChatSuggestions(true)
            assertTrue(awaitItem())

            testee.setHasChatSuggestions(false)
            assertFalse(awaitItem())

            cancel()
        }
    }

    @Test
    fun `hasChatSuggestions does not emit the same value`() = runTest {
        testee.hasChatSuggestions.test {
            testee.setHasChatSuggestions(true)
            assertTrue(awaitItem())

            testee.setHasChatSuggestions(true)
            expectNoEvents()

            cancel()
        }
    }

    @Test
    fun `hasChatSuggestions emits last known value`() = runTest {
        testee.setHasChatSuggestions(false)
        testee.setHasChatSuggestions(true)

        testee.hasChatSuggestions.test {
            assertTrue(awaitItem())

            cancel()
        }
    }

    @Test
    fun `clearCachedValue removes last known value`() = runTest {
        testee.setHasChatSuggestions(true)
        testee.clearCachedValue()

        testee.hasChatSuggestions.test {
            expectNoEvents()

            cancel()
        }
    }
}
