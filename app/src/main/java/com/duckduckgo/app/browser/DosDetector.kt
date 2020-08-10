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
        val lastKnownRefresh = lastUrlLoadTime
        val now = System.currentTimeMillis()

        val isUrlGeneratingDos =
            !(dosCount < MAX_REQUESTS_COUNT || lastKnownRefresh == null || (now - lastKnownRefresh) > MIN_DIFF_IN_MS || lastUrl != url)

        if (lastUrl != url) {
            dosCount = 0
        } else {
            dosCount++
        }

        lastUrl = url
        lastUrlLoadTime = System.currentTimeMillis()
        return isUrlGeneratingDos
    }

    companion object {
        const val MIN_DIFF_IN_MS = 1_000
        const val MAX_REQUESTS_COUNT = 20
    }
}
