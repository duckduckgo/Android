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

package com.duckduckgo.remote.messaging.impl

import com.duckduckgo.remote.messaging.impl.matchers.AndroidAppAttributeMatcher
import com.duckduckgo.remote.messaging.impl.matchers.DeviceAttributeMatcher
import com.duckduckgo.remote.messaging.impl.matchers.Result
import com.duckduckgo.remote.messaging.impl.matchers.toResult
import com.duckduckgo.remote.messaging.impl.models.MatchingAttribute
import com.duckduckgo.remote.messaging.impl.models.RemoteConfig
import com.duckduckgo.remote.messaging.impl.models.RemoteMessage
import timber.log.Timber

class RemoteMessagingConfigMatcher(
    val deviceAttributeMatcher: DeviceAttributeMatcher,
    val androidAppAttributeMatcher: AndroidAppAttributeMatcher
) {
    fun evaluate(remoteConfig: RemoteConfig): RemoteMessage? {
        val rules = remoteConfig.rules

        remoteConfig.messages.forEach { message ->
            val matchingRules = if (message.matchingRules.isEmpty()) return message else message.matchingRules

            val result = matchingRules.evaluateMatchingRules(rules)

            if (result == Result.Match) return message
        }

        return null
    }

    private fun Iterable<Int>.evaluateMatchingRules(rules: Map<Int, List<MatchingAttribute>>): Result {
        var result: Result = Result.Match

        for (rule in this) {
            val attributes = rules[rule].takeUnless { it.isNullOrEmpty() } ?: return Result.Match
            result = Result.Match

            for (attr in attributes) {
                result = evaluateAttribute(attr)
                if (result == Result.Fail || result == Result.NextMessage) {
                    Timber.i("RMF: first failed attribute $attr")
                    break
                }
            }

            if (result == Result.NextMessage) return result
        }

        return result
    }

    private fun evaluateAttribute(matchingAttribute: MatchingAttribute): Result {
        when (matchingAttribute) {
            is MatchingAttribute.Api -> return deviceAttributeMatcher.evaluate(matchingAttribute)
            is MatchingAttribute.AppAtb -> return androidAppAttributeMatcher.evaluate(matchingAttribute)
            is MatchingAttribute.AppId -> return androidAppAttributeMatcher.evaluate(matchingAttribute)
            is MatchingAttribute.AppTheme -> TODO()
            is MatchingAttribute.AppVersion -> return androidAppAttributeMatcher.evaluate(matchingAttribute)
            is MatchingAttribute.Atb -> return androidAppAttributeMatcher.evaluate(matchingAttribute)
            is MatchingAttribute.Bookmarks -> TODO()
            is MatchingAttribute.DaysSinceInstalled -> TODO()
            is MatchingAttribute.DaysUsedSince -> TODO()
            is MatchingAttribute.DefaultBrowser -> TODO()
            is MatchingAttribute.EmailEnabled -> TODO()
            is MatchingAttribute.ExpVariant -> return androidAppAttributeMatcher.evaluate(matchingAttribute)
            is MatchingAttribute.Favorites -> TODO()
            is MatchingAttribute.Flavor -> return androidAppAttributeMatcher.evaluate(matchingAttribute)
            is MatchingAttribute.InstalledGPlay -> return androidAppAttributeMatcher.evaluate(matchingAttribute)
            is MatchingAttribute.Locale -> return deviceAttributeMatcher.evaluate(matchingAttribute)
            is MatchingAttribute.SearchAtb -> return androidAppAttributeMatcher.evaluate(matchingAttribute)
            is MatchingAttribute.SearchCount -> TODO()
            is MatchingAttribute.WebView -> return deviceAttributeMatcher.evaluate(matchingAttribute)
            is MatchingAttribute.WidgetAdded -> TODO()
            is MatchingAttribute.Unknown -> {
                return matchingAttribute.fallback.toResult()
            }
        }
    }
}