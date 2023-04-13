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

package com.duckduckgo.anrs.api

import androidx.annotation.WorkerThread

interface CrashLogger {
    /**
     * Logs the [Crash] to be sent later on to the backend
     *
     * This method shall be executed off the main thread otherwise it will throw [IllegalStateException]
     *
     *@param crash [Crash] model
     */
    @WorkerThread
    fun logCrash(crash: Crash)

    data class Crash(
        val shortName: String,
        val t: Throwable,
    )
}
