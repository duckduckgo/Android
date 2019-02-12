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

package com.duckduckgo.app.global.performance

import android.util.Log
import timber.log.Timber
import java.util.concurrent.TimeUnit


inline fun <T> measureExecution(logMessage: String, logLevel: Int = Log.DEBUG, function: () -> T): T {
    val startTime = System.nanoTime()
    return function.invoke().also {
        val difference = System.nanoTime() - startTime
        Timber.log(logLevel, "$logMessage; took ${TimeUnit.NANOSECONDS.toMillis(difference)}ms")
    }
}