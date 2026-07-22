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

package com.duckduckgo.app.onboarding.orchestrator

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class SuspendMemoTest {

    @Test
    fun `when invoked multiple times then computes once`() = runTest {
        val computeCount = AtomicInteger(0)
        val memo = SuspendMemo {
            computeCount.incrementAndGet()
            "value"
        }

        val first = memo()
        val second = memo()

        assertEquals("value", first)
        assertEquals("value", second)
        assertEquals(1, computeCount.get())
    }

    @Test
    fun `when compute returns null then caches null and computes once`() = runTest {
        val computeCount = AtomicInteger(0)
        val memo = SuspendMemo<String?> {
            computeCount.incrementAndGet()
            null
        }

        assertEquals(null, memo())
        assertEquals(null, memo())
        assertEquals(1, computeCount.get())
    }
}
