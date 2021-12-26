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

import com.duckduckgo.browser.api.DeviceProperties
import com.duckduckgo.remote.messaging.impl.models.MatchingAttribute
import com.duckduckgo.remote.messaging.impl.models.RemoteConfig
import com.duckduckgo.remote.messaging.impl.models.RemoteMessage
import timber.log.Timber

class RemoteMessagingConfigMatcher(
    val deviceProperties: DeviceProperties
) {
    fun evaluate(remoteConfig: RemoteConfig): RemoteMessage? {
        val rules = remoteConfig.rules

        remoteConfig.messages.forEach { message ->
            val matchingRules = message.matchingRules

            if (matchingRules.isEmpty()) return message

            val matchRule = matchingRules.find { rule ->
                val failedAttribute = rules[rule]?.find { !evaluateAttribute(it) }
                Timber.i("RMF: first failed attribute $failedAttribute")
                failedAttribute == null
            }

            Timber.i("RMF: matchedRule $matchRule")
            if (matchRule != null) return message
        }

        return null
    }

    private fun evaluateAttribute(matchingAttribute: MatchingAttribute): Boolean {
        when (matchingAttribute) {
            is MatchingAttribute.Api -> {
                if ((matchingAttribute.min.defaultValue() || deviceProperties.osApiLevel() >= matchingAttribute.min) &&
                    (matchingAttribute.max.defaultValue() || deviceProperties.osApiLevel() <= matchingAttribute.max)
                ) {
                    return true
                }

                return false
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
                if (locales.contains(deviceProperties.deviceLocale().toString())) return true
                return false
            }
            is MatchingAttribute.SearchAtb -> TODO()
            is MatchingAttribute.SearchCount -> TODO()
            is MatchingAttribute.Unknown -> TODO()
            is MatchingAttribute.WebView -> {
                val deviceWebView = deviceProperties.webView()
                Timber.i("RMF: device WV: $deviceWebView")

                if ((matchingAttribute.min.defaultValue() || deviceWebView >= matchingAttribute.min) &&
                    (matchingAttribute.max.defaultValue() || deviceWebView <= matchingAttribute.max)
                ) {
                    return true
                }

                return false
            }
            is MatchingAttribute.WidgetAdded -> TODO()
        }
    }
}

private fun String.defaultValue() = this.isEmpty()
private fun Int.defaultValue() = this == -1
