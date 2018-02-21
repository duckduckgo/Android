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
import com.duckduckgo.app.global.AppUrl
import com.duckduckgo.app.global.AppUrl.ParamKey
import javax.inject.Inject


class DuckDuckGoUrlDetector @Inject constructor() {

    fun isDuckDuckGoUrl(uri: Uri): Boolean {
        return AppUrl.Url.HOST == uri.host
    }

    fun isDuckDuckGoUrl(uriString: String): Boolean {
        return isDuckDuckGoUrl(uriString.toUri())
    }

    fun isDuckDuckGoQueryUrl(uriString: String): Boolean {
        return isDuckDuckGoUrl(uriString.toUri()) && hasQuery(uriString)
    }

    private fun hasQuery(uriString: String): Boolean {
        return uriString.toUri().queryParameterNames.contains(ParamKey.QUERY)
    }

    fun extractQuery(uriString: String): String? {
        val uri = uriString.toUri()
        return uri.getQueryParameter(ParamKey.QUERY)
    }

    private fun String.toUri(): Uri {
        return Uri.parse(this)
    }
}