/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.app.trackerdetection

import android.os.Process
import android.os.SystemClock
import android.util.Log

// TEMPORARY instrumentation for measuring TDS load time on release builds. Do not merge.
// Uses android.util.Log directly because the logcat logger is only installed on debuggable builds.
internal object TdsTiming {
    fun now(): Long = SystemClock.uptimeMillis()

    fun log(message: String) {
        val sinceProcessStart = SystemClock.uptimeMillis() - Process.getStartUptimeMillis()
        Log.i("TdsTiming", "+${sinceProcessStart}ms [${Thread.currentThread().name}] $message")
    }
}
