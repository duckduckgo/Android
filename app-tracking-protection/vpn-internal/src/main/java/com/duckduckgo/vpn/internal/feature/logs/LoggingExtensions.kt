/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.vpn.internal.feature.logs

import logcat.AndroidLogcatLogger
import logcat.LogPriority
import logcat.LogcatLogger
import logcat.logcat

class LoggingExtensions {
    companion object {
        @JvmStatic
        fun isLoggingEnabled(): Boolean {
            return LogcatLogger.isInstalled
        }

        @JvmStatic
        fun disableLogging() {
            logcat { "Logging Stopped" }
            LogcatLogger.uninstall()
        }

        @JvmStatic
        fun enableLogging() {
            LogcatLogger.install(AndroidLogcatLogger(LogPriority.VERBOSE))
            logcat { "Logging Started" }
        }
    }
}
