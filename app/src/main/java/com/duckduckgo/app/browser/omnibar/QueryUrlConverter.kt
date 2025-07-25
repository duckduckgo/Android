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
import android.webkit.URLUtil
import com.duckduckgo.app.browser.RequestRewriter
import com.duckduckgo.app.browser.UriString
import com.duckduckgo.common.utils.AppUrl
import com.duckduckgo.common.utils.AppUrl.Url
import com.duckduckgo.common.utils.UrlScheme.Companion.https
import com.duckduckgo.common.utils.withScheme
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class QueryUrlConverter @Inject constructor(private val requestRewriter: RequestRewriter) : OmnibarEntryConverter {

    override fun convertQueryToUrl(
        searchQuery: String,
        vertical: String?,
        queryOrigin: QueryOrigin,
        extractUrlFromQuery: Boolean,
    ): String {
        val isUrl = when (queryOrigin) {
            is QueryOrigin.FromAutocomplete -> queryOrigin.isNav
            is QueryOrigin.FromUser, QueryOrigin.FromBookmark -> UriString.isWebUrl(searchQuery, extractUrlFromQuery) || UriString.isDuckUri(
                searchQuery,
            )
        }

        if (isUrl == true) {
            return convertUri(searchQuery)
        }

        if (URLUtil.isDataUrl(searchQuery) || URLUtil.isAssetUrl(searchQuery)) {
            return searchQuery
        }

        val uriBuilder = Uri.Builder()
            .scheme(https)
            .appendQueryParameter(AppUrl.ParamKey.QUERY, searchQuery)
            .authority(Url.HOST)

        if (vertical != null && majorVerticals.contains(vertical)) {
            uriBuilder.appendQueryParameter(AppUrl.ParamKey.VERTICAL_REWRITE, vertical)
        }

        requestRewriter.addCustomQueryParams(uriBuilder)
        return uriBuilder.build().toString()
    }

    private fun convertUri(input: String): String {
        val url = UriString.extractUrl(input) ?: input
        val uri = Uri.parse(url).withScheme()

        if (requestRewriter.shouldRewriteRequest(uri)) {
            return requestRewriter.rewriteRequestWithCustomQueryParams(uri).toString()
        }

        return uri.toString()
    }

    companion object {
        val majorVerticals = listOf("images", "videos", "news", "shopping")
    }
}
