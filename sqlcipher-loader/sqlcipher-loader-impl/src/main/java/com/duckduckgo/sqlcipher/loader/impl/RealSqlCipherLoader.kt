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
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Daily
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.library.loader.LibraryLoader
import com.duckduckgo.sqlcipher.loader.api.SqlCipherLoader
import com.duckduckgo.sqlcipher.loader.impl.SqlCipherPixelName.LIBRARY_LOAD_FAILURE_SQLCIPHER
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.LogPriority.ERROR
import logcat.logcat
import javax.inject.Inject

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class, SqlCipherLoader::class)
@ContributesMultibinding(AppScope::class, MainProcessLifecycleObserver::class)
class RealSqlCipherLoader @Inject constructor(
    private val context: Context,
    private val dispatchers: DispatcherProvider,
    private val pixel: Pixel,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : SqlCipherLoader, MainProcessLifecycleObserver {

    private val libraryLoaded = CompletableDeferred<Unit>()

    // Eagerly kick off the load at app startup on the IO dispatcher,
    // well before autofill or PIR need it.
    override fun onCreate(owner: LifecycleOwner) {
        appCoroutineScope.launch(dispatchers.io()) {
            doLoad()
        }
    }

    override suspend fun waitForLibraryLoad(): Result<Unit> {
        // If onCreate hasn't fired yet (edge case), trigger the load inline.
        if (!libraryLoaded.isCompleted) {
            appCoroutineScope.launch(dispatchers.io()) { doLoad() }
        }
        return runCatching { libraryLoaded.await() }
    }

    private fun doLoad() {
        // CompletableDeferred.complete() is a no-op after first call,
        // so concurrent doLoad() calls are safe.
        try {
            LibraryLoader.loadLibrary(context, SQLCIPHER_LIB_NAME)
            logcat { "SqlCipher: native library loaded successfully" }
            libraryLoaded.complete(Unit)
        } catch (t: Throwable) {
            logcat(ERROR) { "SqlCipher: failed to load native library: ${t.message}" }
            pixel.fire(LIBRARY_LOAD_FAILURE_SQLCIPHER, type = Daily())
            libraryLoaded.completeExceptionally(t)
        }
    }

    private companion object {
        private const val SQLCIPHER_LIB_NAME = "sqlcipher"
    }
}

enum class SqlCipherPixelName(override val pixelName: String) : Pixel.PixelName {
    LIBRARY_LOAD_FAILURE_SQLCIPHER("library_load_failure_sqlcipher"),
}
