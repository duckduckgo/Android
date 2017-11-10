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
import android.webkit.WebResourceRequest
import com.duckduckgo.app.browser.DuckDuckGoRequestRewriter.Constants.appVersionParam
import com.duckduckgo.app.browser.DuckDuckGoRequestRewriter.Constants.sourceParam
import com.duckduckgo.app.browser.omnibar.QueryUrlConverter
import timber.log.Timber
import javax.inject.Inject

class DuckDuckGoRequestRewriter @Inject constructor() {

    object Constants {
        val sourceParam = "t"
        val appVersionParam = "tappv"
    }

    fun rewriteRequestWithCustomQueryParams(request: Uri): Uri {
        val builder = Uri.Builder()
                .authority(request.authority)
                .scheme(request.scheme)
                .path(request.path)
                .fragment(request.fragment)

        request.queryParameterNames
                .filter { it != sourceParam && it != appVersionParam }
                .forEach { builder.appendQueryParameter(it, request.getQueryParameter(it)) }

        addCustomQueryParams(builder)
        val newUri = builder.build()

        Timber.d("Rewriting request\n$request [original]\n$newUri [rewritten]")
        return newUri
    }

    fun shouldRewriteRequest(request: WebResourceRequest): Boolean {
        return request.url.host == "duckduckgo.com" && !request.url.queryParameterNames.containsAll(arrayListOf(sourceParam, appVersionParam))
    }

    fun addCustomQueryParams(builder: Uri.Builder) {
        builder.appendQueryParameter(appVersionParam, formatAppVersion())
        builder.appendQueryParameter(sourceParam, QueryUrlConverter.Query.querySource)
    }

    private fun formatAppVersion(): String {
        return String.format("android_%s", BuildConfig.VERSION_NAME.replace(".", "_"))
    }
}
