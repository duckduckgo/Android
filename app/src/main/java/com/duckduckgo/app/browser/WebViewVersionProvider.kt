/*
 * Copyright (c) 2022 DuckDuckGo
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

import com.duckduckgo.app.browser.WebViewVersionProvider.Companion.WEBVIEW_UNKNOWN_VERSION
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface WebViewVersionProvider {
    companion object {
        const val WEBVIEW_UNKNOWN_VERSION = "unknown"
    }

    fun getFullVersion(): String
    fun getMajorVersion(): String
}

@ContributesBinding(AppScope::class)
class DefaultWebViewVersionProvider @Inject constructor(
    private val webViewVersionSource: WebViewVersionSource
) : WebViewVersionProvider {
    companion object {
        const val WEBVIEW_VERSION_DELIMITER = "."
    }

    override fun getFullVersion(): String = webViewVersionSource.get().mapEmptyToUnknown()

    override fun getMajorVersion(): String =
        webViewVersionSource.get().captureMajorVersion().mapNonIntegerToUnknown()

    private fun String.captureMajorVersion() = this.split(WEBVIEW_VERSION_DELIMITER)[0]

    private fun String.mapNonIntegerToUnknown() =
        if (isNotBlank() && all(Char::isDigit)) this else WEBVIEW_UNKNOWN_VERSION

    private fun String.mapEmptyToUnknown() = if (isBlank()) WEBVIEW_UNKNOWN_VERSION else this
}
