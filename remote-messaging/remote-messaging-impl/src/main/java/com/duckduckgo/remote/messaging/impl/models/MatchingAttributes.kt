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

import com.duckduckgo.remote.messaging.impl.matchers.Result
import com.duckduckgo.remote.messaging.impl.matchers.defaultValue
import com.duckduckgo.remote.messaging.impl.matchers.toResult
import timber.log.Timber
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

fun StringArrayMatchingAttribute.matches(value: String): Result {
    this.value.find { it.equals(value, true) } ?: return false.toResult()
    return true.toResult()
}

fun BooleanMatchingAttribute.matches(value: Boolean): Result {
    return (this.value == value).toResult()
}

fun IntMatchingAttribute.matches(value: Int): Result {
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

fun DateMatchingAttribute.matches(value: Int): Result {
    if ((this.value.defaultValue() || value == this.value)) {
        return true.toResult()
    }
    return false.toResult()
}

fun StringMatchingAttribute.matches(value: String): Result {
    return this.value.equals(value, ignoreCase = true).toResult()
}

fun RangeStringMatchingAttribute.matches(value: String): Result {
    Timber.i("RMF: device value: $value")
    if (!value.matches(Regex("[0-9]+(\\.[0-9]+)*"))) return false.toResult()

    val versionAsIntList = value.split(".").filter { it.isNotEmpty() }.map { it.toInt() }
    val minVersionAsIntList = this.min.split(".").filter { it.isNotEmpty() }.map { it.toInt() }
    val maxVersionAsIntList = this.max.split(".").filter { it.isNotEmpty() }.map { it.toInt() }

    if (versionAsIntList.isEmpty()) return false.toResult()
    if (versionAsIntList.compareTo(minVersionAsIntList) <= -1) return false.toResult()
    if (versionAsIntList.compareTo(maxVersionAsIntList) >= 1) return false.toResult()

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

@Suppress("UNCHECKED_CAST")
fun Any?.toStringList(): List<String> = this?.let { it as List<String> } ?: emptyList()

fun Any?.toIntOrDefault(default: Int): Int = when {
    this == null -> default
    this is Double -> this.toInt()
    this is Long -> this.toInt()
    else -> this as Int
}

fun Any?.toStringOrDefault(default: String): String = this?.let { it as String } ?: default

internal fun Locale.asJsonFormat() = "$language-$country"

const val MATCHING_ATTR_INT_DEFAULT_VALUE = -1
const val MATCHING_ATTR_STRING_DEFAULT_VALUE = ""
