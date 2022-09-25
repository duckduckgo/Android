/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.app.browser

import android.net.Uri
import javax.inject.Inject

class DosDetector @Inject constructor() {

    var lastUrl: Uri? = null
    var lastUrlLoadTime: Long? = null
    var dosCount = 0

    fun isUrlGeneratingDos(url: Uri?): Boolean {

        val currentUrlLoadTime = System.currentTimeMillis()

        if (url != lastUrl) {
            reset(url, currentUrlLoadTime)
            return false
        }

        if (!withinDosTimeWindow(currentUrlLoadTime)) {
            reset(url, currentUrlLoadTime)
            return false
        }

        dosCount++
        lastUrlLoadTime = currentUrlLoadTime
        return dosCount > MAX_REQUESTS_COUNT
    }

    private fun reset(
        url: Uri?,
        currentLoadTime: Long
    ) {
        dosCount = 0
        lastUrl = url
        lastUrlLoadTime = currentLoadTime
    }

    private fun withinDosTimeWindow(currentLoadTime: Long): Boolean {
        val previousLoadTime = lastUrlLoadTime ?: return false
        return (currentLoadTime - previousLoadTime) < DOS_TIME_WINDOW_MS
    }

    companion object {
        const val DOS_TIME_WINDOW_MS = 1_000
        const val MAX_REQUESTS_COUNT = 20
    }
}
