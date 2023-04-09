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
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.store.OfflinePixelCountDataStore
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.isInternalBuild
import java.io.InterruptedIOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.*

@ExperimentalCoroutinesApi
class GlobalUncaughtExceptionHandlerTest {

    private lateinit var testee: GlobalUncaughtExceptionHandler
    private val mockDefaultExceptionHandler: Thread.UncaughtExceptionHandler = mock()
    private val crashLogger: CrashLogger = mock()
    private val mockAppBuildConfig: AppBuildConfig = mock()

    private lateinit var fakeDataStore: OfflinePixelCountDataStore

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    @Before
    fun setup() {
        whenever(mockAppBuildConfig.isInternalBuild()).thenReturn(true)
        fakeDataStore = FakeDataStore()

        testee = GlobalUncaughtExceptionHandler(
            mockDefaultExceptionHandler,
            fakeDataStore,
            crashLogger,
            coroutineTestRule.testDispatcherProvider,
            TestScope(),
            mockAppBuildConfig,
        )
    }

    @Test
    fun whenExceptionIsNotInIgnoreListThenCrashRecordedInDatabase() = runTest {
        testee.uncaughtException(Thread.currentThread(), NullPointerException("Deliberate"))
        advanceUntilIdle()

        verify(crashLogger).logCrash(
            CrashLogger.Crash(
                pixelName = Pixel.StatisticsPixelName.APPLICATION_CRASH_GLOBAL.pixelName,
                t = NullPointerException("Deliberate"),
            ),
        )
        Assert.assertEquals(1, fakeDataStore.applicationCrashCount)
    }

    @Test
    fun whenExceptionIsNotInIgnoreListThenDefaultExceptionHandlerCalled() = runTest {
        val exception = NullPointerException("Deliberate")
        testee.uncaughtException(Thread.currentThread(), exception)
        advanceUntilIdle()

        verify(mockDefaultExceptionHandler).uncaughtException(any(), eq(exception))
        Assert.assertEquals(1, fakeDataStore.applicationCrashCount)
    }

    @Test
    fun whenExceptionIsInterruptedIoExceptionThenCrashNotRecorded() = runTest {
        testee.uncaughtException(Thread.currentThread(), InterruptedIOException("Deliberate"))
        advanceUntilIdle()

        verify(crashLogger, never()).logCrash(any())
        Assert.assertEquals(0, fakeDataStore.applicationCrashCount)
    }

    @Test
    fun whenExceptionIsInterruptedExceptionThenCrashNotRecorded() = runTest {
        testee.uncaughtException(Thread.currentThread(), InterruptedException("Deliberate"))
        advanceUntilIdle()

        verify(crashLogger, never()).logCrash(any())
        Assert.assertEquals(0, fakeDataStore.applicationCrashCount)
    }

    @Test
    fun whenExceptionIsNotRecordedButInInternalBuildThenDefaultExceptionHandlerCalled() = runTest {
        val exception = InterruptedIOException("Deliberate")
        testee.uncaughtException(Thread.currentThread(), exception)
        advanceUntilIdle()

        verify(mockDefaultExceptionHandler).uncaughtException(any(), eq(exception))
        Assert.assertEquals(1, fakeDataStore.applicationCrashCount)
    }
}

private class FakeDataStore : OfflinePixelCountDataStore {
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
