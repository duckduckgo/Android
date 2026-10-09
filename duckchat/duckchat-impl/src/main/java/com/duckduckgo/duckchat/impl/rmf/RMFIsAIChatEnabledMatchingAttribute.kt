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

package com.duckduckgo.duckchat.impl.rmf

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.duckduckgo.duckchat.impl.repository.DuckChatFeatureRepository
import com.duckduckgo.remote.messaging.api.AttributeMatcherPlugin
import com.duckduckgo.remote.messaging.api.JsonMatchingAttribute
import com.duckduckgo.remote.messaging.api.JsonToMatchingAttributeMapper
import com.duckduckgo.remote.messaging.api.MatchingAttribute
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

/**
 * Evaluates the attribute directly by querying the sources directly rather than through [com.duckduckgo.duckchat.api.DuckChat.isEnabled].
 * This prevents a race condition in which DuckChat's cache has not been updated before RMF evaluation takes place
 * upon a cold start-up.
 */
@ContributesMultibinding(AppScope::class)
class IsAIChatEnabledAttributeMatcherPlugin @Inject constructor(
    private val duckChatFeature: DuckChatFeature,
    private val duckChatFeatureRepository: DuckChatFeatureRepository,
) : AttributeMatcherPlugin {
    override suspend fun evaluate(matchingAttribute: MatchingAttribute): Boolean? {
        if (matchingAttribute !is IsAIChatEnabledMatchingAttribute) {
            return null
        }
        val isEnabled = duckChatFeature.self().isEnabled() && duckChatFeatureRepository.isDuckChatUserEnabled()
        return isEnabled == matchingAttribute.remoteValue
    }
}

@ContributesMultibinding(AppScope::class)
class IsAIChatEnabledMatchingAttributeMapper @Inject constructor() : JsonToMatchingAttributeMapper {
    override fun map(
        key: String,
        jsonMatchingAttribute: JsonMatchingAttribute,
    ): MatchingAttribute? {
        if (key != IsAIChatEnabledMatchingAttribute.KEY) {
            return null
        }
        val value = jsonMatchingAttribute.value ?: return null
        return IsAIChatEnabledMatchingAttribute(remoteValue = value as Boolean)
    }
}

internal data class IsAIChatEnabledMatchingAttribute(val remoteValue: Boolean) : MatchingAttribute {
    companion object {
        const val KEY = "isAIChatEnabled"
    }
}
