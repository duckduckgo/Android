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

package com.duckduckgo.eventhub.impl

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.eventhub.store.EventHubConfigEntity
import com.duckduckgo.eventhub.store.EventHubRepository
import com.duckduckgo.eventhub.store.WebEventsFeatureConfigEntity
import com.duckduckgo.privacy.config.api.PrivacyFeaturePlugin
import com.squareup.anvil.annotations.ContributesMultibinding
import logcat.LogPriority.DEBUG
import logcat.logcat
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class EventHubFeaturePlugin @Inject constructor(
    private val repository: EventHubRepository,
    private val pixelManager: RealEventHubPixelManager,
) : PrivacyFeaturePlugin {

    override fun store(featureName: String, jsonString: String): Boolean {
        if (featureName == this.featureName) {
            logcat(DEBUG) { "EventHub: storing eventHub config (${jsonString.length} chars)" }
            repository.updateEventHubConfig(EventHubConfigEntity(json = jsonString))
            pixelManager.onConfigChanged()
            return true
        }
        return false
    }

    override val featureName: String = EventHubFeatureName.EventHub.value
}

@ContributesMultibinding(AppScope::class)
class WebEventsFeaturePlugin @Inject constructor(
    private val repository: EventHubRepository,
) : PrivacyFeaturePlugin {

    override fun store(featureName: String, jsonString: String): Boolean {
        if (featureName == this.featureName) {
            repository.updateWebEventsFeatureConfig(WebEventsFeatureConfigEntity(json = jsonString))
            return true
        }
        return false
    }

    override val featureName: String = EventHubFeatureName.WebEvents.value
}
