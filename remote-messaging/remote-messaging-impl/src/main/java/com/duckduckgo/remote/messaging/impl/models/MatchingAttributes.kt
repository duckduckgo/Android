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

package com.duckduckgo.remote.messaging.impl.models

import java.util.*

sealed class MatchingAttribute {
    data class Locale(
        override val value: List<String> = emptyList(),
        val fallback: Boolean? = null
    ) : MatchingAttribute(), StringArrayMatchingAttribute

    data class Api(
        override val min: Int = MATCHING_ATTR_INT_DEFAULT_VALUE,
        override val max: Int = MATCHING_ATTR_INT_DEFAULT_VALUE,
        override val value: Int = MATCHING_ATTR_INT_DEFAULT_VALUE,
        val fallback: Boolean? = null
    ) : MatchingAttribute(), RangeIntMatchingAttribute, IntMatchingAttribute

    data class WebView(
        override val min: String = MATCHING_ATTR_STRING_DEFAULT_VALUE,
        override val max: String = MATCHING_ATTR_STRING_DEFAULT_VALUE,
        override val value: String = MATCHING_ATTR_STRING_DEFAULT_VALUE,
        val fallback: Boolean? = null
    ) : MatchingAttribute(), RangeStringMatchingAttribute, StringMatchingAttribute

    data class Flavor(
        override val value: List<String> = emptyList(),
        val fallback: Boolean? = null
    ) : MatchingAttribute(), StringArrayMatchingAttribute

    data class AppId(
        override val value: String = MATCHING_ATTR_STRING_DEFAULT_VALUE,
        val fallback: Boolean? = null
    ) : MatchingAttribute(), StringMatchingAttribute

    data class AppVersion(
        override val min: String = MATCHING_ATTR_STRING_DEFAULT_VALUE,
        override val max: String = MATCHING_ATTR_STRING_DEFAULT_VALUE,
        override val value: String = MATCHING_ATTR_STRING_DEFAULT_VALUE,
        val fallback: Boolean? = null
    ) : MatchingAttribute(), RangeStringMatchingAttribute, StringMatchingAttribute

    data class Atb(
        override val value: String = MATCHING_ATTR_STRING_DEFAULT_VALUE,
        val fallback: Boolean? = null
    ) : MatchingAttribute(), StringMatchingAttribute

    data class AppAtb(
        override val value: String = MATCHING_ATTR_STRING_DEFAULT_VALUE,
        val fallback: Boolean? = null
    ) : MatchingAttribute(), StringMatchingAttribute

    data class SearchAtb(
        override val value: String = MATCHING_ATTR_STRING_DEFAULT_VALUE,
        val fallback: Boolean? = null
    ) : MatchingAttribute(), StringMatchingAttribute

    data class ExpVariant(
        override val value: String = MATCHING_ATTR_STRING_DEFAULT_VALUE,
        val fallback: Boolean? = null
    ) : MatchingAttribute(), StringMatchingAttribute

    data class InstalledGPlay(
        override val value: Boolean,
        val fallback: Boolean? = null
    ) : MatchingAttribute(), BooleanMatchingAttribute

    data class DefaultBrowser(
        override val value: Boolean,
        val fallback: Boolean? = null
    ) : MatchingAttribute(), BooleanMatchingAttribute

    data class EmailEnabled(
        override val value: Boolean,
        val fallback: Boolean? = null
    ) : MatchingAttribute(), BooleanMatchingAttribute

    data class WidgetAdded(
        override val value: Boolean,
        val fallback: Boolean? = null
    ) : MatchingAttribute(), BooleanMatchingAttribute

    data class SearchCount(
        override val min: Int = MATCHING_ATTR_INT_DEFAULT_VALUE,
        override val max: Int = MATCHING_ATTR_INT_DEFAULT_VALUE,
        override val value: Int = MATCHING_ATTR_INT_DEFAULT_VALUE,
        val fallback: Boolean? = null
    ) : MatchingAttribute(), RangeIntMatchingAttribute, IntMatchingAttribute

    data class Bookmarks(
        override val min: Int = MATCHING_ATTR_INT_DEFAULT_VALUE,
        override val max: Int = MATCHING_ATTR_INT_DEFAULT_VALUE,
        override val value: Int = MATCHING_ATTR_INT_DEFAULT_VALUE,
        val fallback: Boolean? = null
    ) : MatchingAttribute(), RangeIntMatchingAttribute, IntMatchingAttribute

    data class Favorites(
        override val min: Int = MATCHING_ATTR_INT_DEFAULT_VALUE,
        override val max: Int = MATCHING_ATTR_INT_DEFAULT_VALUE,
        override val value: Int = MATCHING_ATTR_INT_DEFAULT_VALUE,
        val fallback: Boolean? = null
    ) : MatchingAttribute(), RangeIntMatchingAttribute, IntMatchingAttribute

    data class AppTheme(
        override val value: String = MATCHING_ATTR_STRING_DEFAULT_VALUE,
        val fallback: Boolean? = null
    ) : MatchingAttribute(), StringMatchingAttribute

    data class DaysSinceInstalled(
        override val min: Int = MATCHING_ATTR_INT_DEFAULT_VALUE,
        override val max: Int = MATCHING_ATTR_INT_DEFAULT_VALUE,
        override val value: Int = MATCHING_ATTR_INT_DEFAULT_VALUE,
        val fallback: Boolean? = null
    ) : MatchingAttribute(), RangeIntMatchingAttribute, IntMatchingAttribute

    data class DaysUsedSince(
        override val since: Date,
        override val value: Int,
        val fallback: Boolean? = null
    ) : MatchingAttribute(), DateMatchingAttribute

    data class Unknown(val fallback: Boolean?) : MatchingAttribute()
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

interface IntMatchingAttribute {
    val value: Int
}

interface StringArrayMatchingAttribute {
    val value: List<String>
}

interface DateMatchingAttribute {
    val since: Date
    val value: Int
}

@Suppress("UNCHECKED_CAST")
internal fun Any?.toStringList(): List<String> = this?.let { it as List<String> } ?: emptyList()

internal fun Any?.toIntOrDefault(default: Int): Int = when {
    this == null -> default
    this is Double -> this.toInt()
    this is Long -> this.toInt()
    else -> this as Int
}

internal fun Any?.toStringOrDefault(default: String): String = this?.let { it as String } ?: default

internal fun Locale.asJsonFormat() = "$language-$country"

const val MATCHING_ATTR_INT_DEFAULT_VALUE = -1
const val MATCHING_ATTR_STRING_DEFAULT_VALUE = ""
