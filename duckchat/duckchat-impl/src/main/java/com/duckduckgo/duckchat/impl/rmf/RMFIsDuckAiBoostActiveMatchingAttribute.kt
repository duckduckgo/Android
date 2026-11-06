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
import com.duckduckgo.duckchat.impl.promo.data.PromoCampaignRepository
import com.duckduckgo.remote.messaging.api.AttributeMatcherPlugin
import com.duckduckgo.remote.messaging.api.JsonMatchingAttribute
import com.duckduckgo.remote.messaging.api.JsonToMatchingAttributeMapper
import com.duckduckgo.remote.messaging.api.MatchingAttribute
import dev.zacsweers.metro.ContributesIntoSet
import javax.inject.Inject

@ContributesIntoSet(AppScope::class)
class IsDuckAiBoostActiveAttributeMatcherPlugin @Inject constructor(
    private val duckChatFeature: DuckChatFeature,
    private val promoCampaignRepository: PromoCampaignRepository,
) : AttributeMatcherPlugin {
    override suspend fun evaluate(matchingAttribute: MatchingAttribute): Boolean? {
        if (matchingAttribute !is IsDuckAiBoostActiveMatchingAttribute) {
            return null
        }
        val isBoostActive = duckChatFeature.duckAiPromoCampaigns().isEnabled() && promoCampaignRepository.isPromoActive()
        return isBoostActive == matchingAttribute.remoteValue
    }
}

@ContributesIntoSet(AppScope::class)
class IsDuckAiBoostActiveMatchingAttributeMapper @Inject constructor() : JsonToMatchingAttributeMapper {
    override fun map(
        key: String,
        jsonMatchingAttribute: JsonMatchingAttribute,
    ): MatchingAttribute? {
        if (key != IsDuckAiBoostActiveMatchingAttribute.KEY) {
            return null
        }
        val value = jsonMatchingAttribute.value ?: return null
        return IsDuckAiBoostActiveMatchingAttribute(remoteValue = value as Boolean)
    }
}

internal data class IsDuckAiBoostActiveMatchingAttribute(val remoteValue: Boolean) : MatchingAttribute {
    companion object {
        const val KEY = "isDuckAiBoostActive"
    }
}
