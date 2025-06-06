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

@file:Suppress("unused")

package com.duckduckgo.app.global.performance

import android.util.Log
import logcat.LogPriority.DEBUG
import logcat.LogPriority.ERROR
import logcat.LogPriority.INFO
import logcat.LogPriority.VERBOSE
import logcat.LogPriority.WARN
import logcat.logcat

object PerformanceConstants {
    const val NANO_TO_MILLIS_DIVISOR = 1_000_000.0
}

inline fun <T> measureExecution(
    logMessage: String,
    logLevel: Int = Log.DEBUG,
    function: () -> T,
): T {
    val startTime = System.nanoTime()
    return function.invoke().also {
        val difference = (System.nanoTime() - startTime) / PerformanceConstants.NANO_TO_MILLIS_DIVISOR
        val priority = when (logLevel) {
            Log.VERBOSE -> VERBOSE
            Log.DEBUG -> DEBUG
            Log.INFO -> INFO
            Log.WARN -> WARN
            Log.ERROR -> ERROR
            else -> DEBUG
        }
        logcat(tag = "Performance", priority = priority) { "$logMessage; took ${difference}ms" }
    }
}
