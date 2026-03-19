/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.sqlcipher.loader.impl

import android.content.Context
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.utils.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.coroutines.CoroutineContext

@OptIn(ExperimentalCoroutinesApi::class)
class RealSqlCipherLoaderTest {

    private val mockContext: Context = mock()
    private val mockDispatchers: DispatcherProvider = mock()
    private val mockPixel: Pixel = mock()

    // A dispatcher that never runs its tasks, keeping libraryLoaded pending.
    private val neverDispatcher = object : CoroutineDispatcher() {
        override fun dispatch(context: CoroutineContext, block: Runnable) = Unit
    }

    @Test
    fun whenCallerCancelledWhileAwaitingLibraryLoadThenCancellationExceptionPropagates() = runTest {
        whenever(mockDispatchers.io()).thenReturn(neverDispatcher)
        val appScope = CoroutineScope(SupervisorJob() + neverDispatcher)
        val testee = RealSqlCipherLoader(
            context = mockContext,
            dispatchers = mockDispatchers,
            pixel = mockPixel,
            appCoroutineScope = appScope,
        )

        var result: Result<Unit>? = null
        val job = launch {
            // Use Long.MAX_VALUE so the withTimeout inside waitForLibraryLoad does not
            // fire before the cancellation when runCurrent() advances virtual time.
            result = testee.waitForLibraryLoad(timeoutMillis = Long.MAX_VALUE)
        }

        // Run until the coroutine suspends on libraryLoaded.await() without advancing
        // virtual time (which would trigger the Long.MAX_VALUE timeout delay).
        runCurrent()

        // Cancel the caller while it is suspended on await().
        job.cancel()
        advanceUntilIdle()

        // CancellationException must be rethrown (cooperative cancellation), not
        // swallowed and returned as Result.failure — which was the pre-fix bug.
        assertNull(result)
    }
}
