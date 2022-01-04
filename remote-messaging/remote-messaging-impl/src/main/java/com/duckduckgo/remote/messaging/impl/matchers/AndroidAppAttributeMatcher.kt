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
import timber.log.Timber
import java.util.*

class AndroidAppAttributeMatcher(
    val appProperties: AppProperties
) {
    fun evaluate(matchingAttribute: MatchingAttribute): Result {
        when (matchingAttribute) {
            is MatchingAttribute.Flavor -> {
                matchingAttribute.value.find { it.equals(appProperties.flavor(), true) } ?: return false.toResult()
                return true.toResult()
            }
            is MatchingAttribute.AppId -> {
                return (matchingAttribute.value == appProperties.appId()).toResult()
            }
            is MatchingAttribute.AppVersion -> {
                if (matchingAttribute == MatchingAttribute.AppVersion()) return Result.Fail

                val appVersion = appProperties.appVersion()
                Timber.i("RMF: device WV: $appVersion")
                if (!appVersion.matches(Regex("[0-9]+(\\.[0-9]+)*"))) return false.toResult()


                val appVersionParts = appVersion.split(".").filter { it.isNotEmpty() }.map { it.toInt() }
                val minAppVersionParts = matchingAttribute.min.split(".").filter { it.isNotEmpty() }.map { it.toInt() }
                val maxAppVersionParts = matchingAttribute.max.split(".").filter { it.isNotEmpty() }.map { it.toInt() }

                if (appVersionParts.isEmpty()) return false.toResult()
                if (appVersionParts.compareTo(minAppVersionParts) <= -1) return false.toResult()
                if (appVersionParts.compareTo(maxAppVersionParts) >= 1) return false.toResult()

                return true.toResult()
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

    private fun List<Int>.compareTo(other: List<Int>): Int {
        val otherSize = other.size

        for (index in this.indices) {
            if (index > otherSize-1 ) return 0
            val value = this[index]
            if (value < other[index]) return -1
            if (value > other[index]) return 1
        }

        return 0
    }
}
