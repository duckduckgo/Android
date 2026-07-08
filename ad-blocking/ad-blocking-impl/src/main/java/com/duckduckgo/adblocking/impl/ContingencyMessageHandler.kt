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
import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.adblocking.impl.remoteconfig.AdBlockingExtensionFeature
import com.duckduckgo.adblocking.impl.remoteconfig.ContingencyMessageStore
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

interface ContingencyMessageHandler {
    /** Called on page load. Shows the contingency message if all conditions are met. */
    @UiThread
    fun onPageLoaded(webView: WebView, url: String?)
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(scope = AppScope::class, boundType = ContingencyMessageHandler::class)
@ContributesMultibinding(scope = AppScope::class, boundType = MainProcessLifecycleObserver::class)
class RealContingencyMessageHandler @Inject constructor(
    private val feature: AdBlockingExtensionFeature,
    private val store: ContingencyMessageStore,
    private val view: ContingencyMessageView,
    private val domainMatcher: AdBlockingExtensionDomainMatcher,
    @AppCoroutineScope private val appScope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
) : ContingencyMessageHandler, MainProcessLifecycleObserver {

    @Volatile
    private var shownInSession = false

    override fun onCreate(owner: LifecycleOwner) {
        appScope.launch(dispatchers.io()) {
            feature.enableContingencyMode().enabled()
                .distinctUntilChanged()
                .collect { onContingencyModeChanged(it) }
        }
    }

    @UiThread
    override fun onPageLoaded(webView: WebView, url: String?) {
        if (!webView.isShown) return
        if (!shouldShow(url)) return
        view.show(webView) {
            shownInSession = true
            appScope.launch(dispatchers.io()) { store.setShown() }
        }
    }

    internal suspend fun onContingencyModeChanged(contingencyEnabled: Boolean) {
        val shown = store.shown.value
        if (!contingencyEnabled) {
            shownInSession = false
            if (shown) store.reset()
        }
    }

    internal fun shouldShow(url: String?): Boolean {
        val uxImprovements = feature.adBlockingUXImprovements().isEnabled()
        val contingency = feature.enableContingencyMode().isEnabled()
        val shown = shownInSession || store.shown.value
        val isExtensionDomain = domainMatcher.matches(url)
        val result = uxImprovements && contingency && isExtensionDomain && !shown
        return result
    }
}
