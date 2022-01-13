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

import com.duckduckgo.browser.api.UserBrowserProperties
import com.duckduckgo.remote.messaging.impl.models.DateMatchingAttribute
import com.duckduckgo.remote.messaging.impl.models.MatchingAttribute
import com.duckduckgo.remote.messaging.impl.models.StringMatchingAttribute

class UserAttributeMatcher(
    val userBrowserProperties: UserBrowserProperties
) {
    suspend fun evaluate(matchingAttribute: MatchingAttribute): Result {
        when (matchingAttribute) {
            is MatchingAttribute.AppTheme -> {
                return matchingAttribute.matches(userBrowserProperties.appTheme().toString())
            }
            is MatchingAttribute.Bookmarks -> {
                return matchingAttribute.matches(userBrowserProperties.bookmarks().toInt())
            }
            is MatchingAttribute.DaysSinceInstalled -> {
                return matchingAttribute.matches(userBrowserProperties.daysSinceInstalled().toInt())
            }
            is MatchingAttribute.DaysUsedSince -> {
                val daysUsedSince = userBrowserProperties.daysUsedSince(matchingAttribute.since)
                return matchingAttribute.matches(daysUsedSince.toInt())
            }
            is MatchingAttribute.DefaultBrowser -> {
                return matchingAttribute.matches(userBrowserProperties.defaultBrowser())
            }
            is MatchingAttribute.EmailEnabled -> {
                return matchingAttribute.matches(userBrowserProperties.emailEnabled())
            }
            is MatchingAttribute.Favorites -> {
                return matchingAttribute.matches(userBrowserProperties.favorites().toInt())
            }
            is MatchingAttribute.SearchCount -> {
                return matchingAttribute.matches(userBrowserProperties.searchCount().toInt())
            }
            is MatchingAttribute.WidgetAdded -> {
                return matchingAttribute.matches(userBrowserProperties.widgetAdded())
            }
            else -> throw IllegalArgumentException("Invalid matcher for $matchingAttribute")
        }
    }
}

fun DateMatchingAttribute.matches(value: Int): Result {
    if ((this.value.defaultValue() || value == this.value)) {
        return true.toResult()
    }
    return false.toResult()
}

fun StringMatchingAttribute.matches(value: String): Result {
    return this.value.equals(value, ignoreCase = true).toResult()
}
