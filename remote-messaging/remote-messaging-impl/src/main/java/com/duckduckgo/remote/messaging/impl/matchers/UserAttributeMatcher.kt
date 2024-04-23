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
import com.duckduckgo.remote.messaging.api.AttributeMatcherPlugin
import com.duckduckgo.remote.messaging.api.MatchingAttribute
import com.duckduckgo.remote.messaging.impl.models.*

class UserAttributeMatcher(
    private val userBrowserProperties: UserBrowserProperties,
) : AttributeMatcherPlugin {
    override suspend fun evaluate(matchingAttribute: MatchingAttribute): Boolean? {
        return when (matchingAttribute) {
            is AppTheme -> {
                matchingAttribute.matches(userBrowserProperties.appTheme().toString())
            }
            is Bookmarks -> {
                matchingAttribute.matches(userBrowserProperties.bookmarks().toInt())
            }
            is DaysSinceInstalled -> {
                matchingAttribute.matches(userBrowserProperties.daysSinceInstalled().toInt())
            }
            is DaysUsedSince -> {
                val daysUsedSince = userBrowserProperties.daysUsedSince(matchingAttribute.since)
                matchingAttribute.matches(daysUsedSince.toInt())
            }
            is DefaultBrowser -> {
                matchingAttribute.matches(userBrowserProperties.defaultBrowser())
            }
            is EmailEnabled -> {
                matchingAttribute.matches(userBrowserProperties.emailEnabled())
            }
            is Favorites -> {
                matchingAttribute.matches(userBrowserProperties.favorites().toInt())
            }
            is SearchCount -> {
                matchingAttribute.matches(userBrowserProperties.searchCount().toInt())
            }
            is WidgetAdded -> {
                matchingAttribute.matches(userBrowserProperties.widgetAdded())
            }
            else -> return null
        }
    }
}
