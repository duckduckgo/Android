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

package com.duckduckgo.app.fire

import android.content.Context
import java.io.File

interface DatabaseLocator {
    fun getDatabasePath(): String
}

class WebViewDatabaseLocator(private val context: Context) : DatabaseLocator {

    private val knownLocations = listOf("/app_webview/Default/Cookies", "/app_webview/Cookies")

    override fun getDatabasePath(): String {
        val dataDir = context.applicationInfo.dataDir
        val detectedPath = knownLocations.find { knownPath ->
            val file = File(dataDir, knownPath)
            file.exists()
        }

        return detectedPath
            .takeUnless { it.isNullOrEmpty() }
            ?.let { nonEmptyPath ->
                "$dataDir$nonEmptyPath"
            }.orEmpty()
    }
}
