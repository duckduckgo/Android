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
import com.duckduckgo.common.utils.AppUrl
import com.duckduckgo.common.utils.AppUrl.ParamKey
import com.duckduckgo.common.utils.AppUrl.ParamValue
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import okhttp3.HttpUrl.Companion.toHttpUrl
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class DuckDuckGoUrlDetectorImpl @Inject constructor() : DuckDuckGoUrlDetector {

    override fun isDuckDuckGoEmailUrl(url: String): Boolean {
        val uri = url.toUri()
        val firstSegment = uri.pathSegments.firstOrNull()
        return isDuckDuckGoUrl(url) && firstSegment == AppUrl.Url.EMAIL_SEGMENT
    }

    override fun isDuckDuckGoUrl(url: String): Boolean {
        return runCatching { AppUrl.Url.HOST == url.toHttpUrl().topPrivateDomain() }.getOrElse { false }
    }

    override fun isDuckDuckGoQueryUrl(uri: String): Boolean {
        return isDuckDuckGoUrl(uri) && hasQuery(uri)
    }

    override fun isDuckDuckGoStaticUrl(uri: String): Boolean {
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

    override fun extractQuery(uriString: String): String? {
        val uri = uriString.toUri()
        return uri.getQueryParameter(ParamKey.QUERY)
    }

    override fun isDuckDuckGoVerticalUrl(uri: String): Boolean {
        return isDuckDuckGoUrl(uri) && hasVertical(uri)
    }

    private fun hasVertical(uri: String): Boolean {
        return uri.toUri().queryParameterNames.contains(ParamKey.VERTICAL)
    }

    override fun extractVertical(uriString: String): String? {
        val uri = uriString.toUri()
        return uri.getQueryParameter(ParamKey.VERTICAL)
    }

    override fun isDuckDuckGoChatUrl(uri: String): Boolean {
        return isDuckDuckGoUrl(uri) && hasAIChatVertical(uri)
    }

    private fun hasAIChatVertical(uri: String): Boolean {
        val vertical = extractVertical(uri)
        return vertical == ParamValue.CHAT_VERTICAL
    }

    private fun String.toUri(): Uri {
        return Uri.parse(this)
    }
}
