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
import com.duckduckgo.app.anr.CrashPixel.APPLICATION_CRASH_GLOBAL
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.LogPriority.ERROR
import logcat.asLog
import logcat.logcat
import java.io.InterruptedIOException
import javax.inject.Inject
import javax.inject.Qualifier

@ContributesBinding(AppScope::class)
class GlobalUncaughtExceptionHandler @Inject constructor(
    @InternalApi private val originalHandler: Thread.UncaughtExceptionHandler,
    private val crashLogger: CrashLogger,
    private val dispatcherProvider: DispatcherProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(
        thread: Thread?,
        originalException: Throwable?,
    ) {
        runCatching {
            if (shouldRecordExceptionAndCrashApp(originalException)) {
                recordExceptionAndAllowCrash(thread, originalException)
                return
            }
        }
    }

    /**
     * Some exceptions happen due to lifecycle issues, such as our sync service being forced to stop.
     * In such cases, we don't need to alert about the exception as they are exceptions essentially signalling that work was interrupted.
     * Examples of this would be if the internet was lost during the sync,
     * or when two or more sync operations are scheduled to run at the same time; one would run and the rest would be interrupted.
     */
    private fun shouldRecordExceptionAndCrashApp(exception: Throwable?): Boolean {
        return when (exception) {
            is InterruptedException, is InterruptedIOException -> false
            else -> true
        }
    }

    private fun recordExceptionAndAllowCrash(
        thread: Thread?,
        originalException: Throwable?,
    ) {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            try {
                originalException?.let {
                    crashLogger.logCrash(
                        CrashLogger.Crash(
                            shortName = APPLICATION_CRASH_GLOBAL.pixelName,
                            t = it,
                        ),
                    )
                }
            } catch (e: Throwable) {
                logcat(ERROR) { e.asLog() }
            } finally {
                originalHandler.uncaughtException(thread, originalException)
            }
        }
    }
}

@Retention(AnnotationRetention.BINARY)
@Qualifier
private annotation class InternalApi

@Module
@ContributesTo(AppScope::class)
object UncaughtExceptionHandlerModule {
    @Provides
    @InternalApi
    fun provideDefaultUncaughtExceptionHandler(): Thread.UncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
}
