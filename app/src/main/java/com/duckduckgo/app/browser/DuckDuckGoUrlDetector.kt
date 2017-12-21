/*
 * Copyright (c) 2017 DuckDuckGo
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


class DuckDuckGoUrlDetector @Inject constructor() {

    fun isDuckDuckGoUrl(uri: Uri): Boolean {
        return "duckduckgo.com" == uri.host
    }

    fun isDuckDuckGoUrl(uriString: String): Boolean {
        return isDuckDuckGoUrl(uriString.toUri())
    }

    fun hasQuery(uriString: String): Boolean {
        return uriString.toUri().queryParameterNames.contains(queryParameter)
    }

    fun extractQuery(uriString: String): String? {
        val uri = uriString.toUri()
        return uri.getQueryParameter(queryParameter)
    }

    private companion object {
        const val queryParameter = "q"
    }

    private fun String.toUri(): Uri {
        return Uri.parse(this)
    }
}