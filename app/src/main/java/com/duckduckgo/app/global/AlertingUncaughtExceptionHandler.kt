/*
 * Copyright (c) 2019 DuckDuckGo
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

import com.duckduckgo.app.global.exception.UncaughtExceptionRepository
import com.duckduckgo.app.global.exception.UncaughtExceptionSource
import com.duckduckgo.app.statistics.store.OfflinePixelCountDataStore
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.InterruptedIOException

class AlertingUncaughtExceptionHandler(
    private val originalHandler: Thread.UncaughtExceptionHandler,
    private val offlinePixelCountDataStore: OfflinePixelCountDataStore,
    private val uncaughtExceptionRepository: UncaughtExceptionRepository,
    private val dispatcherProvider: DispatcherProvider,
    private val appCoroutineScope: CoroutineScope,
    private val appBuildConfig: AppBuildConfig
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread?, originalException: Throwable?) {

        if (shouldRecordExceptionAndCrashApp(originalException)) {
            recordExceptionAndAllowCrash(thread, originalException)
            return
        }

        if (shouldCrashApp()) {
            originalHandler.uncaughtException(thread, originalException)
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

    /**
     * If the exception is one we don't report on, we still want to see a crash when we're in DEBUG builds for safety we aren't ignoring important issues
     */
    private fun shouldCrashApp(): Boolean = appBuildConfig.isDebug

    private fun recordExceptionAndAllowCrash(thread: Thread?, originalException: Throwable?) {
        appCoroutineScope.launch(dispatcherProvider.io() + NonCancellable) {
            try {
                uncaughtExceptionRepository.recordUncaughtException(originalException, UncaughtExceptionSource.GLOBAL)
                offlinePixelCountDataStore.applicationCrashCount += 1
            } catch (e: Throwable) {
                Timber.e(e, "Failed to record exception")
            } finally {
                originalHandler.uncaughtException(thread, originalException)
            }
        }
    }
}
