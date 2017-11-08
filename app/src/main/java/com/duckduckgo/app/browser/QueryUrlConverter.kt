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
import com.duckduckgo.app.browser.QueryUrlConverter.Query.baseUrl
import com.duckduckgo.app.browser.QueryUrlConverter.Query.httpScheme
import com.duckduckgo.app.browser.QueryUrlConverter.Query.httpsScheme
import com.duckduckgo.app.browser.QueryUrlConverter.Query.querySource
import com.duckduckgo.app.browser.QueryUrlConverter.Query.scheme
import timber.log.Timber
import javax.inject.Inject

class QueryUrlConverter @Inject constructor() {

    object Query {
        val scheme = "https"
        val baseUrl = "duckduckgo.com"
        val querySource = "ddg_android"
        val httpsScheme = "https://"
        val httpScheme = "http://"
    }

    fun convertInputToUri(inputQuery: String): String {
        if (inputQuery.startsWith(httpScheme) || inputQuery.startsWith(httpsScheme)) {
            Timber.d("Detected as full URL - $inputQuery")
            return inputQuery
        }

        return Uri.Builder()
                .scheme(scheme)
                .authority(baseUrl)
                .appendQueryParameter("q", inputQuery)
                .appendQueryParameter("tappv", formatAppVersion())
                .appendQueryParameter("t", querySource)
                .build()
                .toString()
    }

    private fun formatAppVersion(): String {
        return String.format("android_%s", BuildConfig.VERSION_NAME.replace(".", "_"))
    }

}