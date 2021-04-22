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
import com.duckduckgo.app.global.baseHost
import javax.inject.Inject

class DuckDuckGoUrlDetector @Inject constructor() {

    fun isDuckDuckGoDomain(uri: String): Boolean {
        return uri.toUri().baseHost?.contains(AppUrl.Url.HOST) ?: false
    }

    fun isDuckDuckGoUrl(uri: String): Boolean {
        return AppUrl.Url.HOST == uri.toUri().host
    }

    fun isDuckDuckGoQueryUrl(uri: String): Boolean {
        return isDuckDuckGoUrl(uri) && hasQuery(uri)
    }

    fun isDuckDuckGoStaticUrl(uri: String): Boolean {
        return isDuckDuckGoUrl(uri) && matchesStaticPage(uri)
    }

    private fun matchesStaticPage(uri: String): Boolean {
        return when (uri.toUri().path) {
            AppUrl.StaticUrl.SETTINGS -> true
            AppUrl.StaticUrl.PARAMS -> true
            else -> false
        }
    }

    private fun hasQuery(uri: String): Boolean {
        return uri.toUri().queryParameterNames.contains(ParamKey.QUERY)
    }

    fun extractQuery(uriString: String): String? {
        val uri = uriString.toUri()
        return uri.getQueryParameter(ParamKey.QUERY)
    }

    fun isDuckDuckGoVerticalUrl(uri: String): Boolean {
        return isDuckDuckGoUrl(uri) && hasVertical(uri)
    }

    private fun hasVertical(uri: String): Boolean {
        return uri.toUri().queryParameterNames.contains(ParamKey.VERTICAL)
    }

    fun extractVertical(uriString: String): String? {
        val uri = uriString.toUri()
        return uri.getQueryParameter(ParamKey.VERTICAL)
    }

    private fun String.toUri(): Uri {
        return Uri.parse(this)
    }
}
