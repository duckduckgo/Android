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
import com.duckduckgo.remote.messaging.impl.models.IntMatchingAttribute
import com.duckduckgo.remote.messaging.impl.models.MATCHING_ATTR_INT_DEFAULT_VALUE
import com.duckduckgo.remote.messaging.impl.models.MatchingAttribute
import com.duckduckgo.remote.messaging.impl.models.RangeIntMatchingAttribute
import com.duckduckgo.remote.messaging.impl.models.matches

class UserAttributeMatcher(
    val userBrowserProperties: UserBrowserProperties
) : AttributeMatcher {
    override suspend fun evaluate(matchingAttribute: MatchingAttribute): EvaluationResult? {
        when (matchingAttribute) {
            is MatchingAttribute.AppTheme -> {
                return matchingAttribute.matches(userBrowserProperties.appTheme().toString())
            }
            is MatchingAttribute.Bookmarks -> {
                if (matchingAttribute == MatchingAttribute.Bookmarks()) return EvaluationResult.Fail
                if (matchingAttribute.value != MATCHING_ATTR_INT_DEFAULT_VALUE) {
                    return (matchingAttribute as IntMatchingAttribute).matches(userBrowserProperties.bookmarks().toInt())
                }
                return (matchingAttribute as RangeIntMatchingAttribute).matches(userBrowserProperties.bookmarks().toInt())
            }
            is MatchingAttribute.DaysSinceInstalled -> {
                if (matchingAttribute == MatchingAttribute.DaysSinceInstalled()) return EvaluationResult.Fail

                if (matchingAttribute.value != MATCHING_ATTR_INT_DEFAULT_VALUE) {
                    return (matchingAttribute as IntMatchingAttribute).matches(userBrowserProperties.daysSinceInstalled().toInt())
                }
                return (matchingAttribute as RangeIntMatchingAttribute).matches(userBrowserProperties.daysSinceInstalled().toInt())
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
                if (matchingAttribute == MatchingAttribute.Favorites()) return EvaluationResult.Fail
                if (matchingAttribute.value != MATCHING_ATTR_INT_DEFAULT_VALUE) {
                    return (matchingAttribute as IntMatchingAttribute).matches(userBrowserProperties.favorites().toInt())
                }
                return (matchingAttribute as RangeIntMatchingAttribute).matches(userBrowserProperties.favorites().toInt())
            }
            is MatchingAttribute.SearchCount -> {
                if (matchingAttribute == MatchingAttribute.SearchCount()) return EvaluationResult.Fail
                if (matchingAttribute.value != MATCHING_ATTR_INT_DEFAULT_VALUE) {
                    return (matchingAttribute as IntMatchingAttribute).matches(userBrowserProperties.searchCount().toInt())
                }
                return (matchingAttribute as RangeIntMatchingAttribute).matches(userBrowserProperties.searchCount().toInt())
            }
            is MatchingAttribute.WidgetAdded -> {
                return matchingAttribute.matches(userBrowserProperties.widgetAdded())
            }
            else -> return null
        }
    }
}
