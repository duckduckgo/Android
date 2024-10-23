/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.browser.omnibar.rmf

import com.duckduckgo.app.browser.omnibar.ChangeOmnibarPositionFeature
import com.duckduckgo.app.browser.omnibar.model.OmnibarPosition
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.remote.messaging.api.AttributeMatcherPlugin
import com.duckduckgo.remote.messaging.api.JsonMatchingAttribute
import com.duckduckgo.remote.messaging.api.JsonToMatchingAttributeMapper
import com.duckduckgo.remote.messaging.api.MatchingAttribute
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

data class OmnibarPositionFeatureEnabledMatchingAttribute(
    val remoteValue: Boolean,
) : MatchingAttribute {
    companion object {
        const val KEY = "omnibarPositionFeatureEnabled"
    }
}

data class OmnibarPositionMatchingAttribute(
    val remoteValue: OmnibarPosition,
) : MatchingAttribute {
    companion object {
        const val KEY = "omnibarPosition"
    }
}

@ContributesMultibinding(AppScope::class)
class OmnibarPositionAttributeMatcherPlugin @Inject constructor(
    private val omnibarPositionFeature: ChangeOmnibarPositionFeature,
    private val settingsDataStore: SettingsDataStore,
) : AttributeMatcherPlugin {
    override suspend fun evaluate(matchingAttribute: MatchingAttribute): Boolean? {
        return when (matchingAttribute) {
            is OmnibarPositionFeatureEnabledMatchingAttribute -> {
                matchingAttribute.remoteValue == omnibarPositionFeature.self().isEnabled()
            }

            is OmnibarPositionMatchingAttribute -> {
                matchingAttribute.remoteValue == settingsDataStore.omnibarPosition
            }

            else -> return null
        }
    }
}

@ContributesMultibinding(AppScope::class)
class OmnibarPositionJsonMatchingAttributeMapper @Inject constructor() : JsonToMatchingAttributeMapper {
    override fun map(
        key: String,
        jsonMatchingAttribute: JsonMatchingAttribute,
    ): MatchingAttribute? {
        return when (key) {
            OmnibarPositionFeatureEnabledMatchingAttribute.KEY -> {
                OmnibarPositionFeatureEnabledMatchingAttribute(
                    jsonMatchingAttribute.value as Boolean,
                )
            }
            OmnibarPositionMatchingAttribute.KEY -> {
                OmnibarPositionMatchingAttribute(
                    OmnibarPosition.valueOf((jsonMatchingAttribute.value as String).uppercase()),
                )
            }
            else -> null
        }
    }
}
