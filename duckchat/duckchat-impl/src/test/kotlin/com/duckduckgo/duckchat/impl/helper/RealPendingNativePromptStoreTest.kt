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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RealPendingNativePromptStoreTest {

    private val testee = RealPendingNativePromptStore()

    @Test
    fun whenNothingStoredThenConsumeReturnsNull() {
        assertNull(testee.consume())
    }

    @Test
    fun whenPromptStoredThenConsumeReturnsPromptAndId() {
        testee.store("hello", "model")

        val result = testee.consume()

        assertEquals("hello", result?.prompt)
        assertEquals("model", result?.modelId)
    }

    @Test
    fun whenPromptStoredWithNullModelIdThenConsumeReturnsNullModelId() {
        testee.store("hello", null)

        val result = testee.consume()

        assertEquals("hello", result?.prompt)
        assertNull(result?.modelId)
    }

    @Test
    fun whenConsumedThenSecondConsumeReturnsNull() {
        testee.store("hello", "model")

        testee.consume()
        val result = testee.consume()

        assertNull(result)
    }
}
