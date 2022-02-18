/*
 * Copyright (c) 2021 DuckDuckGo
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
import com.duckduckgo.browser.api.AppProperties
import com.duckduckgo.remote.messaging.impl.models.MATCHING_ATTR_STRING_DEFAULT_VALUE
import com.duckduckgo.remote.messaging.impl.models.MatchingAttribute
import com.duckduckgo.remote.messaging.impl.models.RangeStringMatchingAttribute
import com.duckduckgo.remote.messaging.impl.models.StringMatchingAttribute
import com.duckduckgo.remote.messaging.impl.models.matches

class AndroidAppAttributeMatcher(
    val appProperties: AppProperties,
    val appBuildConfig: AppBuildConfig
) {
    fun evaluate(matchingAttribute: MatchingAttribute): Result {
        when (matchingAttribute) {
            is MatchingAttribute.Flavor -> {
                if (matchingAttribute == MatchingAttribute.Flavor()) return Result.Fail
                return matchingAttribute.matches(appBuildConfig.flavor.toString())
            }
            is MatchingAttribute.AppId -> {
                return matchingAttribute.matches(appBuildConfig.applicationId)
            }
            is MatchingAttribute.AppVersion -> {
                if (matchingAttribute == MatchingAttribute.AppVersion()) return Result.Fail
                if (matchingAttribute.value != MATCHING_ATTR_STRING_DEFAULT_VALUE) {
                    return (matchingAttribute as StringMatchingAttribute).matches(appBuildConfig.versionName)
                }
                return (matchingAttribute as RangeStringMatchingAttribute).matches(appBuildConfig.versionName)
            }
            is MatchingAttribute.Atb -> {
                return matchingAttribute.matches(appProperties.atb())
            }
            is MatchingAttribute.AppAtb -> {
                return matchingAttribute.matches(appProperties.appAtb())
            }
            is MatchingAttribute.SearchAtb -> {
                return matchingAttribute.matches(appProperties.searchAtb())
            }
            is MatchingAttribute.ExpVariant -> {
                return matchingAttribute.matches(appProperties.expVariant())
            }
            is MatchingAttribute.InstalledGPlay -> {
                return matchingAttribute.matches(appProperties.installedGPlay())
            }
            else -> throw IllegalArgumentException("Invalid matcher for $matchingAttribute")
        }
    }
}
