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

import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.remote.messaging.api.AttributeMatcherPlugin
import com.duckduckgo.remote.messaging.api.JsonMatchingAttribute
import com.duckduckgo.remote.messaging.api.JsonToMatchingAttributeMapper
import com.duckduckgo.remote.messaging.api.MatchingAttribute
import dev.zacsweers.metro.ContributesIntoSet
import javax.inject.Inject

@ContributesIntoSet(AppScope::class)
class ManufacturerAttributeMatcherPlugin @Inject constructor(
    private val appBuildConfig: AppBuildConfig,
) : AttributeMatcherPlugin {
    override suspend fun evaluate(matchingAttribute: MatchingAttribute): Boolean? {
        if (matchingAttribute !is ManufacturerMatchingAttribute) {
            return null
        }
        val manufacturer = appBuildConfig.manufacturer
        return matchingAttribute.value.any { it.equals(manufacturer, ignoreCase = true) }
    }
}

@ContributesIntoSet(AppScope::class)
class ManufacturerMatchingAttributeMapper @Inject constructor() : JsonToMatchingAttributeMapper {
    override fun map(
        key: String,
        jsonMatchingAttribute: JsonMatchingAttribute,
    ): MatchingAttribute? {
        if (key != ManufacturerMatchingAttribute.KEY) {
            return null
        }
        val value = (jsonMatchingAttribute.value as? List<*>)?.filterIsInstance<String>().orEmpty()
        return ManufacturerMatchingAttribute(value = value)
    }
}

internal data class ManufacturerMatchingAttribute(val value: List<String>) : MatchingAttribute {
    companion object {
        const val KEY = "manufacturer"
    }
}
