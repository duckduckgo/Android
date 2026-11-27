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
import com.duckduckgo.adblocking.impl.domain.AdBlockingStatusChecker
import com.duckduckgo.adblocking.impl.remoteconfig.AdBlockingExtensionFeature
import com.duckduckgo.app.browser.Domain
import com.duckduckgo.app.browser.UriString
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.browser.api.JsInjectorPlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.Toggle
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import logcat.logcat
import javax.inject.Inject

@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class)
class AdBlockingExtensionJsInjectorPlugin @Inject constructor(
    private val statusChecker: AdBlockingStatusChecker,
    repository: AdBlockingExtensionRepository,
    private val domainMatcher: AdBlockingExtensionDomainMatcher,
    private val contingencyMessageHandler: ContingencyMessageHandler,
    private val feature: AdBlockingExtensionFeature,
    @AppCoroutineScope appScope: CoroutineScope,
) : JsInjectorPlugin {

    private val payload: StateFlow<String?> = repository
        .scriptletsFlow()
        .map { buildScript(it, experimental = false) }
        .stateIn(appScope, SharingStarted.Eagerly, initialValue = null)

    private val experimentPayload: StateFlow<String?> = repository
        .scriptletsFlow()
        .map { buildScript(it, experimental = true) }
        .stateIn(appScope, SharingStarted.Eagerly, initialValue = null)

    @UiThread
    override fun onPageStarted(
        webView: WebView,
        url: String?,
        isDesktopMode: Boolean?,
        activeExperiments: List<Toggle>,
    ) {
        contingencyMessageHandler.cancelPendingShow()
        if (isExperimentDomain(url)) {
            injectExperiment(webView, activeExperiments)
            return
        }
        if (!statusChecker.canInject()) {
            logcat { "Status checker rejected injection, skipping" }
            return
        }
        if (!domainMatcher.matches(url)) {
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

    override fun onPageFinished(webView: WebView, url: String?, site: Site?) {
        contingencyMessageHandler.onPageLoaded(webView, url)
    }

    // Experiment sites ignore the user setting, so only the kill switch and contingency mode apply.
    private fun injectExperiment(webView: WebView, activeExperiments: List<Toggle>) {
        if (!feature.self().isEnabled() || feature.enableContingencyMode().isEnabled()) {
            logcat { "Kill-switch off or contingency mode on, skipping experiment" }
            return
        }
        val inTreatment = activeExperiments.any {
            it.featureName().name == EXPERIMENT_NAME && it.getRawStoredState()?.assignedCohort?.name == TREATMENT_COHORT
        }
        if (!inTreatment) {
            logcat { "Not in $EXPERIMENT_NAME $TREATMENT_COHORT, skipping experiment" }
            return
        }
        val script = experimentPayload.value ?: run {
            logcat { "Empty experiment payload, skipping" }
            return
        }
        logcat { "Injecting experiment script" }
        webView.evaluateJavascript("javascript:$script", null)
    }

    private fun isExperimentDomain(url: String?): Boolean {
        val uri = url?.toUri() ?: return false
        return EXPERIMENT_DOMAINS.any { UriString.sameOrSubdomain(uri, it) }
    }

    private fun buildScript(scriptlets: List<Scriptlet>, experimental: Boolean): String? =
        scriptlets
            .filter { it.name in ALLOWED_SCRIPTLET_NAMES && it.name.endsWith("/ublock-experimental.js") == experimental }
            .takeUnless { it.isEmpty() }
            ?.sortedBy { it.name }
            ?.joinToString(separator = "\n") { it.content }

    private companion object {
        const val EXPERIMENT_NAME = "contentScopeExperiment12"
        const val TREATMENT_COHORT = "treatment"
        val EXPERIMENT_DOMAINS = listOf(
            Domain("pornhub.com"),
            Domain("xvideos.com"),
            Domain("xhamster.com"),
            Domain("xnxx.com"),
        )
    }
}
