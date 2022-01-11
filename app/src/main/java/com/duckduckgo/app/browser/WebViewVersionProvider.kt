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

import android.content.Context
import androidx.webkit.WebViewCompat
import com.duckduckgo.app.browser.WebViewVersionProvider.Companion.WEBVIEW_UNKNOWN_VERSION
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface WebViewVersionProvider {
    companion object {
        const val WEBVIEW_UNKNOWN_VERSION = "unknown"
    }

    fun get(): String
}

interface RawWebViewVersionProvider {
    fun get(): String?
}

@ContributesBinding(AppScope::class)
class WebViewCompatRawWebViewVersionProvider @Inject constructor(
    private val context: Context
) : RawWebViewVersionProvider {

    override fun get(): String? = WebViewCompat.getCurrentWebViewPackage(context)?.versionName

}

@ContributesBinding(AppScope::class)
class MajorWebViewVersionProvider @Inject constructor(
    private val rawWebViewVersionProvider: RawWebViewVersionProvider
) : WebViewVersionProvider {
    companion object {
        const val WEBVIEW_VERSION_DELIMITER = "."
    }

    override fun get(): String = rawWebViewVersionProvider.get().run {
        if (this.isNullOrEmpty()) {
            WEBVIEW_UNKNOWN_VERSION
        } else this.captureMajorVersion()
    }

    private fun String.captureMajorVersion() = this.split(WEBVIEW_VERSION_DELIMITER)[0]
}
