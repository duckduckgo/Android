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
import timber.log.Timber

interface RequestRewriter {
    fun shouldRewriteRequest(uri: Uri): Boolean
    fun rewriteRequestWithCustomQueryParams(request: Uri): Uri
    fun addCustomQueryParams(builder: Uri.Builder)
}

class DuckDuckGoRequestRewriter(private val duckDuckGoUrlDetector: DuckDuckGoUrlDetector) : RequestRewriter {

    companion object {
        private const val sourceParam = "t"
        private const val appVersionParam = "tappv"
        private const val querySource = "ddg_android"
    }

    override fun rewriteRequestWithCustomQueryParams(request: Uri): Uri {
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

    override fun shouldRewriteRequest(uri: Uri): Boolean {
        return duckDuckGoUrlDetector.isDuckDuckGoUrl(uri) &&
                !uri.queryParameterNames.containsAll(arrayListOf(sourceParam, appVersionParam))
    }

    override fun addCustomQueryParams(builder: Uri.Builder) {
        builder.appendQueryParameter(appVersionParam, formatAppVersion())
        builder.appendQueryParameter(sourceParam, querySource)
    }

    private fun formatAppVersion(): String {
        return String.format("android_%s", BuildConfig.VERSION_NAME.replace(".", "_"))
    }
}
