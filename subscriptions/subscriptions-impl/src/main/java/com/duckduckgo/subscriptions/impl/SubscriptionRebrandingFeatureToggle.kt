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

package com.duckduckgo.subscriptions.impl

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.config.api.PrivacyConfigCallbackPlugin
import com.duckduckgo.subscriptions.api.SubscriptionRebrandingFeatureToggle
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.logcat

@ContributesBinding(
    scope = AppScope::class,
    boundType = SubscriptionRebrandingFeatureToggle::class,
)
@ContributesMultibinding(
    scope = AppScope::class,
    boundType = PrivacyConfigCallbackPlugin::class,
)
@ContributesMultibinding(
    scope = AppScope::class,
    boundType = MainProcessLifecycleObserver::class,
)
@SingleInstanceIn(AppScope::class)
class SubscriptionRebrandingFeatureToggleImpl @Inject constructor(
    private val privacyProFeature: PrivacyProFeature,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) : SubscriptionRebrandingFeatureToggle, PrivacyConfigCallbackPlugin, MainProcessLifecycleObserver {

    private var cachedValue: Boolean = false

    override fun isSubscriptionRebrandingEnabled(): Boolean {
        return cachedValue
    }

    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)
        logcat { "SubscriptionRebrandingFeatureToggle: App created, prefetching feature flag" }
        prefetchFeatureFlag()
    }

    override fun onPrivacyConfigDownloaded() {
        logcat { "SubscriptionRebrandingFeatureToggle: Privacy config downloaded, refreshing feature flag" }
        prefetchFeatureFlag()
    }

    private fun prefetchFeatureFlag() {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            val isEnabled = privacyProFeature.subscriptionRebranding().isEnabled()
            cachedValue = isEnabled
            logcat { "SubscriptionRebrandingFeatureToggle: Feature flag cached, value = $isEnabled" }
        }
    }
}
