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
import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.app.lifecycle.PirProcessLifecycleObserver
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Daily
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.library.loader.LibraryLoader
import com.duckduckgo.sqlcipher.loader.api.SqlCipherLoader
import com.duckduckgo.sqlcipher.loader.impl.SqlCipherPixelName.LIBRARY_LOAD_FAILURE_SQLCIPHER
import com.duckduckgo.sqlcipher.loader.impl.SqlCipherPixelName.LIBRARY_LOAD_TIMEOUT_SQLCIPHER
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import logcat.LogPriority.ERROR
import logcat.logcat
import javax.inject.Inject

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class, SqlCipherLoader::class)
@ContributesMultibinding(AppScope::class, MainProcessLifecycleObserver::class)
@ContributesMultibinding(AppScope::class, PirProcessLifecycleObserver::class)
class RealSqlCipherLoader @Inject constructor(
    private val context: Context,
    private val dispatchers: DispatcherProvider,
    private val pixel: Pixel,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : SqlCipherLoader, MainProcessLifecycleObserver, PirProcessLifecycleObserver {

    private val libraryLoaded = CompletableDeferred<Unit>()

    // Eagerly kick off the load at app startup, well before autofill or PIR need it.
    override fun onCreate(owner: LifecycleOwner) {
        appCoroutineScope.launch(dispatchers.io()) {
            logcat { "SqlCipher: Attempting to load native library on the main process" }
            doLoad()
        }
    }

    // Also trigger load when PIR process starts.
    override fun onPirProcessCreated() {
        appCoroutineScope.launch(dispatchers.io()) {
            if (!libraryLoaded.isCompleted) {
                logcat { "SqlCipher: Attempting to load native library on the PIR process" }
                doLoad()
            }
        }
    }

    override suspend fun waitForLibraryLoad(timeoutMillis: Long): Result<Unit> {
        // If onCreate hasn't fired yet (edge case), trigger the load inline.
        if (!libraryLoaded.isCompleted) {
            appCoroutineScope.launch(dispatchers.io()) { doLoad() }
        }

        return try {
            withTimeout(timeoutMillis) {
                libraryLoaded.await()
            }
            logcat { "SqlCipher: native library loaded" }
            Result.success(Unit)
        } catch (e: TimeoutCancellationException) {
            logcat(ERROR) { "SqlCipher: timed out waiting for native library after ${timeoutMillis}ms" }
            pixel.fire(LIBRARY_LOAD_TIMEOUT_SQLCIPHER, type = Daily())
            Result.failure(e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logcat(ERROR) { "SqlCipher: failed waiting for native library: ${e.javaClass.simpleName} - ${e.message}" }
            Result.failure(e)
        }
    }

    private fun doLoad() {
        logcat { "SqlCipher: starting async library load" }
        LibraryLoader.loadLibrary(
            context,
            SQLCIPHER_LIB_NAME,
            object : LibraryLoader.LibraryLoaderListener {
                override fun success() {
                    logcat { "SqlCipher: native library loaded successfully" }
                    libraryLoaded.complete(Unit)
                }

                override fun failure(throwable: Throwable) {
                    logcat(ERROR) { "SqlCipher: native library load failed: ${throwable.javaClass.simpleName} - ${throwable.message}" }
                    // Guard ensures the pixel fires exactly once even if doLoad() races.
                    if (libraryLoaded.completeExceptionally(throwable)) {
                        pixel.fire(LIBRARY_LOAD_FAILURE_SQLCIPHER, type = Daily())
                    }
                }
            },
        )
    }

    private companion object {
        private const val SQLCIPHER_LIB_NAME = "sqlcipher"
    }
}

enum class SqlCipherPixelName(override val pixelName: String) : Pixel.PixelName {
    LIBRARY_LOAD_TIMEOUT_SQLCIPHER("library_load_timeout_sqlcipher"),
    LIBRARY_LOAD_FAILURE_SQLCIPHER("library_load_failure_sqlcipher"),
}
