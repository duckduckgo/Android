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

package com.duckduckgo.app.remotemessage.impl.matchingattributes

sealed class MatchingAttribute(val fallback: Boolean? = null) {
    data class Locale(override val value: List<String>) : MatchingAttribute(), StringArrayMatchingAttribute
    data class Api(override val min: Int, override val max: Int) : MatchingAttribute(), RangeIntMatchingAttribute
    data class WebView(override val min: String, override val max: String) : MatchingAttribute(), RangeStringMatchingAttribute
    data class Flavor(override val value: List<String>) : MatchingAttribute(), StringArrayMatchingAttribute
    data class AppId(override val value: String) : MatchingAttribute(), StringMatchingAttribute

    data class AppVersion(override val min: String, override val max: String) : MatchingAttribute(), RangeStringMatchingAttribute
    data class Atb(override val value: String) : MatchingAttribute(), StringMatchingAttribute
    data class AppAtb(override val value: String) : MatchingAttribute(), StringMatchingAttribute
    data class SearchAtb(override val value: String) : MatchingAttribute(), StringMatchingAttribute
    data class ExpVariant(override val value: String) : MatchingAttribute(), StringMatchingAttribute
    data class InstalledGPlay(override val value: Boolean) : MatchingAttribute(), BooleanMatchingAttribute

    data class DefaultBrowser(override val value: Boolean) : MatchingAttribute(), BooleanMatchingAttribute
    data class EmailEnabled(override val value: Boolean) : MatchingAttribute(), BooleanMatchingAttribute
    data class WidgetAdded(override val value: Boolean) : MatchingAttribute(), BooleanMatchingAttribute
    data class SearchCount(override val min: Int, override val max: Int) : MatchingAttribute(), RangeIntMatchingAttribute
    data class Bookmarks(override val min: Int, override val max: Int) : MatchingAttribute(), RangeIntMatchingAttribute
    data class Favorites(override val min: Int, override val max: Int) : MatchingAttribute(), RangeIntMatchingAttribute
    data class AppTheme(override val value: String) : MatchingAttribute(), StringMatchingAttribute
    data class DaysSinceInstalled(override val min: Int, override val max: Int) : MatchingAttribute(), RangeIntMatchingAttribute
    data class DaysUsedSince(override val min: Int, override val max: Int) : MatchingAttribute(), RangeIntMatchingAttribute
    class Unknown : MatchingAttribute()
}

interface RangeIntMatchingAttribute {
    val min: Int
    val max: Int
}

interface RangeStringMatchingAttribute {
    val min: String
    val max: String
}

interface BooleanMatchingAttribute {
    val value: Boolean
}

interface StringMatchingAttribute {
    val value: String
}

interface StringArrayMatchingAttribute {
    val value: List<String>
}
