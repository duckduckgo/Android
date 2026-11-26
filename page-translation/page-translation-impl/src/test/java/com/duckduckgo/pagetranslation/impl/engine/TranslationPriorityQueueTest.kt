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

package com.duckduckgo.pagetranslation.impl.engine

import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TranslationPriorityQueueTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule(StandardTestDispatcher())

    private val queue = TranslationPriorityQueue(coroutineRule.testScope, coroutineRule.testDispatcher)
    private val ranCalls = mutableListOf<String>()

    @Test
    fun runVisibleCallsFirst() = coroutineRule.testScope.runTest {
        val first = async { queue.run({ false }) { ranCalls += "first" } }
        val second = async { queue.run({ true }) { ranCalls += "second" } }
        runCurrent()

        assertEquals(listOf("second", "first"), ranCalls)
        assertTrue(first.isCompleted && second.isCompleted)
    }

    @Test
    fun checkVisibilityWhenTheNextCallIsPicked() = coroutineRule.testScope.runTest {
        var isSecondVisible = false

        val first = async(start = UNDISPATCHED) { queue.run({ false }) { ranCalls += "first" } }
        val second = async(start = UNDISPATCHED) { queue.run({ isSecondVisible }) { ranCalls += "second" } }
        isSecondVisible = true
        runCurrent()

        assertEquals(listOf("second", "first"), ranCalls)
        assertTrue(first.isCompleted && second.isCompleted)
    }

    @Test
    fun runCallsInOrderWhenNoneIsVisible() = coroutineRule.testScope.runTest {
        val first = async { queue.run({ false }) { ranCalls += "first" } }
        val second = async { queue.run({ false }) { ranCalls += "second" } }
        runCurrent()

        assertEquals(listOf("first", "second"), ranCalls)
        assertTrue(first.isCompleted && second.isCompleted)
    }

    @Test
    fun dropACallWhoseCallerIsCanceledWhileItWaits() = coroutineRule.testScope.runTest {
        val canceled = async(start = UNDISPATCHED) { queue.run({ true }) { ranCalls += "canceled" } }
        val kept = async(start = UNDISPATCHED) { queue.run({ true }) { ranCalls += "kept" } }

        canceled.cancel()
        runCurrent()

        assertEquals(listOf("kept"), ranCalls)
        assertTrue(kept.isCompleted)
    }

    @Test
    fun returnTheCallsResult() = coroutineRule.testScope.runTest {
        val result = async { queue.run({ true }) { 42 } }
        runCurrent()

        assertEquals(42, result.await())
    }

    @Test
    fun throwTheCallsExceptionToItsCallerAndKeepRunningCalls() = coroutineRule.testScope.runTest {
        val failing = async { runCatching { queue.run({ true }) { error("failed") } } }
        val next = async { queue.run({ true }) { ranCalls += "next" } }
        runCurrent()

        assertEquals("failed", failing.await().exceptionOrNull()?.message)
        assertEquals(listOf("next"), ranCalls)
        assertTrue(next.isCompleted)
    }
}
