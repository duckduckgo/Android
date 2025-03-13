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

package com.duckduckgo.pir.internal.common

import java.io.File

internal fun getMaximumParallelRunners(): Int {
    return try {
        // Get the directory containing CPU info
        val cpuDir = File("/sys/devices/system/cpu/")
        // Filter folders matching the pattern "cpu[0-9]+"
        val cpuFiles = cpuDir.listFiles { file -> file.name.matches(Regex("cpu[0-9]+")) }
        cpuFiles?.size ?: Runtime.getRuntime().availableProcessors()
    } catch (e: Exception) {
        // In case of an error, fall back to availableProcessors
        Runtime.getRuntime().availableProcessors()
    }
}

internal fun <T> List<T>.splitIntoParts(parts: Int): List<List<T>> {
    val chunkSize = (this.size + parts - 1) / parts // Ensure rounding up
    return this.chunked(chunkSize)
}
