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

package com.duckduckgo.adblocking.impl

import android.webkit.WebView
import androidx.annotation.UiThread
import androidx.core.net.toUri
import com.duckduckgo.adblocking.impl.remoteconfig.AdBlockingExtensionFeature
import com.duckduckgo.app.browser.Domain
import com.duckduckgo.app.browser.UriString
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.browser.api.JsInjectorPlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.Toggle
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import logcat.logcat
import javax.inject.Inject

@SingleInstanceIn(AppScope::class)
@ContributesMultibinding(AppScope::class)
class AdBlockingExtensionJsInjectorPlugin @Inject constructor(
    private val feature: AdBlockingExtensionFeature,
    repository: AdBlockingExtensionRepository,
    @AppCoroutineScope appScope: CoroutineScope,
) : JsInjectorPlugin {

    private val payload: StateFlow<String?> = repository
        .scriptletsFlow()
        .map(::buildScript)
        .stateIn(appScope, SharingStarted.Eagerly, initialValue = null)

    @UiThread
    override fun onPageStarted(
        webView: WebView,
        url: String?,
        isDesktopMode: Boolean?,
        activeExperiments: List<Toggle>,
    ) {
        if (!feature.isDiscoverable().isEnabled()) {
            logcat { "Feature not discoverable, skipping" }
            return
        }
        if (!feature.self().isEnabled()) {
            logcat { "Feature not operational, skipping" }
            return
        }
        val uri = url?.toUri() ?: return
        if (domains.none { UriString.sameOrSubdomain(uri, it) }) {
            logcat { "No domains matching, skipping" }
            return
        }
        val script = payload.value ?: run {
            logcat { "Empty payload, skipping" }
            return
        }

        logcat { "Injecting script" }
        webView.evaluateJavascript("javascript:$script", null)
    }

    override fun onPageFinished(webView: WebView, url: String?, site: Site?) = Unit

    private fun buildScript(scriptlets: List<Scriptlet>): String? =
        scriptlets
            .takeUnless { it.isEmpty() }
            ?.sortedBy { it.name }
            ?.joinToString(separator = "\n") { it.content }

    private val domains = listOf(
        Domain("youtube.com"),
        Domain("youtube-nocookie.com"),
    )
}
