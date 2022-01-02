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

import com.duckduckgo.browser.api.DeviceProperties
import com.duckduckgo.remote.messaging.impl.models.MatchingAttribute
import timber.log.Timber

class DeviceAttributeMatcher(
    val deviceProperties: DeviceProperties
) {
    fun evaluate(matchingAttribute: MatchingAttribute): Result {
        when (matchingAttribute) {
            is MatchingAttribute.Api -> {
                if (matchingAttribute == MatchingAttribute.Api()) return Result.Fail

                if ((matchingAttribute.min.defaultValue() || deviceProperties.osApiLevel() >= matchingAttribute.min) &&
                    (matchingAttribute.max.defaultValue() || deviceProperties.osApiLevel() <= matchingAttribute.max)
                ) {
                    return true.toResult()
                }

                return false.toResult()
            }
            is MatchingAttribute.Locale -> {
                val locales = matchingAttribute.value
                if (locales.contains(deviceProperties.deviceLocale().toString())) return true.toResult()
                return false.toResult()
            }
            is MatchingAttribute.WebView -> {
                if (matchingAttribute == MatchingAttribute.WebView()) return Result.Fail

                val deviceWebView = deviceProperties.webView()
                Timber.i("RMF: device WV: $deviceWebView")
                if (!deviceWebView.matches(Regex("[0-9]+(\\.[0-9]+)*"))) return false.toResult()

                val webViewVersion = deviceWebView.split(".").filter { it.isNotEmpty() }
                val minWebViewVersion = matchingAttribute.min.split(".").filter { it.isNotEmpty() }
                val maxWebViewVersion = matchingAttribute.max.split(".").filter { it.isNotEmpty() }

                if (webViewVersion.isEmpty()) return false.toResult()
                if (webViewVersion.compareTo(minWebViewVersion) <= -1) return false.toResult()
                if (webViewVersion.compareTo(maxWebViewVersion) >= 1) return false.toResult()

                return true.toResult()
            }
            else -> throw IllegalArgumentException("Invalid matcher for $matchingAttribute")
        }
    }

    private fun List<String>.compareTo(other: List<String>): Int {
        val otherSize = other.size

        for (index in this.indices) {
            if (index > otherSize-1 ) return 0
            val value = this[index]
            if (value.isEmpty()) return 0
            if (value < other[index]) return -1
            if (value > other[index]) return 1
        }

        return 0
    }
}