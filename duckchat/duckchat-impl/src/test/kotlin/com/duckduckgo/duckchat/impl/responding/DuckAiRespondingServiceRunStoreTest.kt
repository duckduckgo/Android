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

package com.duckduckgo.duckchat.impl.responding

import com.duckduckgo.common.test.api.InMemorySharedPreferences
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class DuckAiRespondingServiceRunStoreTest {

    private val fakePrefs = InMemorySharedPreferences()
    private val provider: SharedPreferencesProvider = mock {
        on { getSharedPreferences(any(), any(), any()) } doReturn fakePrefs
    }
    private val testee = DuckAiRespondingServiceRunStore(provider)

    @Test
    fun `when nothing marked then takeInterruptedRun returns null`() {
        assertNull(testee.takeInterruptedRun())
    }

    @Test
    fun `when marked running then takeInterruptedRun returns restricted flag`() {
        testee.markRunning(restricted = true)

        assertEquals(InterruptedRun(restricted = true), testee.takeInterruptedRun())
    }

    @Test
    fun `when taken then next take returns null`() {
        testee.markRunning(restricted = false)
        testee.takeInterruptedRun()

        assertNull(testee.takeInterruptedRun())
    }

    @Test
    fun `when cleared then takeInterruptedRun returns null`() {
        testee.markRunning(restricted = false)
        testee.clear()

        assertNull(testee.takeInterruptedRun())
    }
}
