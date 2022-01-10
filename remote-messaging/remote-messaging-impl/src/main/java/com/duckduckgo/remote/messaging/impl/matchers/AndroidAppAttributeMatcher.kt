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
import com.duckduckgo.remote.messaging.impl.models.BooleanMatchingAttribute
import com.duckduckgo.remote.messaging.impl.models.MatchingAttribute
import com.duckduckgo.remote.messaging.impl.models.RangeIntMatchingAttribute
import com.duckduckgo.remote.messaging.impl.models.StringArrayMatchingAttribute
import timber.log.Timber
import java.util.*

class AndroidAppAttributeMatcher(
    val appProperties: AppProperties
) {
    fun evaluate(matchingAttribute: MatchingAttribute): Result {
        when (matchingAttribute) {
            is MatchingAttribute.Flavor -> {
                return matchingAttribute.matches(appProperties.flavor())
            }
            is MatchingAttribute.AppId -> {
                return matchingAttribute.matches(appProperties.appId())
            }
            is MatchingAttribute.AppVersion -> {
                if (matchingAttribute == MatchingAttribute.AppVersion()) return Result.Fail
                return matchingAttribute.matches(appProperties.appVersion())
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

fun StringArrayMatchingAttribute.matches(value: String): Result {
    this.value.find { it.equals(value, true) } ?: return false.toResult()
    return true.toResult()
}

fun BooleanMatchingAttribute.matches(value: Boolean): Result {
    return (this.value == value).toResult()
}

fun RangeIntMatchingAttribute.matches(value: Int): Result {
    if ((this.min.defaultValue() || value >= this.min) &&
        (this.max.defaultValue() || value <= this.max)
    ) {
        return true.toResult()
    }

    return false.toResult()
}