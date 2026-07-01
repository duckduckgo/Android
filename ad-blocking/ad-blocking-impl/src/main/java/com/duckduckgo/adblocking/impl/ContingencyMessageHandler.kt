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
import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.adblocking.api.duckplayer.YOUTUBE_HOST
import com.duckduckgo.adblocking.api.duckplayer.YOUTUBE_MOBILE_HOST
import com.duckduckgo.adblocking.impl.remoteconfig.AdBlockingExtensionFeature
import com.duckduckgo.adblocking.impl.remoteconfig.ContingencyMessageStore
import com.duckduckgo.app.browser.UriString
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
import logcat.logcat
import javax.inject.Inject

/**
 * Decides whether to show the ad-blocking contingency bottom sheet and shows it. It is driven by
 * [AdBlockingExtensionJsInjectorPlugin] on page load rather than being a [JsInjectorPlugin] itself,
 * so that JS injection and UI concerns stay separated.
 */
interface ContingencyMessageHandler {
    /** Called on page load. Shows the contingency message if all conditions are met. */
    @UiThread
    fun onPageLoaded(webView: WebView, url: String?)
}

/**
 * Shows a one-off bottom sheet informing the user that YouTube ad blocking is temporarily
 * unavailable while ad-blocking contingency mode is active. Gated behind the
 * [AdBlockingExtensionFeature.adBlockingUXImprovements] rollout flag. The message is shown only the
 * first time the user reaches YouTube after contingency mode is enabled; the "shown" state is reset
 * whenever contingency mode is disabled so it shows again next time it is enabled.
 */
@SingleInstanceIn(AppScope::class)
@ContributesBinding(scope = AppScope::class, boundType = ContingencyMessageHandler::class)
@ContributesMultibinding(scope = AppScope::class, boundType = MainProcessLifecycleObserver::class)
class RealContingencyMessageHandler @Inject constructor(
    private val feature: AdBlockingExtensionFeature,
    private val store: ContingencyMessageStore,
    @AppCoroutineScope private val appScope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
) : ContingencyMessageHandler, MainProcessLifecycleObserver {

    private var dialog: ContingencyMessageBottomSheet? = null

    override fun onCreate(owner: LifecycleOwner) {
        // Reset the "shown" flag whenever contingency mode is disabled, so the message shows again
        // next time it is (re)enabled. enableContingencyMode().enabled() re-emits on remote config changes.
        appScope.launch(dispatchers.io()) {
            feature.enableContingencyMode().enabled()
                .distinctUntilChanged()
                .collect { onContingencyModeChanged(it) }
        }
    }

    @UiThread
    override fun onPageLoaded(webView: WebView, url: String?) {
        logcat { "$TAG onPageLoaded url=$url" }
        if (!shouldShow(url)) return
        if (dialog?.isShowing == true) {
            logcat { "$TAG dialog already showing, skipping" }
            return
        }

        logcat { "$TAG showing contingency message" }
        showMessage(webView)
        appScope.launch(dispatchers.io()) { store.setShown() }
    }

    internal suspend fun onContingencyModeChanged(contingencyEnabled: Boolean) {
        val shown = store.shown.value
        logcat { "$TAG onContingencyModeChanged contingencyEnabled=$contingencyEnabled shown=$shown" }
        if (!contingencyEnabled && shown) {
            logcat { "$TAG resetting shown state" }
            store.reset()
        }
    }

    internal fun shouldShow(url: String?): Boolean {
        val uxImprovements = feature.adBlockingUXImprovements().isEnabled()
        val contingency = feature.enableContingencyMode().isEnabled()
        val shown = store.shown.value
        val uri = url?.toUri()
        val isYouTube = uri != null &&
            (UriString.sameOrSubdomain(uri, YOUTUBE_HOST) || UriString.sameOrSubdomain(uri, YOUTUBE_MOBILE_HOST))
        val result = uxImprovements && contingency && isYouTube && !shown
        logcat {
            "$TAG shouldShow=$result (uxImprovements=$uxImprovements, contingency=$contingency, " +
                "isYouTube=$isYouTube, shown=$shown, url=$url)"
        }
        return result
    }

    private fun showMessage(webView: WebView) {
        dialog = ContingencyMessageBottomSheet(webView.context).also { it.show() }
    }

    companion object {
        private const val TAG = "ContingencyMessage:"
    }
}
