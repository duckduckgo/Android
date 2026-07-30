/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.contentscopescripts.impl.features.contentscopeexperiments

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.contentscopescripts.api.contentscopeExperiments.ContentScopeExperiments
import com.duckduckgo.contentscopescripts.impl.ContentScopeScriptsFeature
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.FeatureTogglesInventory
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.privacy.config.api.PrivacyConfigCallbackPlugin
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject

@SingleInstanceIn(AppScope::class)
@ContributesBinding(
    scope = AppScope::class,
    boundType = ContentScopeExperiments::class,
)
@ContributesMultibinding(
    scope = AppScope::class,
    boundType = PrivacyConfigCallbackPlugin::class,
)
class RealContentScopeExperiments @Inject constructor(
    private val contentScopeExperimentsFeature: ContentScopeExperimentsFeature,
    private val contentScopeScriptsFeature: ContentScopeScriptsFeature,
    private val featureTogglesInventory: FeatureTogglesInventory,
    private val dispatcherProvider: DispatcherProvider,
) : ContentScopeExperiments, PrivacyConfigCallbackPlugin {

    /**
     * Resolving the active experiments walks every feature toggle in the app and enrols each candidate, which is too
     * expensive to repeat per navigation: page start awaits it before content scope scripts can be injected. The
     * result only changes when a new privacy config lands, so it is held until then.
     */
    @Volatile
    private var activeExperiments: List<Toggle>? = null

    /**
     * Sampled rather than read per call because the flag is itself a toggle: reading it on every navigation would put
     * back a chunk of the cost this is here to remove. Cleared alongside the result so a remote change to the
     * kill-switch takes effect at the next privacy config update rather than needing a restart.
     */
    @Volatile
    private var cachingEnabled: Boolean? = null

    private val resolveMutex = Mutex()

    override suspend fun getActiveExperiments(): List<Toggle> {
        // Returning without suspending matters as much as holding the result: the caller awaits this on the main
        // thread before injecting, so dispatching to io() and back would cost a main looper turn even for a hit.
        activeExperiments?.let { return it }

        // Resolving straight through, rather than under the mutex, keeps the disabled path exactly as it shipped:
        // concurrent navigations in different tabs resolve in parallel instead of queueing behind each other.
        if (!cachingEnabled()) return resolve()

        return resolveMutex.withLock {
            activeExperiments ?: resolve().also { activeExperiments = it }
        }
    }

    override fun onPrivacyConfigDownloaded() {
        invalidate()
    }

    override fun onPrivacyConfigPersisted() {
        invalidate()
    }

    private fun invalidate() {
        activeExperiments = null
        cachingEnabled = null
    }

    private suspend fun cachingEnabled(): Boolean =
        cachingEnabled ?: withContext(dispatcherProvider.io()) {
            contentScopeScriptsFeature.cacheContentScopeExperiments().isEnabled()
        }.also { cachingEnabled = it }

    private suspend fun resolve(): List<Toggle> =
        withContext(dispatcherProvider.io()) {
            val featureName = contentScopeExperimentsFeature.self().featureName().name
            val experiments =
                if (contentScopeExperimentsFeature.self().isEnabled()) {
                    featureTogglesInventory.getAllTogglesForParent(featureName)
                } else {
                    emptyList()
                }
            experiments.mapNotNull {
                it.enroll()
                if (it.isEnabled()) {
                    it
                } else {
                    null
                }
            }
        }
}
