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

package com.duckduckgo.autofill.impl.securestorage

import android.annotation.SuppressLint
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Daily
import com.duckduckgo.autofill.api.AutofillFeature
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.LIBRARY_LOAD_FAILURE_SQLCIPHER
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.LIBRARY_LOAD_TIMEOUT_SQLCIPHER
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.library.loader.LibraryLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
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
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import java.time.LocalDateTime

@SuppressLint("DenyListedApi")
@RunWith(AndroidJUnit4::class)
@Config(shadows = [ShadowLibraryLoader::class])
class SqlCipherLibraryLoaderTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val mockFeature = FakeFeatureToggleFactory.create(AutofillFeature::class.java)
    private val fakeTimeProvider = FakeCurrentTimeProvider()
    private val pixel: Pixel = mock()
    private lateinit var context: Context
    private lateinit var loader: SqlCipherLibraryLoader

    @Before
    fun setup() {
        ShadowLibraryLoader.reset()
        context = ApplicationProvider.getApplicationContext()
        loader = SqlCipherLibraryLoader(context, coroutineTestRule.testDispatcherProvider, mockFeature, fakeTimeProvider, pixel)
    }

    @After
    fun tearDown() {
        ShadowLibraryLoader.reset()
    }

    @Test
    fun whenFeatureFlagEnabledThenUsesAsyncLoading() = runTest {
        configureAsyncLoadingEnabled()

        val resultDeferred = asyncImmediately { loader.waitForLibraryLoad() }

        // Verify async loading started (callback stored)
        assertNotNull("Async callback should be set", ShadowLibraryLoader.asyncCallback)

        // Complete the async load
        ShadowLibraryLoader.completeAsyncSuccess()

        assertTrue(resultDeferred.await().isSuccess)
    }

    @Test
    fun whenFeatureFlagDisabledThenUsesSyncLoading() = runTest {
        configureAsyncLoadingDisabled()

        val result = loader.waitForLibraryLoad()

        // Verify sync loading was used (no callback)
        assertNull("Sync loading should not set callback", ShadowLibraryLoader.asyncCallback)
        assertTrue(result.isSuccess)
    }

    @Test
    fun whenAsyncLoadSucceedsThenReturnsSuccess() = runTest {
        configureAsyncLoadingEnabled()

        val resultDeferred = asyncImmediately { loader.waitForLibraryLoad() }

        ShadowLibraryLoader.completeAsyncSuccess()

        assertTrue(resultDeferred.await().isSuccess)
    }

    @Test
    fun whenAsyncLoadFailsThenReturnsFailure() = runTest {
        configureAsyncLoadingEnabled()

        val resultDeferred = asyncImmediately { loader.waitForLibraryLoad() }
        ShadowLibraryLoader.completeAsyncFailure(UnsatisfiedLinkError("Test error"))

        resultDeferred.await().assertIsFailure<UnsatisfiedLinkError>()
    }

    @Test
    fun whenSyncLoadFailsThenReturnsFailure() = runTest {
        configureAsyncLoadingDisabled()

        ShadowLibraryLoader.syncShouldFail = true

        loader.waitForLibraryLoad().assertIsFailure<UnsatisfiedLinkError>()
    }

    @Test
    fun whenTimeoutOccursThenReturnsFailureWithTimeoutException() = runTest {
        configureAsyncLoadingEnabled()

        // Start waiting but don't complete the load
        val resultDeferred = asyncImmediately { loader.waitForLibraryLoad(timeoutMillis = 100) }

        // Don't call completeAsyncSuccess() - let it timeout
        resultDeferred.await().assertIsFailure<TimeoutCancellationException>()
    }

    @Test
    fun whenAsyncLoadingEnabledAndTimeoutOccursThenTimeoutPixelFired() = runTest {
        configureAsyncLoadingEnabled()

        val resultDeferred = asyncImmediately { loader.waitForLibraryLoad(timeoutMillis = 100) }
        resultDeferred.await()

        verify(pixel).fire(LIBRARY_LOAD_TIMEOUT_SQLCIPHER, type = Daily())
        verify(pixel, never()).fire(eq(LIBRARY_LOAD_FAILURE_SQLCIPHER), any(), any(), any())
    }

    @Test
    fun whenAsyncLoadingEnabledAndLoadFailsThenFailurePixelFired() = runTest {
        configureAsyncLoadingEnabled()

        val resultDeferred = asyncImmediately { loader.waitForLibraryLoad() }
        ShadowLibraryLoader.completeAsyncFailure(UnsatisfiedLinkError("Test error"))
        resultDeferred.await()

        verify(pixel).fire(LIBRARY_LOAD_FAILURE_SQLCIPHER, type = Daily())
        verify(pixel, never()).fire(eq(LIBRARY_LOAD_TIMEOUT_SQLCIPHER), any(), any(), any())
    }

    @Test
    fun whenAsyncLoadingEnabledAndLoadSucceedsThenNoPixelFired() = runTest {
        configureAsyncLoadingEnabled()

        val resultDeferred = asyncImmediately { loader.waitForLibraryLoad() }
        ShadowLibraryLoader.completeAsyncSuccess()
        resultDeferred.await()

        verify(pixel, never()).fire(eq(LIBRARY_LOAD_TIMEOUT_SQLCIPHER), any(), any(), any())
        verify(pixel, never()).fire(eq(LIBRARY_LOAD_FAILURE_SQLCIPHER), any(), any(), any())
    }

    @Test
    fun whenAsyncLoadingDisabledAndSyncLoadFailsThenNoPixelFired() = runTest {
        configureAsyncLoadingDisabled()
        ShadowLibraryLoader.syncShouldFail = true

        loader.waitForLibraryLoad()

        verify(pixel, never()).fire(eq(LIBRARY_LOAD_TIMEOUT_SQLCIPHER), any(), any(), any())
        verify(pixel, never()).fire(eq(LIBRARY_LOAD_FAILURE_SQLCIPHER), any(), any(), any())
    }

    @Test
    fun whenCalledMultipleTimesThenInitializesOnlyOnce() = runTest {
        configureAsyncLoadingEnabled()

        // First call
        val result1Deferred = asyncImmediately { loader.waitForLibraryLoad() }
        ShadowLibraryLoader.completeAsyncSuccess()
        val result1 = result1Deferred.await()

        // Reset shadow state to detect new initialization attempts
        ShadowLibraryLoader.asyncCallback = null

        // Second call - should reuse existing initialization
        val result2 = loader.waitForLibraryLoad()

        // Verify no new initialization happened
        assertNull("Should not start new initialization", ShadowLibraryLoader.asyncCallback)
        assertTrue(result1.isSuccess)
        assertTrue(result2.isSuccess)
    }

    @Test
    fun whenMultipleCallersWaitConcurrentlyThenAllSucceed() = runTest {
        configureAsyncLoadingEnabled()

        // Launch multiple concurrent waiters
        val result1 = asyncImmediately { loader.waitForLibraryLoad() }
        val result2 = asyncImmediately { loader.waitForLibraryLoad() }
        val result3 = asyncImmediately { loader.waitForLibraryLoad() }

        // Complete once
        ShadowLibraryLoader.completeAsyncSuccess()

        // All should succeed
        assertTrue(result1.await().isSuccess)
        assertTrue(result2.await().isSuccess)
        assertTrue(result3.await().isSuccess)
    }

    @Test
    fun whenMultipleCallersWaitWithTimeoutThenAllTimeout() = runTest {
        configureAsyncLoadingEnabled()

        // Launch multiple concurrent waiters with timeout
        val result1 = asyncImmediately { loader.waitForLibraryLoad(timeoutMillis = 100) }
        val result2 = asyncImmediately { loader.waitForLibraryLoad(timeoutMillis = 100) }

        // Don't complete - let them timeout
        result1.await().assertIsFailure<TimeoutCancellationException>()
        result2.await().assertIsFailure<TimeoutCancellationException>()
    }

    private fun configureAsyncLoadingEnabled() {
        mockFeature.sqlCipherAsyncLoading().setRawStoredState(Toggle.State(enable = true))
    }

    private fun configureAsyncLoadingDisabled() {
        mockFeature.sqlCipherAsyncLoading().setRawStoredState(Toggle.State(enable = false))
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
        assertTrue("Expected ${T::class.simpleName} but was ${exceptionOrNull()?.javaClass?.simpleName}", exceptionOrNull() is T)
    }
}

/**
 * Shadow for LibraryLoader to avoid actual native library loading in tests.
 * Allows tests to control when async/sync loading completes and whether it succeeds or fails.
 */
@Implements(LibraryLoader::class)
class ShadowLibraryLoader {
    @Suppress("unused")
    companion object {
        var asyncCallback: LibraryLoader.LibraryLoaderListener? = null
        var syncShouldFail = false

        @JvmStatic
        @Implementation
        fun loadLibrary(context: Context, name: String, listener: LibraryLoader.LibraryLoaderListener) {
            // Store callback - test controls when to complete
            asyncCallback = listener
        }

        @JvmStatic
        @Implementation
        fun loadLibrary(context: Context, name: String) {
            if (syncShouldFail) {
                throw UnsatisfiedLinkError("Test failure")
            }
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
            syncShouldFail = false
        }
    }
}

class FakeCurrentTimeProvider : CurrentTimeProvider {
    override fun elapsedRealtime(): Long = System.currentTimeMillis()
    override fun currentTimeMillis(): Long = System.currentTimeMillis()
    override fun localDateTimeNow(): LocalDateTime = LocalDateTime.now()
}
