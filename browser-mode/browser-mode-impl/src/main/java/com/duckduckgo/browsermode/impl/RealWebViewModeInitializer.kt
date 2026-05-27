/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.browsermode.impl

import android.annotation.SuppressLint
import android.webkit.WebView
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.browsermode.api.FireModeAvailability
import com.duckduckgo.browsermode.api.WebViewModeInitializer
import com.duckduckgo.browsermode.api.profileName
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealWebViewModeInitializer @Inject constructor(
    private val fireModeAvailability: FireModeAvailability,
    private val webViewProfileBinder: WebViewProfileBinder,
) : WebViewModeInitializer {
    override fun bind(webView: WebView, mode: BrowserMode): Result<Unit> {
        if (!fireModeAvailability.isAvailable()) {
            return Result.failure(
                IllegalStateException(
                    "Attempting to bind a WebView profile to " +
                        "Fire mode when the Fire mode feature is not available.",
                ),
            )
        }
        return runCatching {
            webViewProfileBinder.bind(webView, mode.profileName)
        }
    }
}

interface WebViewProfileBinder {
    fun bind(webView: WebView, profileName: String)
}

@ContributesBinding(AppScope::class)
@SuppressLint("RequiresFeature")
class AndroidXWebViewProfileBinder @Inject constructor() : WebViewProfileBinder {
    override fun bind(webView: WebView, profileName: String) {
        ProfileStore.getInstance().getOrCreateProfile(profileName)
        WebViewCompat.setProfile(webView, profileName)
    }
}
