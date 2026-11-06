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

package com.duckduckgo.autofill.impl.ui.credential.management.importpassword.google

import com.duckduckgo.autofill.impl.time.TimeProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RealExternalImportAuthSuppressorTest {

    private val timeProvider: TimeProvider = mock {
        on { currentTimeMillis() } doReturn 0
    }
    private val testee = RealExternalImportAuthSuppressor(timeProvider)

    @Test
    fun whenNoImportRunningThenSuppressionNotAllowed() {
        assertFalse(testee.consumeSuppression())
    }

    @Test
    fun whenImportRunningThenSuppressionAllowed() = runTest {
        testee.suppressAuthPromptWhileRunning {
            whenever(timeProvider.currentTimeMillis()).thenReturn(10_000)
            assertTrue(testee.consumeSuppression())
        }
    }

    @Test
    fun whenSuppressionAlreadyConsumedThenSuppressionNotAllowedAgain() = runTest {
        testee.suppressAuthPromptWhileRunning {
            testee.consumeSuppression()
            assertFalse(testee.consumeSuppression())
        }
    }

    @Test
    fun whenImportRunningForTooLongThenSuppressionNotAllowed() = runTest {
        testee.suppressAuthPromptWhileRunning {
            whenever(timeProvider.currentTimeMillis()).thenReturn(180_001)
            assertFalse(testee.consumeSuppression())
        }
    }

    @Test
    fun whenImportFinishedThenSuppressionNotAllowed() = runTest {
        testee.suppressAuthPromptWhileRunning { }
        assertFalse(testee.consumeSuppression())
    }

    @Test
    fun whenImportThrowsThenSuppressionNotAllowed() = runTest {
        runCatching { testee.suppressAuthPromptWhileRunning { throw IllegalStateException() } }
        assertFalse(testee.consumeSuppression())
    }

    @Test
    fun whenNewImportStartedAfterSuppressionConsumedThenSuppressionAllowedAgain() = runTest {
        testee.suppressAuthPromptWhileRunning { testee.consumeSuppression() }
        testee.suppressAuthPromptWhileRunning {
            assertTrue(testee.consumeSuppression())
        }
    }

    @Test
    fun whenImportRunsThenBlockResultReturned() = runTest {
        assertEquals("result", testee.suppressAuthPromptWhileRunning { "result" })
    }
}
