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
import com.duckduckgo.remote.messaging.impl.models.MatchingAttribute

class UserAttributeMatcher(
    val userBrowserProperties: UserBrowserProperties
) {
    suspend fun evaluate(matchingAttribute: MatchingAttribute): Result {
        when (matchingAttribute) {
            is MatchingAttribute.AppTheme -> {
                return matchingAttribute.value.equals(userBrowserProperties.appTheme().toString(), ignoreCase = true).toResult()
            }
            is MatchingAttribute.Bookmarks -> {
                val bookmarks = userBrowserProperties.bookmarks()
                if ((matchingAttribute.min.defaultValue() || bookmarks >= matchingAttribute.min) &&
                    (matchingAttribute.max.defaultValue() || bookmarks <= matchingAttribute.max)
                ) {
                    return true.toResult()
                }

                return false.toResult()
            }
            is MatchingAttribute.DaysSinceInstalled -> {
                val daysSinceInstalled = userBrowserProperties.daysSinceInstalled()
                if ((matchingAttribute.min.defaultValue() || daysSinceInstalled >= matchingAttribute.min) &&
                    (matchingAttribute.max.defaultValue() || daysSinceInstalled <= matchingAttribute.max)
                ) {
                    return true.toResult()
                }

                return false.toResult()
            }
            is MatchingAttribute.DaysUsedSince -> {
                val daysUsedSince = userBrowserProperties.daysUsedSince(matchingAttribute.since)
                if ((matchingAttribute.value.defaultValue() || daysUsedSince == matchingAttribute.value.toLong())) {
                    return true.toResult()
                }

                return false.toResult()
            }
            is MatchingAttribute.DefaultBrowser -> {
                return (matchingAttribute.value == userBrowserProperties.defaultBrowser()).toResult()
            }
            is MatchingAttribute.EmailEnabled -> {
                return (matchingAttribute.value == userBrowserProperties.emailEnabled()).toResult()
            }
            is MatchingAttribute.Favorites -> {
                val favorites = userBrowserProperties.favorites()
                if ((matchingAttribute.min.defaultValue() || favorites >= matchingAttribute.min) &&
                    (matchingAttribute.max.defaultValue() || favorites <= matchingAttribute.max)
                ) {
                    return true.toResult()
                }

                return false.toResult()
            }
            is MatchingAttribute.SearchCount -> {
                val searchCount = userBrowserProperties.searchCount()
                if ((matchingAttribute.min.defaultValue() || searchCount >= matchingAttribute.min) &&
                    (matchingAttribute.max.defaultValue() || searchCount <= matchingAttribute.max)
                ) {
                    return true.toResult()
                }

                return false.toResult()
            }
            is MatchingAttribute.WidgetAdded -> {
                return (matchingAttribute.value == userBrowserProperties.widgetAdded()).toResult()
            }
            else -> throw IllegalArgumentException("Invalid matcher for $matchingAttribute")
        }
    }
}