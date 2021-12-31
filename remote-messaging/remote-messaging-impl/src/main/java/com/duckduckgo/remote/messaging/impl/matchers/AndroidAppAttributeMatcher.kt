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

import com.duckduckgo.browser.api.AppProperties
import com.duckduckgo.remote.messaging.impl.models.MatchingAttribute

class AndroidAppAttributeMatcher(
    val appProperties: AppProperties
) {
    fun evaluate(matchingAttribute: MatchingAttribute): Result {
        when (matchingAttribute) {
            is MatchingAttribute.Flavor -> {
                if (matchingAttribute.value.contains(appProperties.flavor())) return true.toResult()
                return false.toResult()
            }
            is MatchingAttribute.AppId -> {
                return (matchingAttribute.value == appProperties.appId()).toResult()
            }
            is MatchingAttribute.AppVersion -> {
                if ((matchingAttribute.min.defaultValue() || appProperties.appVersion() >= matchingAttribute.min) &&
                    (matchingAttribute.max.defaultValue() || appProperties.appVersion() <= matchingAttribute.max)
                ) {
                    return true.toResult()
                }
                return false.toResult()
            }
            is MatchingAttribute.Atb -> {
                return (matchingAttribute.value == appProperties.atb()).toResult()
            }
            is MatchingAttribute.AppAtb -> {
                return (matchingAttribute.value == appProperties.appAtb()).toResult()
            }
            is MatchingAttribute.SearchAtb -> {
                return (matchingAttribute.value == appProperties.searchAtb()).toResult()
            }
            is MatchingAttribute.ExpVariant -> {
                return (matchingAttribute.value == appProperties.expVariant()).toResult()
            }
            is MatchingAttribute.InstalledGPlay -> {
                return (matchingAttribute.value == appProperties.installedGPlay()).toResult()
            }
            else -> throw IllegalArgumentException("Invalid matcher for $matchingAttribute")
        }
    }
}
