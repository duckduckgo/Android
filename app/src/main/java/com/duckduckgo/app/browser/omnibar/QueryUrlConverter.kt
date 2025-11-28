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
import com.duckduckgo.app.browser.UriString.Companion.extractUrl
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.common.utils.AppUrl
import com.duckduckgo.common.utils.AppUrl.Url
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.UrlScheme.Companion.https
import com.duckduckgo.common.utils.withScheme
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.config.api.PrivacyConfigCallbackPlugin
import com.duckduckgo.urlpredictor.Decision
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@ContributesBinding(AppScope::class, boundType = OmnibarEntryConverter::class)
@ContributesMultibinding(scope = AppScope::class, boundType = PrivacyConfigCallbackPlugin::class)
@SingleInstanceIn(scope = AppScope::class)
class QueryUrlConverter @Inject constructor(
    private val requestRewriter: RequestRewriter,
    private val androidBrowserConfigFeature: AndroidBrowserConfigFeature,
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val queryUrlPredictor: QueryUrlPredictor,
) : OmnibarEntryConverter, PrivacyConfigCallbackPlugin {

    @Volatile
    private var useUrlPredictor: Boolean = false

    init {
        cacheRemoteConfig()
    }

    override fun onPrivacyConfigDownloaded() {
        cacheRemoteConfig()
    }

    override fun convertQueryToUrl(
        searchQuery: String,
        vertical: String?,
        queryOrigin: QueryOrigin,
        extractUrlFromQuery: Boolean,
    ): String {
        val isUrl = when (queryOrigin) {
            is QueryOrigin.FromAutocomplete -> queryOrigin.isNav
            is QueryOrigin.FromUser, QueryOrigin.FromBookmark -> {
                if (useUrlPredictor && queryUrlPredictor.isReady()) {
                    var queryClassification = queryUrlPredictor.classify(input = searchQuery)

                    if (extractUrlFromQuery) {
                        var currentInput = searchQuery
                        val seenInputs = mutableSetOf(currentInput)

                        while (queryClassification is Decision.Search) {
                            currentInput = extractUrl(inputQuery = currentInput, cleanInputQuery = true)
                                ?: break // no URL found

                            if (!seenInputs.add(currentInput)) break // cycle detected

                            queryClassification = queryUrlPredictor.classify(input = currentInput)
                        }
                    }

                    // TODO remove the DuckUri check when the predictor is updated to include 'duck' scheme
                    queryClassification is Decision.Navigate || UriString.isDuckUri(searchQuery)
                } else {
                    UriString.isWebUrl(searchQuery, extractUrlFromQuery) || UriString.isDuckUri(
                        searchQuery,
                    )
                }
            }
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
        val url = UriString.extractUrl(input, cleanInputQuery = false) ?: input
        val uri = Uri.parse(url).withScheme()

        if (requestRewriter.shouldRewriteRequest(uri)) {
            return requestRewriter.rewriteRequestWithCustomQueryParams(uri).toString()
        }

        return uri.toString()
    }

    private fun cacheRemoteConfig() {
        coroutineScope.launch(dispatcherProvider.io()) {
            useUrlPredictor = androidBrowserConfigFeature.useUrlPredictor().isEnabled()
        }
    }

    companion object {
        val majorVerticals = listOf("images", "videos", "news", "shopping")
    }
}
