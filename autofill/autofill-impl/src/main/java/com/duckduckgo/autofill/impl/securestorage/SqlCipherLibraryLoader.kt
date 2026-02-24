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

import android.content.Context
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Daily
import com.duckduckgo.autofill.api.AutofillFeature
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.LIBRARY_LOAD_FAILURE_SQLCIPHER
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.LIBRARY_LOAD_TIMEOUT_SQLCIPHER
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.library.loader.LibraryLoader
import dagger.SingleInstanceIn
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import logcat.LogPriority.ERROR
import logcat.logcat
import javax.inject.Inject

/**
 * Singleton that manages asynchronous loading of the sqlcipher native library.
 *
 * This loader uses lazy initialization to avoid blocking the main thread during app startup.
 *
 * The loader ensures that sqlcipher is loaded and provides a suspend function to wait for the library to be ready.
 */
@SingleInstanceIn(AppScope::class)
class SqlCipherLibraryLoader @Inject constructor(
    private val context: Context,
    private val dispatchers: DispatcherProvider,
    private val autofillFeature: AutofillFeature,
    private val currentTimeProvider: CurrentTimeProvider,
    private val pixel: Pixel,
) {
    private val initializationMutex = Mutex()
    private var libraryLoaded: CompletableDeferred<Unit>? = null

    /**
     * Suspends until the sqlcipher library is loaded or a timeout occurs.
     *
     * Safe to call from any thread. On first call, initiates library loading and feature flag
     * checks on IO thread. Subsequent calls wait for the existing load operation to complete.
     *
     * @param timeoutMillis Maximum time to wait for library loading in milliseconds (default: 10 seconds)
     * @return Result.success if library loaded, Result.failure if there was an exception or it timed out
     */
    suspend fun waitForLibraryLoad(
        timeoutMillis: Long = 10_000,
    ): Result<Unit> {
        logcat { "SqlCipher-Init: waitForLibraryLoad() called with timeout=${timeoutMillis}ms" }

        val useAsyncLoading = withContext(dispatchers.io()) {
            autofillFeature.sqlCipherAsyncLoading().isEnabled()
        }

        initialize(useAsyncLoading)

        val waitStartTimeMillis = currentTimeProvider.currentTimeMillis()
        logcat { "SqlCipher-Init: Waiting for library load to complete (timeout=${timeoutMillis}ms)" }

        return try {
            withTimeout(timeoutMillis) {
                libraryLoaded!!.await()
            }
            val waitDurationMillis = currentTimeProvider.currentTimeMillis() - waitStartTimeMillis
            logcat { "SqlCipher-Init: Library load wait completed successfully (${waitDurationMillis}ms)" }
            Result.success(Unit)
        } catch (e: TimeoutCancellationException) {
            logcat(ERROR) { "SqlCipher-Init: Timeout after waiting ${timeoutMillis}ms for library load" }
            if (useAsyncLoading) {
                pixel.fire(LIBRARY_LOAD_TIMEOUT_SQLCIPHER, type = Daily())
            }
            Result.failure(e)
        } catch (e: Throwable) {
            logcat(ERROR) { "SqlCipher-Init: Failed while waiting for library load: ${e.javaClass.simpleName} - ${e.message}" }
            currentCoroutineContext().ensureActive()
            if (useAsyncLoading) {
                pixel.fire(LIBRARY_LOAD_FAILURE_SQLCIPHER, type = Daily())
            }
            Result.failure(e)
        }
    }

    /**
     * Ensures the library loading has been initiated.
     * Called lazily on first use.
     */
    private suspend fun initialize(useAsyncLoading: Boolean) {
        // Fast path: if already initialized, return immediately
        if (libraryLoaded != null) {
            logcat { "SqlCipher-Init: Already initialized, skipping" }
            return
        }

        initializationMutex.withLock {
            // Double-check after acquiring lock
            if (libraryLoaded != null) {
                logcat { "SqlCipher-Init: Another thread completed initialization while waiting for lock" }
                return
            }

            if (useAsyncLoading) {
                loadAsync()
            } else {
                loadSync()
            }
        }
    }

    /**
     * Initiates asynchronous loading of the sqlcipher library.
     * This method runs on the IO dispatcher to avoid blocking the main thread.
     */
    private suspend fun loadAsync() {
        withContext(dispatchers.io()) {
            val deferred = CompletableDeferred<Unit>()
            libraryLoaded = deferred

            val startTimeMillis = currentTimeProvider.currentTimeMillis()
            logcat { "SqlCipher-Init: Starting async library load on IO thread" }
            try {
                LibraryLoader.loadLibrary(
                    context,
                    SQLCIPHER_LIB_NAME,
                    object : LibraryLoader.LibraryLoaderListener {
                        override fun success() {
                            val durationMillis = currentTimeProvider.currentTimeMillis() - startTimeMillis
                            logcat { "SqlCipher-Init: Asynchronous library load completed successfully (${durationMillis}ms)" }
                            deferred.complete(Unit)
                        }

                        override fun failure(throwable: Throwable) {
                            val durationMillis = currentTimeProvider.currentTimeMillis() - startTimeMillis
                            logcat(ERROR) {
                                "SqlCipher-Init: Asynchronous library load failed after ${durationMillis}ms: " +
                                    "${throwable.javaClass.simpleName} - ${throwable.message}"
                            }
                            deferred.completeExceptionally(throwable)
                        }
                    },
                )
            } catch (t: Throwable) {
                logcat(ERROR) { "SqlCipher-Init: Exception during synchronous library load setup: ${t.javaClass.simpleName} - ${t.message}" }
                deferred.completeExceptionally(t)
            }
        }
    }

    /**
     * Performs synchronous loading of the sqlcipher library (fallback mode).
     * Matches production behavior - blocking call on main thread.
     */
    private suspend fun loadSync() {
        withContext(dispatchers.main()) {
            val deferred = CompletableDeferred<Unit>()
            libraryLoaded = deferred

            val startTimeMillis = currentTimeProvider.currentTimeMillis()
            logcat { "SqlCipher-Init: Starting synchronous library load on thread ${Thread.currentThread().name}" }
            try {
                LibraryLoader.loadLibrary(context, SQLCIPHER_LIB_NAME)
                val durationMillis = currentTimeProvider.currentTimeMillis() - startTimeMillis
                logcat { "SqlCipher-Init: Sync library load completed successfully (${durationMillis}ms)" }
                deferred.complete(Unit)
            } catch (t: Throwable) {
                val durationMillis = currentTimeProvider.currentTimeMillis() - startTimeMillis
                logcat(ERROR) { "SqlCipher-Init: Sync library load failed after ${durationMillis}ms: ${t.javaClass.simpleName} - ${t.message}" }
                deferred.completeExceptionally(t)
            }
        }
    }

    private companion object {
        private const val SQLCIPHER_LIB_NAME = "sqlcipher"
    }
}
