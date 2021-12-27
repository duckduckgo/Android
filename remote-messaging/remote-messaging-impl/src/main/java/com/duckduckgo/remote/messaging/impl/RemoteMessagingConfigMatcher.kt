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

import com.duckduckgo.browser.api.AppProperties
import com.duckduckgo.browser.api.DeviceProperties
import com.duckduckgo.remote.messaging.impl.models.MatchingAttribute
import com.duckduckgo.remote.messaging.impl.models.RemoteConfig
import com.duckduckgo.remote.messaging.impl.models.RemoteMessage
import timber.log.Timber

class RemoteMessagingConfigMatcher(
    val deviceProperties: DeviceProperties,
    val appProperties: AppProperties
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
            is MatchingAttribute.Api -> {
                if ((matchingAttribute.min.defaultValue() || deviceProperties.osApiLevel() >= matchingAttribute.min) &&
                    (matchingAttribute.max.defaultValue() || deviceProperties.osApiLevel() <= matchingAttribute.max)
                ) {
                    return true.toResult()
                }

                return false.toResult()
            }
            is MatchingAttribute.AppAtb -> TODO()
            is MatchingAttribute.AppId -> TODO()
            is MatchingAttribute.AppTheme -> TODO()
            is MatchingAttribute.AppVersion -> TODO()
            is MatchingAttribute.Atb -> TODO()
            is MatchingAttribute.Bookmarks -> TODO()
            is MatchingAttribute.DaysSinceInstalled -> TODO()
            is MatchingAttribute.DaysUsedSince -> TODO()
            is MatchingAttribute.DefaultBrowser -> TODO()
            is MatchingAttribute.EmailEnabled -> TODO()
            is MatchingAttribute.ExpVariant -> TODO()
            is MatchingAttribute.Favorites -> TODO()
            is MatchingAttribute.Flavor -> TODO()
            is MatchingAttribute.InstalledGPlay -> TODO()
            is MatchingAttribute.Locale -> {
                val locales = matchingAttribute.value
                if (locales.contains(deviceProperties.deviceLocale().toString())) return true.toResult()
                return false.toResult()
            }
            is MatchingAttribute.SearchAtb -> TODO()
            is MatchingAttribute.SearchCount -> TODO()
            is MatchingAttribute.Unknown -> {
                return matchingAttribute.fallback.toResult()
            }
            is MatchingAttribute.WebView -> {
                val deviceWebView = deviceProperties.webView()
                Timber.i("RMF: device WV: $deviceWebView")

                if ((matchingAttribute.min.defaultValue() || deviceWebView >= matchingAttribute.min) &&
                    (matchingAttribute.max.defaultValue() || deviceWebView <= matchingAttribute.max)
                ) {
                    return true.toResult()
                }

                return false.toResult()
            }
            is MatchingAttribute.WidgetAdded -> TODO()
        }
    }

    private fun Boolean?.toResult(): Result {
        return when (this) {
            true -> Result.Match
            false -> Result.Fail
            null -> Result.NextMessage
        }
    }
}

sealed class Result {
    object Match : Result()
    object Fail : Result()
    object NextMessage : Result()
}

private fun String.defaultValue() = this.isEmpty()
private fun Int.defaultValue() = this == -1
