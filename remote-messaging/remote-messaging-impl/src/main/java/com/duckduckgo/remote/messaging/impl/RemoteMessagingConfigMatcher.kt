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

import com.duckduckgo.remote.messaging.impl.matchers.*
import com.duckduckgo.remote.messaging.impl.models.MatchingAttribute
import com.duckduckgo.remote.messaging.impl.models.RemoteConfig
import com.duckduckgo.remote.messaging.impl.models.RemoteMessage
import timber.log.Timber

class RemoteMessagingConfigMatcher(
    val deviceAttributeMatcher: DeviceAttributeMatcher,
    val androidAppAttributeMatcher: AndroidAppAttributeMatcher,
    val userAttributeMatcher: UserAttributeMatcher
) {
    suspend fun evaluate(remoteConfig: RemoteConfig): RemoteMessage? {
        val rules = remoteConfig.rules

        remoteConfig.messages.forEach { message ->
            val matchingRules = if (message.matchingRules.isEmpty() && message.exclusionRules.isEmpty()) return message else message.matchingRules

            val matchingResult = matchingRules.evaluateMatchingRules(rules)
            val excludeResult = message.exclusionRules.evaluateExclusionRules(rules)

            if (matchingResult == Result.Match && excludeResult == Result.Fail) return message
        }

        return null
    }

    private suspend fun Iterable<Int>.evaluateMatchingRules(rules: Map<Int, List<MatchingAttribute>>): Result {
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

            if (result == Result.NextMessage || result == Result.Match) return result
        }

        return result
    }

    private suspend fun Iterable<Int>.evaluateExclusionRules(rules: Map<Int, List<MatchingAttribute>>): Result {
        var result: Result = Result.Fail

        for (rule in this) {
            val attributes = rules[rule].takeUnless { it.isNullOrEmpty() } ?: return Result.Fail
            result = Result.Fail

            for (attr in attributes) {
                result = evaluateAttribute(attr)
                if (result == Result.Fail || result == Result.NextMessage) {
                    Timber.i("RMF: first failed attribute $attr")
                    break
                }
            }

            if (result == Result.NextMessage || result == Result.Match) return result
        }

        return result
    }

    private suspend fun evaluateAttribute(matchingAttribute: MatchingAttribute): Result {
        when (matchingAttribute) {
            is MatchingAttribute.Api -> return deviceAttributeMatcher.evaluate(matchingAttribute)
            is MatchingAttribute.AppAtb -> return androidAppAttributeMatcher.evaluate(matchingAttribute)
            is MatchingAttribute.AppId -> return androidAppAttributeMatcher.evaluate(matchingAttribute)
            is MatchingAttribute.AppTheme -> return userAttributeMatcher.evaluate(matchingAttribute)
            is MatchingAttribute.AppVersion -> return androidAppAttributeMatcher.evaluate(matchingAttribute)
            is MatchingAttribute.Atb -> return androidAppAttributeMatcher.evaluate(matchingAttribute)
            is MatchingAttribute.Bookmarks -> return userAttributeMatcher.evaluate(matchingAttribute)
            is MatchingAttribute.DaysSinceInstalled -> return userAttributeMatcher.evaluate(matchingAttribute)
            is MatchingAttribute.DaysUsedSince -> return userAttributeMatcher.evaluate(matchingAttribute)
            is MatchingAttribute.DefaultBrowser -> return userAttributeMatcher.evaluate(matchingAttribute)
            is MatchingAttribute.EmailEnabled -> return userAttributeMatcher.evaluate(matchingAttribute)
            is MatchingAttribute.ExpVariant -> return androidAppAttributeMatcher.evaluate(matchingAttribute)
            is MatchingAttribute.Favorites -> return userAttributeMatcher.evaluate(matchingAttribute)
            is MatchingAttribute.Flavor -> return androidAppAttributeMatcher.evaluate(matchingAttribute)
            is MatchingAttribute.InstalledGPlay -> return androidAppAttributeMatcher.evaluate(matchingAttribute)
            is MatchingAttribute.Locale -> return deviceAttributeMatcher.evaluate(matchingAttribute)
            is MatchingAttribute.SearchAtb -> return androidAppAttributeMatcher.evaluate(matchingAttribute)
            is MatchingAttribute.SearchCount -> return userAttributeMatcher.evaluate(matchingAttribute)
            is MatchingAttribute.WebView -> return deviceAttributeMatcher.evaluate(matchingAttribute)
            is MatchingAttribute.WidgetAdded -> return userAttributeMatcher.evaluate(matchingAttribute)
            is MatchingAttribute.Unknown -> {
                return matchingAttribute.fallback.toResult()
            }
        }
    }
}