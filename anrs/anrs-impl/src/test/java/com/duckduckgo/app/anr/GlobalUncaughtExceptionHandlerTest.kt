/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.app.anr

import com.duckduckgo.anrs.api.CrashLogger
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.*
import java.io.InterruptedIOException

@ExperimentalCoroutinesApi
class GlobalUncaughtExceptionHandlerTest {

    private lateinit var testee: GlobalUncaughtExceptionHandler
    private val mockDefaultExceptionHandler: Thread.UncaughtExceptionHandler = mock()
    private val crashLogger: CrashLogger = mock()

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    @Before
    fun setup() {
        testee = GlobalUncaughtExceptionHandler(
            mockDefaultExceptionHandler,
            crashLogger,
            coroutineTestRule.testDispatcherProvider,
            TestScope(),
        )
    }

    @Test
    fun whenExceptionIsNotInIgnoreListThenCrashRecordedInDatabase() = runTest {
        testee.uncaughtException(Thread.currentThread(), NullPointerException("Deliberate"))
        advanceUntilIdle()

        verify(crashLogger).logCrash(any())
        verify(mockDefaultExceptionHandler).uncaughtException(any(), any())
    }

    @Test
    fun whenExceptionIsNotInIgnoreListThenDefaultExceptionHandlerCalled() = runTest {
        val exception = NullPointerException("Deliberate")
        testee.uncaughtException(Thread.currentThread(), exception)
        advanceUntilIdle()

        verify(crashLogger).logCrash(any())
        verify(mockDefaultExceptionHandler).uncaughtException(any(), eq(exception))
    }

    @Test
    fun whenExceptionIsInterruptedIoExceptionThenCrashNotRecorded() = runTest {
        val exception = InterruptedIOException("Deliberate")
        testee.uncaughtException(Thread.currentThread(), exception)
        advanceUntilIdle()

        verify(crashLogger, never()).logCrash(any())
        verify(mockDefaultExceptionHandler, never()).uncaughtException(any(), eq(exception))
    }

    @Test
    fun whenExceptionIsInterruptedExceptionThenCrashNotRecorded() = runTest {
        val exception = InterruptedException("Deliberate")
        testee.uncaughtException(Thread.currentThread(), exception)
        advanceUntilIdle()

        verify(crashLogger, never()).logCrash(any())
        verify(mockDefaultExceptionHandler, never()).uncaughtException(any(), eq(exception))
    }
}
