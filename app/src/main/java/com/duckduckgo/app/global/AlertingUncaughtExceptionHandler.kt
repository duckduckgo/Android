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
import io.reactivex.exceptions.UndeliverableException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.IOException

class AlertingUncaughtExceptionHandler(
    private val originalHandler: Thread.UncaughtExceptionHandler,
    private val offlinePixelCountDataStore: OfflinePixelCountDataStore,
    private val uncaughtExceptionRepository: UncaughtExceptionRepository
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(t: Thread?, originalException: Throwable?) {

        if (shouldIgnore(originalException)) {
            Timber.w(originalException, "An exception occurred but we don't need to handle it")
            return
        }

        GlobalScope.launch(Dispatchers.IO + NonCancellable) {
            uncaughtExceptionRepository.recordUncaughtException(originalException, UncaughtExceptionSource.GLOBAL)
            offlinePixelCountDataStore.applicationCrashCount += 1

            // wait until the exception has been fully processed before propagating exception
            originalHandler.uncaughtException(t, originalException)
        }
    }

    /**
     * Some exceptions happen due to lifecycle issues, such as our sync service being forced to stop.
     * In such cases, we don't need to alert about the exception as they are exceptions essentially signalling that work was interrupted.
     * Examples of this would be if the internet was lost during the sync,
     * or when two or more sync operations are scheduled to run at the same time; one would run and the rest would be interrupted.
     */
    private fun shouldIgnore(exception: Throwable?): Boolean {
        return when (exception) {
            is UndeliverableException, is InterruptedException, is IOException -> true
            else -> false
        }
    }
}