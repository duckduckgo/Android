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

package com.duckduckgo.app.browser.omnibar

import android.net.Uri
import com.duckduckgo.app.browser.RequestRewriter
import com.duckduckgo.app.global.AppUrl.Url
import com.duckduckgo.app.global.UriString
import com.duckduckgo.app.global.UrlScheme.Companion.https
import com.duckduckgo.app.global.withScheme
import javax.inject.Inject

class QueryUrlConverter @Inject constructor(private val requestRewriter: RequestRewriter) : OmnibarEntryConverter {

    override fun convertQueryToUrl(query: String): String {
        if (UriString.isWebUrl(query)) {
            return convertUri(query)
        }

        val uriBuilder = Uri.Builder()
            .scheme(https)
            .appendQueryParameter("q", query)
            .authority(Url.HOST)

        requestRewriter.addCustomQueryParams(uriBuilder)
        return uriBuilder.build().toString()
    }

    private fun convertUri(input: String): String {
        val uri = Uri.parse(input).withScheme()

        if (uri.host == Url.HOST) {
            return requestRewriter.rewriteRequestWithCustomQueryParams(uri).toString()
        }

        return uri.toString()
    }

}