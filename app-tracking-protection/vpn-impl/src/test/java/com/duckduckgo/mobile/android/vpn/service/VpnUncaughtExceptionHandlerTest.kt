/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.service

import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.global.exception.UncaughtExceptionRepository
import com.duckduckgo.app.global.exception.UncaughtExceptionSource
import com.duckduckgo.app.statistics.store.OfflinePixelCountDataStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

@ExperimentalCoroutinesApi
class VpnUncaughtExceptionHandlerTest {
    private val mockDefaultExceptionHandler: Thread.UncaughtExceptionHandler = mock()
    private val mockUncaughtExceptionRepository: UncaughtExceptionRepository = mock()

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    lateinit var fakeDataStore: OfflinePixelCountDataStore
    private lateinit var exceptionHandler: VpnUncaughtExceptionHandler

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)

        fakeDataStore = FakeDataStore()
        exceptionHandler = VpnUncaughtExceptionHandler(
            mockDefaultExceptionHandler,
            TestScope(),
            coroutineTestRule.testDispatcherProvider,
            fakeDataStore,
            mockUncaughtExceptionRepository,
        )
    }

    @Test
    fun whenUncaughtExceptionThenCrashRecordedInDatabaseAndCallDefaultHandler() = runTest {
        val e = NullPointerException("test")
        exceptionHandler.uncaughtException(Thread.currentThread(), e)
        advanceUntilIdle()

        verify(mockUncaughtExceptionRepository).recordUncaughtException(any(), eq(UncaughtExceptionSource.GLOBAL))
        assertEquals(1, fakeDataStore.applicationCrashCount)
        verify(mockDefaultExceptionHandler).uncaughtException(any(), eq(e))
    }

    inner class FakeDataStore : OfflinePixelCountDataStore {
        override var applicationCrashCount: Int = 0
        override var webRendererGoneCrashCount: Int
            get() = TODO("Not yet implemented")
            set(value) {}
        override var webRendererGoneKilledCount: Int
            get() = TODO("Not yet implemented")
            set(value) {}
        override var cookieDatabaseNotFoundCount: Int
            get() = TODO("Not yet implemented")
            set(value) {}
        override var cookieDatabaseOpenErrorCount: Int
            get() = TODO("Not yet implemented")
            set(value) {}
        override var cookieDatabaseCorruptedCount: Int
            get() = TODO("Not yet implemented")
            set(value) {}
        override var cookieDatabaseDeleteErrorCount: Int
            get() = TODO("Not yet implemented")
            set(value) {}

    }
}
