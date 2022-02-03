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
import com.duckduckgo.remote.messaging.impl.models.MATCHING_ATTR_STRING_DEFAULT_VALUE
import com.duckduckgo.remote.messaging.impl.models.MatchingAttribute
import com.duckduckgo.remote.messaging.impl.models.RangeStringMatchingAttribute
import com.duckduckgo.remote.messaging.impl.models.StringMatchingAttribute
import com.duckduckgo.remote.messaging.impl.models.asJsonFormat
import timber.log.Timber

class DeviceAttributeMatcher(
    val deviceProperties: DeviceProperties
) {
    fun evaluate(matchingAttribute: MatchingAttribute): Result {
        when (matchingAttribute) {
            is MatchingAttribute.Api -> {
                if (matchingAttribute == MatchingAttribute.Api()) return Result.Fail
                return matchingAttribute.matches(deviceProperties.osApiLevel())
            }
            is MatchingAttribute.Locale -> {
                return matchingAttribute.matches(deviceProperties.deviceLocale().asJsonFormat())
            }
            is MatchingAttribute.WebView -> {
                if (matchingAttribute == MatchingAttribute.WebView()) return Result.Fail
                if (matchingAttribute.value != MATCHING_ATTR_STRING_DEFAULT_VALUE) {
                    return (matchingAttribute as StringMatchingAttribute).matches(deviceProperties.webView())
                }
                return (matchingAttribute as RangeStringMatchingAttribute).matches(deviceProperties.webView())
            }
            else -> throw IllegalArgumentException("Invalid matcher for $matchingAttribute")
        }
    }
}

fun RangeStringMatchingAttribute.matches(value: String): Result {
    Timber.i("RMF: device WV: $value")
    if (!value.matches(Regex("[0-9]+(\\.[0-9]+)*"))) return false.toResult()

    val webViewVersion = value.split(".").filter { it.isNotEmpty() }.map { it.toInt() }
    val minWebViewVersion = this.min.split(".").filter { it.isNotEmpty() }.map { it.toInt() }
    val maxWebViewVersion = this.max.split(".").filter { it.isNotEmpty() }.map { it.toInt() }

    if (webViewVersion.isEmpty()) return false.toResult()
    if (webViewVersion.compareTo(minWebViewVersion) <= -1) return false.toResult()
    if (webViewVersion.compareTo(maxWebViewVersion) >= 1) return false.toResult()

    return true.toResult()
}

private fun List<Int>.compareTo(other: List<Int>): Int {
    val otherSize = other.size

    for (index in this.indices) {
        if (index > otherSize - 1) return 0
        val value = this[index]
        if (value < other[index]) return -1
        if (value > other[index]) return 1
    }

    return 0
}
