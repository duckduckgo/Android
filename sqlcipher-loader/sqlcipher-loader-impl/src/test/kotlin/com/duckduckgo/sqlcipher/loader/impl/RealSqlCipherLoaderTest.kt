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

import android.annotation.SuppressLint
import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Daily
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.library.loader.LibraryLoader
import com.duckduckgo.sqlcipher.loader.impl.SqlCipherPixelName.LIBRARY_LOAD_FAILURE_SQLCIPHER
import com.duckduckgo.sqlcipher.loader.impl.SqlCipherPixelName.LIBRARY_LOAD_TIMEOUT_SQLCIPHER
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

@SuppressLint("DenyListedApi")
@RunWith(AndroidJUnit4::class)
@Config(shadows = [ShadowLibraryLoader::class])
@OptIn(ExperimentalCoroutinesApi::class)
class RealSqlCipherLoaderTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val mockContext: Context = mock()
    private val mockPixel: Pixel = mock()
    private lateinit var loader: RealSqlCipherLoader

    @Before
    fun setup() {
        ShadowLibraryLoader.reset()
        loader = RealSqlCipherLoader(
            context = mockContext,
            dispatchers = coroutineTestRule.testDispatcherProvider,
            pixel = mockPixel,
            appCoroutineScope = coroutineTestRule.testScope,
        )
    }

    @After
    fun tearDown() {
        ShadowLibraryLoader.reset()
    }

    @Test
    fun whenCallerCancelledWhileAwaitingLibraryLoadThenCancellationExceptionPropagates() = runTest {
        val testee = RealSqlCipherLoader(
            context = mockContext,
            dispatchers = coroutineTestRule.testDispatcherProvider,
            pixel = mockPixel,
            appCoroutineScope = coroutineTestRule.testScope,
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

    @Test
    fun whenLoadSucceedsThenReturnsSuccess() = runTest {
        val resultDeferred = asyncImmediately { loader.waitForLibraryLoad() }

        ShadowLibraryLoader.completeAsyncSuccess()

        assertTrue(resultDeferred.await().isSuccess)
    }

    @Test
    fun whenLoadFailsThenReturnsFailure() = runTest {
        val resultDeferred = asyncImmediately { loader.waitForLibraryLoad() }

        ShadowLibraryLoader.completeAsyncFailure(UnsatisfiedLinkError("Test error"))

        resultDeferred.await().assertIsFailure<UnsatisfiedLinkError>()
    }

    @Test
    fun whenTimeoutOccursThenReturnsFailureWithTimeoutException() = runTest {
        // Don't complete the load — let the timeout fire
        val resultDeferred = asyncImmediately { loader.waitForLibraryLoad(timeoutMillis = 100) }

        resultDeferred.await().assertIsFailure<TimeoutCancellationException>()
    }

    @Test
    fun whenTimeoutOccursThenTimeoutPixelFired() = runTest {
        val resultDeferred = asyncImmediately { loader.waitForLibraryLoad(timeoutMillis = 100) }
        resultDeferred.await()

        verify(mockPixel).fire(LIBRARY_LOAD_TIMEOUT_SQLCIPHER, type = Daily())
        verify(mockPixel, never()).fire(eq(LIBRARY_LOAD_FAILURE_SQLCIPHER), any(), any(), any())
        verifyNoMoreInteractions(mockPixel)
    }

    @Test
    fun whenLoadFailsThenFailurePixelFired() = runTest {
        val resultDeferred = asyncImmediately { loader.waitForLibraryLoad() }
        ShadowLibraryLoader.completeAsyncFailure(UnsatisfiedLinkError("Test error"))
        resultDeferred.await()

        verify(mockPixel).fire(LIBRARY_LOAD_FAILURE_SQLCIPHER, type = Daily())
        verify(mockPixel, never()).fire(eq(LIBRARY_LOAD_TIMEOUT_SQLCIPHER), any(), any(), any())
        verifyNoMoreInteractions(mockPixel)
    }

    @Test
    fun whenLoadSucceedsThenNoPixelFired() = runTest {
        val resultDeferred = asyncImmediately { loader.waitForLibraryLoad() }
        ShadowLibraryLoader.completeAsyncSuccess()
        resultDeferred.await()

        verify(mockPixel, never()).fire(eq(LIBRARY_LOAD_TIMEOUT_SQLCIPHER), any(), any(), any())
        verify(mockPixel, never()).fire(eq(LIBRARY_LOAD_FAILURE_SQLCIPHER), any(), any(), any())
        verifyNoMoreInteractions(mockPixel)
    }

    @Test
    fun whenCalledMultipleTimesThenInitializesOnlyOnce() = runTest {
        // First call
        val result1Deferred = asyncImmediately { loader.waitForLibraryLoad() }
        ShadowLibraryLoader.completeAsyncSuccess()
        val result1 = result1Deferred.await()

        // Reset shadow state to detect new initialization attempts
        ShadowLibraryLoader.asyncCallback = null

        // Second call — should reuse the already-completed initialization
        val result2 = loader.waitForLibraryLoad()

        assertNull("Should not start new initialization", ShadowLibraryLoader.asyncCallback)
        assertTrue(result1.isSuccess)
        assertTrue(result2.isSuccess)
    }

    @Test
    fun whenMultipleCallersWaitConcurrentlyThenAllSucceed() = runTest {
        val result1 = asyncImmediately { loader.waitForLibraryLoad() }
        val result2 = asyncImmediately { loader.waitForLibraryLoad() }
        val result3 = asyncImmediately { loader.waitForLibraryLoad() }

        ShadowLibraryLoader.completeAsyncSuccess()

        assertTrue(result1.await().isSuccess)
        assertTrue(result2.await().isSuccess)
        assertTrue(result3.await().isSuccess)
    }

    @Test
    fun whenMultipleCallersWaitWithTimeoutThenAllTimeout() = runTest {
        val result1 = asyncImmediately { loader.waitForLibraryLoad(timeoutMillis = 100) }
        val result2 = asyncImmediately { loader.waitForLibraryLoad(timeoutMillis = 100) }

        result1.await().assertIsFailure<TimeoutCancellationException>()
        result2.await().assertIsFailure<TimeoutCancellationException>()
    }

    @Test
    fun whenOnCreateFiredBeforeWaitThenLoadTriggeredEarly() = runTest {
        loader.onCreate(mock<LifecycleOwner>())

        assertNotNull("onCreate should trigger library load", ShadowLibraryLoader.asyncCallback)

        ShadowLibraryLoader.completeAsyncSuccess()
        assertTrue(loader.waitForLibraryLoad().isSuccess)
    }

    @Test
    fun whenOnPirProcessCreatedFiredBeforeWaitThenLoadTriggeredEarly() = runTest {
        loader.onPirProcessCreated()

        assertNotNull(
            "onPirProcessCreated should trigger library load",
            ShadowLibraryLoader.asyncCallback,
        )

        ShadowLibraryLoader.completeAsyncSuccess()
        assertTrue(loader.waitForLibraryLoad().isSuccess)
    }

    /**
     * Launch async coroutine with UNDISPATCHED start to force immediate execution.
     * Eliminates need for yield() calls - coroutine executes immediately until first suspension.
     *
     * This lets us start it immediately while not blocking waiting for it so we can test interim state, but still be able to get the result.
     */
    private fun <T> CoroutineScope.asyncImmediately(block: suspend () -> T): Deferred<T> =
        async(start = CoroutineStart.UNDISPATCHED) { block() }

    private inline fun <reified T : Throwable> Result<*>.assertIsFailure() {
        assertTrue("Expected failure but was success", isFailure)
        assertTrue(
            "Expected ${T::class.simpleName} but was ${exceptionOrNull()?.javaClass?.simpleName}",
            exceptionOrNull() is T,
        )
    }
}

/**
 * Shadow for LibraryLoader to avoid actual native library loading in tests.
 * Allows tests to control when async loading completes and whether it succeeds or fails.
 */
@Implements(LibraryLoader::class)
class ShadowLibraryLoader {
    @Suppress("unused")
    companion object {
        var asyncCallback: LibraryLoader.LibraryLoaderListener? = null

        @JvmStatic
        @Implementation
        fun loadLibrary(
            context: Context,
            name: String,
            listener: LibraryLoader.LibraryLoaderListener,
        ) {
            // Store callback — test controls when to complete
            asyncCallback = listener
        }

        @JvmStatic
        @Implementation
        fun loadLibrary(context: Context, name: String) {
            // Sync load succeeds immediately in tests
        }

        fun completeAsyncSuccess() {
            asyncCallback?.success()
        }

        fun completeAsyncFailure(throwable: Throwable) {
            asyncCallback?.failure(throwable)
        }

        fun reset() {
            asyncCallback = null
        }
    }
}
