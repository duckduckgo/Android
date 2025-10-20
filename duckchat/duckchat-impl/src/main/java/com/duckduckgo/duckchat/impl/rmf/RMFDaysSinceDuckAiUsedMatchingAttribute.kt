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

package com.duckduckgo.duckchat.impl.rmf

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.impl.repository.DuckChatFeatureRepository
import com.duckduckgo.remote.messaging.api.AttributeMatcherPlugin
import com.duckduckgo.remote.messaging.api.JsonMatchingAttribute
import com.duckduckgo.remote.messaging.api.JsonToMatchingAttributeMapper
import com.duckduckgo.remote.messaging.api.MatchingAttribute
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = JsonToMatchingAttributeMapper::class,
)
@ContributesMultibinding(
    scope = AppScope::class,
    boundType = AttributeMatcherPlugin::class,
)
@SingleInstanceIn(AppScope::class)
class RMFDaysSinceDuckAiUsedMatchingAttribute @Inject constructor(
    private val duckChatFeatureRepository: DuckChatFeatureRepository,
) : JsonToMatchingAttributeMapper, AttributeMatcherPlugin {
    override suspend fun evaluate(matchingAttribute: MatchingAttribute): Boolean? {
        return when (matchingAttribute) {
            is DaysSinceDuckAiUsedMatchingAttribute -> {
                return if (matchingAttribute == DaysSinceDuckAiUsedMatchingAttribute()) {
                    false
                } else {
                    val lastDuckAiSessionTimestamp = duckChatFeatureRepository.lastSessionTimestamp()
                    // If lastDuckAiSessionTimestamp is 0, Duck AI was never used, so no matching criteria should match
                    if (lastDuckAiSessionTimestamp == 0L) {
                        return false
                    }
                    
                    val now = System.currentTimeMillis()
                    val daysSinceUsed = TimeUnit.MILLISECONDS.toDays(now - lastDuckAiSessionTimestamp).toInt()
                    if (!matchingAttribute.value.isDefaultValue()) {
                        matchingAttribute.value == daysSinceUsed
                    } else {
                        (matchingAttribute.min.isDefaultValue() || daysSinceUsed >= matchingAttribute.min) &&
                            (matchingAttribute.max.isDefaultValue() || daysSinceUsed <= matchingAttribute.max)
                    }
                }
            }

            else -> null
        }
    }

    private fun Int.isDefaultValue(): Boolean {
        return this == -1
    }

    override fun map(
        key: String,
        jsonMatchingAttribute: JsonMatchingAttribute,
    ): MatchingAttribute? {
        return when (key) {
            DaysSinceDuckAiUsedMatchingAttribute.KEY -> {
                try {
                    DaysSinceDuckAiUsedMatchingAttribute(
                        min = jsonMatchingAttribute.min.toIntOrDefault(-1),
                        max = jsonMatchingAttribute.max.toIntOrDefault(-1),
                        value = jsonMatchingAttribute.value.toIntOrDefault(-1),
                    )
                } catch (e: Exception) {
                    null
                }
            }

            else -> null
        }
    }
}

internal data class DaysSinceDuckAiUsedMatchingAttribute(
    val min: Int = -1,
    val max: Int = -1,
    val value: Int = -1,
) : MatchingAttribute {
    companion object {
        const val KEY = "daysSinceDuckAiUsed"
    }
}

internal fun Any?.toIntOrDefault(default: Int): Int = when {
    this == null -> default
    this is Double -> this.toInt()
    this is Long -> this.toInt()
    else -> this as Int
}
