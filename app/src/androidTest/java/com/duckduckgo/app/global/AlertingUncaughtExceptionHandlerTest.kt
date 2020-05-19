/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.app.global

import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.global.exception.UncaughtExceptionRepository
import com.duckduckgo.app.global.exception.UncaughtExceptionSource
import com.duckduckgo.app.statistics.store.OfflinePixelCountDataStore
import com.nhaarman.mockitokotlin2.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.InterruptedIOException

@ExperimentalCoroutinesApi
class AlertingUncaughtExceptionHandlerTest {

    private lateinit var testee: AlertingUncaughtExceptionHandler
    private val mockDefaultExceptionHandler: Thread.UncaughtExceptionHandler = mock()
    private val mockPixelCountDataStore: OfflinePixelCountDataStore = mock()
    private val mockUncaughtExceptionRepository: UncaughtExceptionRepository = mock()

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    @Before
    fun setup() {
        testee = AlertingUncaughtExceptionHandler(
            mockDefaultExceptionHandler,
            mockPixelCountDataStore,
            mockUncaughtExceptionRepository,
            coroutineTestRule.testDispatcherProvider
        )
    }

    @Test
    fun whenExceptionIsNotInIgnoreListThenCrashRecordedInDatabase() = coroutineTestRule.testDispatcher.runBlockingTest {
        testee.uncaughtException(Thread.currentThread(), NullPointerException("Deliberate"))
        advanceUntilIdle()

        verify(mockUncaughtExceptionRepository).recordUncaughtException(any(), eq(UncaughtExceptionSource.GLOBAL))
    }

    @Test
    fun whenExceptionIsNotInIgnoreListThenDefaultExceptionHandlerCalled() = coroutineTestRule.testDispatcher.runBlockingTest {
        val exception = NullPointerException("Deliberate")
        testee.uncaughtException(Thread.currentThread(), exception)
        advanceUntilIdle()

        verify(mockDefaultExceptionHandler).uncaughtException(any(), eq(exception))
    }

    @Test
    fun whenExceptionIsInterruptedIoExceptionThenCrashNotRecorded() = coroutineTestRule.testDispatcher.runBlockingTest {
        testee.uncaughtException(Thread.currentThread(), InterruptedIOException("Deliberate"))
        advanceUntilIdle()

        verify(mockUncaughtExceptionRepository, never()).recordUncaughtException(any(), any())
    }

    @Test
    fun whenExceptionIsInterruptedExceptionThenCrashNotRecorded() = coroutineTestRule.testDispatcher.runBlockingTest {
        testee.uncaughtException(Thread.currentThread(), InterruptedException("Deliberate"))
        advanceUntilIdle()

        verify(mockUncaughtExceptionRepository, never()).recordUncaughtException(any(), any())
    }

    @Test
    fun whenExceptionIsNotRecordedButInDebugModeThenDefaultExceptionHandlerCalled() = coroutineTestRule.testDispatcher.runBlockingTest {
        val exception = InterruptedIOException("Deliberate")
        testee.uncaughtException(Thread.currentThread(), exception)
        advanceUntilIdle()

        verify(mockDefaultExceptionHandler).uncaughtException(any(), eq(exception))
    }
}
