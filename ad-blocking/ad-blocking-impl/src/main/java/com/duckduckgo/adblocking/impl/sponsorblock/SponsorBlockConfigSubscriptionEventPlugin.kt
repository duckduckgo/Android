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

package com.duckduckgo.adblocking.impl.sponsorblock

import com.duckduckgo.contentscopescripts.api.ContentScopeScriptsSubscriptionEventPlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.js.messaging.api.SubscriptionEventData
import dev.zacsweers.metro.ContributesIntoSet
import javax.inject.Inject

// Sent on every resume rather than when the config changes, so a change made while the page is on screen applies on the next resume or
// page load.
@ContributesIntoSet(AppScope::class)
class SponsorBlockConfigSubscriptionEventPlugin @Inject constructor(
    private val sponsorBlock: SponsorBlock,
) : ContentScopeScriptsSubscriptionEventPlugin {

    override fun getSubscriptionEventData(): SubscriptionEventData = SubscriptionEventData(
        featureName = SponsorBlockContentScopeJsMessageHandler.FEATURE_NAME,
        subscriptionName = SUBSCRIPTION_CONFIG_CHANGED,
        params = sponsorBlock.config().toJson(),
    )

    private companion object {
        const val SUBSCRIPTION_CONFIG_CHANGED = "onConfigChanged"
    }
}
