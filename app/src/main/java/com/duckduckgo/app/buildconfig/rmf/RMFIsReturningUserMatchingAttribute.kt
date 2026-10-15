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
package com.duckduckgo.app.buildconfig.rmf

import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.remote.messaging.api.AttributeMatcherPlugin
import com.duckduckgo.remote.messaging.api.JsonMatchingAttribute
import com.duckduckgo.remote.messaging.api.JsonToMatchingAttributeMapper
import com.duckduckgo.remote.messaging.api.MatchingAttribute
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class IsReturningUserAttributeMatcherPlugin @Inject constructor(
    private val appBuildConfig: AppBuildConfig,
) : AttributeMatcherPlugin {
    override suspend fun evaluate(matchingAttribute: MatchingAttribute): Boolean? {
        if (matchingAttribute !is IsReturningUserMatchingAttribute) {
            return null
        }
        return appBuildConfig.isAppReinstall() == matchingAttribute.remoteValue
    }
}

@ContributesMultibinding(AppScope::class)
class IsReturningUserMatchingAttributeMapper @Inject constructor() : JsonToMatchingAttributeMapper {
    override fun map(
        key: String,
        jsonMatchingAttribute: JsonMatchingAttribute,
    ): MatchingAttribute? {
        if (key != IsReturningUserMatchingAttribute.KEY) {
            return null
        }
        val value = jsonMatchingAttribute.value ?: return null
        return IsReturningUserMatchingAttribute(remoteValue = value as Boolean)
    }
}

internal data class IsReturningUserMatchingAttribute(val remoteValue: Boolean) : MatchingAttribute {
    companion object {
        const val KEY = "isReturningUser"
    }
}
