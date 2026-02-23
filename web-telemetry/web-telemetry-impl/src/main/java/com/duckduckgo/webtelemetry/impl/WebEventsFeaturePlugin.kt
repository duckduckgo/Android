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

package com.duckduckgo.webtelemetry.impl

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.config.api.PrivacyFeaturePlugin
import com.duckduckgo.webtelemetry.store.WebEventsFeatureConfigEntity
import com.duckduckgo.webtelemetry.store.EventHubConfigEntity
import com.duckduckgo.webtelemetry.store.WebEventsRepository
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class EventHubFeaturePlugin @Inject constructor(
    private val repository: WebEventsRepository,
    private val pixelManager: EventHubPixelManager,
) : PrivacyFeaturePlugin {

    override fun store(featureName: String, jsonString: String): Boolean {
        if (featureName == this.featureName) {
            repository.updateConfig(EventHubConfigEntity(json = jsonString))
            pixelManager.onConfigChanged()
            return true
        }
        return false
    }

    override val featureName: String = WebEventsFeatureName.EventHub.value
}

@ContributesMultibinding(AppScope::class)
class WebEventsFeaturePlugin @Inject constructor(
    private val repository: WebEventsRepository,
) : PrivacyFeaturePlugin {

    override fun store(featureName: String, jsonString: String): Boolean {
        if (featureName == this.featureName) {
            repository.updateWebEventsConfig(WebEventsFeatureConfigEntity(json = jsonString))
            return true
        }
        return false
    }

    override val featureName: String = WebEventsFeatureName.WebEvents.value
}
