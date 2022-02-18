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
import com.duckduckgo.remote.messaging.impl.models.IntMatchingAttribute
import com.duckduckgo.remote.messaging.impl.models.MATCHING_ATTR_INT_DEFAULT_VALUE
import com.duckduckgo.remote.messaging.impl.models.MATCHING_ATTR_STRING_DEFAULT_VALUE
import com.duckduckgo.remote.messaging.impl.models.MatchingAttribute
import com.duckduckgo.remote.messaging.impl.models.RangeIntMatchingAttribute
import com.duckduckgo.remote.messaging.impl.models.RangeStringMatchingAttribute
import com.duckduckgo.remote.messaging.impl.models.StringMatchingAttribute
import com.duckduckgo.remote.messaging.impl.models.asJsonFormat
import com.duckduckgo.remote.messaging.impl.models.matches

class DeviceAttributeMatcher(
    val appBuildConfig: AppBuildConfig,
    val appProperties: AppProperties
) {
    fun evaluate(matchingAttribute: MatchingAttribute): Result {
        when (matchingAttribute) {
            is MatchingAttribute.Api -> {
                if (matchingAttribute == MatchingAttribute.Api()) return Result.Fail

                if (matchingAttribute.value != MATCHING_ATTR_INT_DEFAULT_VALUE) {
                    return (matchingAttribute as IntMatchingAttribute).matches(appBuildConfig.sdkInt)
                }
                return (matchingAttribute as RangeIntMatchingAttribute).matches(appBuildConfig.sdkInt)
            }
            is MatchingAttribute.Locale -> {
                if (matchingAttribute == MatchingAttribute.Locale()) return Result.Fail
                return matchingAttribute.matches(appBuildConfig.deviceLocale.asJsonFormat())
            }
            is MatchingAttribute.WebView -> {
                if (matchingAttribute == MatchingAttribute.WebView()) return Result.Fail
                if (matchingAttribute.value != MATCHING_ATTR_STRING_DEFAULT_VALUE) {
                    return (matchingAttribute as StringMatchingAttribute).matches(appProperties.webView())
                }
                return (matchingAttribute as RangeStringMatchingAttribute).matches(appProperties.webView())
            }
            else -> throw IllegalArgumentException("Invalid matcher for $matchingAttribute")
        }
    }
}
