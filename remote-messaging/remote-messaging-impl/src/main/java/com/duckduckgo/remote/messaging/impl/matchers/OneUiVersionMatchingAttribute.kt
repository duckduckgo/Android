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

package com.duckduckgo.remote.messaging.impl.matchers

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.remote.messaging.api.AttributeMatcherPlugin
import com.duckduckgo.remote.messaging.api.JsonMatchingAttribute
import com.duckduckgo.remote.messaging.api.JsonToMatchingAttributeMapper
import com.duckduckgo.remote.messaging.api.MatchingAttribute
import com.duckduckgo.remote.messaging.impl.models.MATCHING_ATTR_STRING_DEFAULT_VALUE
import dev.zacsweers.metro.ContributesIntoSet
import javax.inject.Inject

@ContributesIntoSet(AppScope::class)
class OneUiVersionAttributeMatcherPlugin @Inject constructor(
    private val oneUiVersionProvider: OneUiVersionProvider,
) : AttributeMatcherPlugin {
    override suspend fun evaluate(matchingAttribute: MatchingAttribute): Boolean? {
        if (matchingAttribute !is OneUiVersionMatchingAttribute) {
            return null
        }
        if (matchingAttribute.isMalformed || matchingAttribute == OneUiVersionMatchingAttribute()) return false
        val device = oneUiVersionProvider.get() ?: return false
        val deviceParts = listOf(device.major, device.minor)

        if (matchingAttribute.value.isNotEmpty()) {
            val value = matchingAttribute.value.toVersionParts() ?: return false
            return deviceParts.compareToConfig(value) == 0
        }

        if (matchingAttribute.min.isNotEmpty()) {
            val min = matchingAttribute.min.toVersionParts() ?: return false
            if (deviceParts.compareToConfig(min) < 0) return false
        }
        if (matchingAttribute.max.isNotEmpty()) {
            val max = matchingAttribute.max.toVersionParts() ?: return false
            if (deviceParts.compareToConfig(max) > 0) return false
        }
        return true
    }

    private fun String.toVersionParts(): List<Int>? = split(".").map { it.toIntOrNull() ?: return null }

    private fun List<Int>.compareToConfig(config: List<Int>): Int {
        config.forEachIndexed { index, configPart ->
            val devicePart = getOrElse(index) { 0 }
            if (devicePart != configPart) return devicePart.compareTo(configPart)
        }
        return 0
    }
}

@ContributesIntoSet(AppScope::class)
class OneUiVersionMatchingAttributeMapper @Inject constructor() : JsonToMatchingAttributeMapper {
    override fun map(
        key: String,
        jsonMatchingAttribute: JsonMatchingAttribute,
    ): MatchingAttribute? {
        if (key != OneUiVersionMatchingAttribute.KEY) {
            return null
        }
        val min = jsonMatchingAttribute.min.toVersionString()
        val max = jsonMatchingAttribute.max.toVersionString()
        val value = jsonMatchingAttribute.value.toVersionString()
        return OneUiVersionMatchingAttribute(
            min = min ?: MATCHING_ATTR_STRING_DEFAULT_VALUE,
            max = max ?: MATCHING_ATTR_STRING_DEFAULT_VALUE,
            value = value ?: MATCHING_ATTR_STRING_DEFAULT_VALUE,
            isMalformed = min == null || max == null || value == null,
        )
    }

    private fun Any?.toVersionString(): String? = when {
        this == null -> MATCHING_ATTR_STRING_DEFAULT_VALUE
        this is String -> this
        this is Number && toDouble() % 1.0 == 0.0 -> toLong().toString()
        else -> null
    }
}

internal data class OneUiVersionMatchingAttribute(
    val min: String = MATCHING_ATTR_STRING_DEFAULT_VALUE,
    val max: String = MATCHING_ATTR_STRING_DEFAULT_VALUE,
    val value: String = MATCHING_ATTR_STRING_DEFAULT_VALUE,
    val isMalformed: Boolean = false,
) : MatchingAttribute {
    companion object {
        const val KEY = "oneUiVersion"
    }
}
